package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// Sealed error hierarchy — mirrors iOS AuthError enum
// ---------------------------------------------------------------------------

sealed class AuthException(message: String) : Exception(message) {
    object InvalidEmail : AuthException("Geçersiz email adresi")
    object InvalidPassword : AuthException("Şifre en az 6 karakter olmalıdır")
    object EmailNotConfirmed : AuthException("email_not_confirmed")
    class SignUpFailed(cause: String) : AuthException("Kayıt başarısız: $cause")
    class SignInFailed(cause: String) : AuthException("Giriş başarısız: $cause")
    class SignOutFailed(cause: String) : AuthException("Çıkış başarısız: $cause")
    object Unknown : AuthException("Bilinmeyen bir hata oluştu")
}

/**
 * AuthService — production Android equivalent of iOS AuthService.swift.
 *
 * Exposes reactive [StateFlow]s for UI observation and suspend functions for
 * all auth operations. Mirrors the iOS @MainActor ObservableObject pattern
 * using Kotlin coroutines + StateFlow.
 *
 * Usage:
 *   // In ViewModel or composable
 *   val isAuthenticated by AuthService.isAuthenticated.collectAsState()
 *   AuthService.signIn(email, password)
 */
object AuthService {

    private val supabase get() = SupabaseConfig.client
    private val auth get() = supabase.auth

    // -----------------------------------------------------------------------
    // Reactive state — mirrors iOS @Published vars
    // -----------------------------------------------------------------------

    /**
     * Raw session status from the Supabase SDK.
     * Exposed so the UI can derive all auth states from a SINGLE StateFlow,
     * preventing race conditions between separate isLoading / isAuthenticated flows.
     */
    val sessionStatus: StateFlow<SessionStatus>
        get() = auth.sessionStatus

    /**
     * Emits the current authenticated [UserInfo] or null.
     * Derived from the Supabase SDK's sessionStatus flow.
     */
    val currentUser: Flow<UserInfo?>
        get() = auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user
                else -> null
            }
        }

    /**
     * True when a valid (non-expired) session exists.
     * Kept for screens that only need this single boolean.
     */
    val isAuthenticated: Flow<Boolean>
        get() = auth.sessionStatus.map { status ->
            status is SessionStatus.Authenticated
        }

    // -----------------------------------------------------------------------
    // Session Management — mirrors iOS checkSession()
    // -----------------------------------------------------------------------

    /**
     * Must be called once at app startup. Restores any persisted session and emits
     * updated state. Safe to call from [MainActivity]; skipped after the first
     * successful load so activity recreation does not replay [Initializing].
     */
    suspend fun checkSession() {
        if (hasLoadedSessionFromStorage) return
        hasLoadedSessionFromStorage = true
        try {
            auth.loadFromStorage()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        try {
            enforceEmailConfirmationIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (_: AuthException.EmailNotConfirmed) {
            // Oturum kapatıldı — kullanıcı giriş ekranına düşer.
        }
    }

    private var hasLoadedSessionFromStorage = false

    // -----------------------------------------------------------------------
    // Sign Up — mirrors iOS AuthService.signUp(email:password:name:gender:)
    // -----------------------------------------------------------------------

    /**
     * Registers a new user with Supabase Auth.
     * Metadata (name, gender, gym_id, gym_name, default_location) is stored in
     * the auth.users raw_user_meta_data — exactly as the iOS implementation does.
     *
     * @return true when the auth user already has a confirmed email (rare on fresh signup).
     * @throws AuthException.InvalidEmail
     * @throws AuthException.InvalidPassword
     * @throws AuthException.SignUpFailed
     */
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        gender: String,
        isIndividual: Boolean = true,
        gymId: String = SupabaseConfig.GYM_ID
    ): Boolean {
        if (!isValidEmail(email)) throw AuthException.InvalidEmail
        if (password.length < 6) throw AuthException.InvalidPassword

        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("name", name)
                    put("gender", gender)
                    put("account_type", if (isIndividual) "individual" else "gym")
                    if (isIndividual) {
                        put("role", "individual")
                    } else {
                        put("gym_id", gymId)
                        put("gym_name", SupabaseConfig.GYM_NAME)
                        put("default_location", SupabaseConfig.DEFAULT_LOCATION)
                    }
                }
            }
            val user = fetchCurrentAuthUser() ?: return false
            return isEmailConfirmed(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            throw AuthException.SignUpFailed(extractAuthErrorMessage(e))
        }
    }

    // -----------------------------------------------------------------------
    // Sign In — mirrors iOS AuthService.signIn(email:password:)
    // -----------------------------------------------------------------------

    /**
     * Authenticates with email + password.
     *
     * @throws AuthException.InvalidEmail
     * @throws AuthException.SignInFailed
     */
    suspend fun signIn(email: String, password: String) {
        if (!isValidEmail(email)) throw AuthException.InvalidEmail

        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            enforceEmailConfirmationIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            val message = extractAuthErrorMessage(e)
            if (message == "email_not_confirmed") {
                throw AuthException.EmailNotConfirmed
            }
            throw AuthException.SignInFailed(message)
        }
    }

    /**
     * Kayıt doğrulama e-postasını yeniden gönderir.
     */
    suspend fun resendConfirmationEmail(email: String) {
        if (!isValidEmail(email)) throw AuthException.InvalidEmail
        try {
            auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException.SignInFailed(
                extractAuthErrorMessage(e).ifBlank { "Doğrulama e-postası gönderilemedi." }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Sign Out — mirrors iOS AuthService.signOut()
    // -----------------------------------------------------------------------

    /**
     * Signs the current user out and clears local session storage.
     *
     * @throws AuthException.SignOutFailed
     */
    suspend fun signOut() {
        try {
            val userId = getCurrentUserId()
            auth.signOut()
            hasLoadedSessionFromStorage = false
            if (userId != null) {
                PusherService.unsubscribeFromUser(userId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException.SignOutFailed(e.message ?: "Unknown error")
        }
    }

    // -----------------------------------------------------------------------
    // Session helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the current non-expired session, or null.
     * Mirrors iOS supabase.auth.session with isExpired guard.
     */
    suspend fun getCurrentSession() = try {
        auth.currentSessionOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Returns the UUID string of the currently authenticated user, or null.
     */
    suspend fun getCurrentUserId(): String? = getCurrentSession()?.user?.id

    suspend fun isCurrentUserEmailConfirmed(): Boolean {
        val user = fetchCurrentAuthUser() ?: return false
        return isEmailConfirmed(user)
    }

    /** Auth metadata — profil yüklenmeden bireysel kullanıcı tespiti için. */
    fun isIndividualAuthUser(user: UserInfo?): Boolean {
        if (user == null) return false
        val meta = user.userMetadata
        val role = meta.stringMeta("role")
        val accountType = meta.stringMeta("account_type")
        return role.equals("individual", ignoreCase = true) ||
            accountType.equals("individual", ignoreCase = true)
    }

    /**
     * E-posta doğrulaması sonrası ilk girişte profil satırı yoksa auth metadata ile oluşturur.
     */
    suspend fun ensureProfileFromAuthIfMissing() {
        val userId = getCurrentUserId() ?: return
        if (UserService.fetchProfile(userId) != null) return
        val user = fetchCurrentAuthUser() ?: return
        val email = user.email ?: return
        val meta = user.userMetadata
        UserService.ensureProfileExists(
            userId = userId,
            email = email,
            name = meta.stringMeta("name").orEmpty(),
            gender = meta.stringMeta("gender").orEmpty(),
            isIndividual = isIndividualAuthUser(user)
        )
    }

    /**
     * Returns true when a valid (non-expired) session is present.
     */
    suspend fun isSessionValid(): Boolean {
        val session = getCurrentSession() ?: return false
        val nowEpoch = System.currentTimeMillis() / 1000L
        return session.expiresAt.epochSeconds > nowEpoch
    }

    // -----------------------------------------------------------------------
    // Password update — mirrors iOS UserService.updatePassword(newPassword:)
    // -----------------------------------------------------------------------

    /**
     * Updates the password for the currently authenticated user.
     */
    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            password = newPassword
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun extractAuthErrorMessage(e: Exception): String {
        val raw = buildString {
            append(e.message.orEmpty())
            e.cause?.message?.let { append(' ').append(it) }
        }
        Regex(""""msg"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.get(1)?.let { return it }
        Regex(""""message"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.get(1)?.let { return it }
        if (raw.contains("user_already_registered", ignoreCase = true) ||
            raw.contains("already registered", ignoreCase = true)
        ) {
            return "Bu e-posta adresi zaten kayıtlı."
        }
        if (raw.contains("users_gender_check", ignoreCase = true)) {
            return "Geçersiz cinsiyet değeri."
        }
        if (raw.contains("Invalid login credentials", ignoreCase = true)) {
            return "E-posta veya şifre hatalı."
        }
        if (raw.contains("email_not_confirmed", ignoreCase = true) ||
            raw.contains("Email not confirmed", ignoreCase = true)
        ) {
            return "email_not_confirmed"
        }
        if (raw.contains("URL:") || raw.length > 120) {
            return "Sunucu hatası. Lütfen tekrar deneyin."
        }
        return raw.ifBlank { "Bilinmeyen hata" }
    }

    /** Mirrors iOS isValidEmail regex. */
    private fun isValidEmail(email: String): Boolean {
        val regex = Regex("[A-Z0-9a-z._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,64}")
        return regex.matches(email)
    }

    /**
     * Oturum açmış kullanıcının e-postası doğrulanmamışsa çıkış yapar.
     */
    private suspend fun enforceEmailConfirmationIfNeeded() {
        val user = fetchCurrentAuthUser() ?: return
        if (isEmailConfirmed(user)) return
        clearSessionAfterUnconfirmedEmail()
        throw AuthException.EmailNotConfirmed
    }

    private suspend fun fetchCurrentAuthUser(): UserInfo? {
        return runCatching {
            auth.retrieveUserForCurrentSession(updateSession = true)
        }.getOrNull() ?: auth.currentSessionOrNull()?.user
    }

    private suspend fun clearSessionAfterUnconfirmedEmail() {
        try {
            auth.signOut()
        } catch (_: Exception) {
        }
        hasLoadedSessionFromStorage = false
    }

    fun isEmailConfirmed(user: UserInfo): Boolean =
        user.emailConfirmedAt != null || user.confirmedAt != null

    private fun JsonObject?.stringMeta(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
