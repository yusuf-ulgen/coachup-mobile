package com.app.coachup.app.services

import android.util.Log
import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.Address
import com.app.coachup.app.models.BiometricSettingUpdate
import com.app.coachup.app.models.DefaultScreenUpdate
import com.app.coachup.app.models.EmergencyContact
import com.app.coachup.app.models.Gym
import com.app.coachup.app.models.NotificationSettingUpdate
import com.app.coachup.app.models.ProfileFieldsUpdateRequest
import com.app.coachup.app.models.UserActivity
import com.app.coachup.app.models.UserActivityInsert
import com.app.coachup.app.models.UserMembership
import com.app.coachup.app.models.UserProfile
import com.app.coachup.app.models.UserProfileInsert
import com.app.coachup.app.models.WeightUnitUpdate
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * UserService — Android production equivalent of iOS UserService.swift.
 *
 * Manages user profile, addresses, emergency contacts, settings, and activity
 * streaks. Every function maps to an exact Supabase table + column set taken
 * from the iOS source.
 */
object UserService {

    private val supabase get() = SupabaseConfig.client

    // -----------------------------------------------------------------------
    // Reactive state — mirrors iOS @Published vars
    // -----------------------------------------------------------------------

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    private val _currentAddress = MutableStateFlow<Address?>(null)
    val currentAddress: StateFlow<Address?> = _currentAddress.asStateFlow()

    private val _emergencyContact = MutableStateFlow<EmergencyContact?>(null)
    val emergencyContact: StateFlow<EmergencyContact?> = _emergencyContact.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableGyms = MutableStateFlow<List<Gym>>(emptyList())
    val availableGyms: StateFlow<List<Gym>> = _availableGyms.asStateFlow()

    private val _availableMemberships = MutableStateFlow<List<UserMembership>>(emptyList())
    val availableMemberships: StateFlow<List<UserMembership>> = _availableMemberships.asStateFlow()

    /** Oturum boyunca kullanıcının seçtiği salon — fetchProfile yenilemelerinde korunur. */
    private var selectedGymId: String? = null

    private fun distinctMembershipGymIds(): List<String> =
        _availableMemberships.value.mapNotNull { it.plan?.gymId }.distinct()

    /** Birden fazla salonda üyelik varsa ve geçerli bir salon seçilmemişse true döner. */
    /** Bireysel kullanıcı — salona bağlı aktif üyeliği yok. */
    fun isIndividualUser(profile: UserProfile? = _currentProfile.value): Boolean {
        if (profile == null) return false
        if (profile.role.equals("individual", ignoreCase = true)) return true
        val hasActiveGymMembership = _availableMemberships.value.any {
            it.isActive && !it.plan?.gymId.isNullOrBlank()
        }
        return !hasActiveGymMembership && profile.gymId.isNullOrBlank()
    }

    /**
     * Anketler gibi salon kapsamlı içerikler için aktif salon kimliği.
     * Bireysel kullanıcıda null; GymConfig fallback kullanılmaz.
     */
    fun resolveActiveGymIdForContent(profile: UserProfile? = _currentProfile.value): String? {
        if (profile == null || isIndividualUser(profile)) return null
        return profile.gymId?.takeIf { it.isNotBlank() }
            ?: selectedGymId?.takeIf { it.isNotBlank() }
            ?: distinctMembershipGymIds().singleOrNull()
    }

    fun shouldShowMembershipPicker(): Boolean {
        val gymIds = distinctMembershipGymIds()
        if (gymIds.size <= 1) return false
        val activeGymId = _currentProfile.value?.gymId ?: selectedGymId
        if (activeGymId.isNullOrBlank()) return true
        return activeGymId !in gymIds
    }

    fun reset() {
        _currentProfile.value = null
        _currentAddress.value = null
        _emergencyContact.value = null
        _availableGyms.value = emptyList()
        _availableMemberships.value = emptyList()
        selectedGymId = null
        _isLoading.value = false
    }

    // -----------------------------------------------------------------------
    // Profile — "users" table
    // Mirrors iOS fetchProfile(userId:) / updateProfile(...)
    // -----------------------------------------------------------------------

    /**
     * Fetches a single user profile row from the "users" table.
     * Mirrors iOS UserService.fetchProfile(userId:).
     */
    suspend fun fetchProfile(userId: String): UserProfile? {
        _isLoading.value = true
        return try {
            val rows = supabase
                .from("users")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeList<UserProfile>()

            val profile = rows.firstOrNull() ?: return null

            Log.d("UserService", "fetchProfile userId=$userId profileId=${profile.id} gymId=${profile.gymId} gymName=${profile.gymName}")

            // Bireysel kullanıcı — salon ataması yapma
            if (profile.isIndividual) {
                _availableGyms.value = emptyList()
                _availableMemberships.value = emptyList()
                _currentProfile.value = profile
                return profile
            }

            // Kullanıcının aktif üyeliklerinden bağlı salonları getir
            // profile.id kullanıyoruz — admin paneli auth.uid() yerine users.id'yi user_memberships'e yazıyor
            val gyms = fetchUserGyms(profile.id)
            _availableGyms.value = gyms
            Log.d("UserService", "fetchUserGyms returned ${gyms.size} gyms: ${gyms.map { it.name }}")

            val profileId = profile.id
            val multiGym = gyms.size > 1

            var finalProfile = when {
                // gym_id ve gym_name dolu
                !profile.gymId.isNullOrBlank() && !profile.gymName.isNullOrBlank() -> profile

                // gym_name dolu, gym_id eksik — isimden eşleştir
                !profile.gymName.isNullOrBlank() -> {
                    val matched = gyms.firstOrNull { it.name == profile.gymName }
                    if (matched != null) profile.copy(gymId = matched.id) else profile
                }

                // gym_id var ama gym_name boş → gyms tablosundan çek
                !profile.gymId.isNullOrBlank() -> {
                    Log.d("UserService", "gym_id set but gym_name empty, fetching from gyms table...")
                    val gym = gyms.firstOrNull { it.id == profile.gymId }
                        ?: runCatching {
                            supabase.from("gyms")
                                .select { filter { eq("id", profile.gymId) } }
                                .decodeSingle<Gym>()
                        }.onFailure { Log.e("UserService", "gyms table fetch failed", it) }
                         .getOrNull()
                    Log.d("UserService", "gym from table: ${gym?.name}")
                    if (gym != null) {
                        runCatching {
                            supabase.from("users")
                                .update(mapOf("gym_name" to gym.name)) {
                                    filter { eq("id", profileId) }
                                }
                        }.onSuccess {
                            Log.d("UserService", "gym_name updated successfully to ${gym.name}")
                        }.onFailure {
                            Log.e("UserService", "gym_name update failed", it)
                        }
                        profile.copy(gymName = gym.name)
                    } else {
                        profile
                    }
                }

                // Tek salon — otomatik ata; birden fazla salon — kullanıcı seçsin
                gyms.size == 1 -> {
                    val gym = gyms.first()
                    Log.d("UserService", "Single gym — auto-assigning: ${gym.name}")
                    runCatching {
                        supabase.from("users")
                            .update(mapOf("gym_id" to gym.id, "gym_name" to gym.name)) {
                                filter { eq("id", profileId) }
                            }
                    }.onFailure {
                        Log.e("UserService", "gym_id+gym_name update failed", it)
                    }
                    profile.copy(gymId = gym.id, gymName = gym.name)
                }

                multiGym -> {
                    Log.d("UserService", "Multiple gyms — waiting for user selection")
                    profile
                }

                else -> {
                    Log.w("UserService", "No gym found for user $userId")
                    profile
                }
            }

            // Oturum içi seçimi koru (profil yenilemelerinde kaybolmasın)
            val gymIds = distinctMembershipGymIds()
            when {
                !finalProfile.gymId.isNullOrBlank() && finalProfile.gymId in gymIds -> {
                    selectedGymId = finalProfile.gymId
                }
                selectedGymId != null && selectedGymId in gymIds -> {
                    val gym = gyms.firstOrNull { it.id == selectedGymId }
                    finalProfile = finalProfile.copy(
                        gymId = selectedGymId,
                        gymName = gym?.name ?: finalProfile.gymName
                    )
                }
            }

            _currentProfile.value = finalProfile
            finalProfile
        } finally {
            _isLoading.value = false
        }
    }

    /** Aktif üyeliklerden bağlı salon listesini döner, memberships state'ini de günceller. */
    private suspend fun fetchUserGyms(userId: String): List<Gym> {
        return try {
            val memberships = supabase
                .from("user_memberships")
                .select(Columns.raw("*, plan:membership_plans(*)")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<UserMembership>()

            Log.d("UserService", "All memberships: ${memberships.size} → ${memberships.map { "${it.plan?.name}(active=${it.isActive})" }}")
            _availableMemberships.value = memberships

            val gymIds = memberships.mapNotNull { it.plan?.gymId }.distinct()

            gymIds.mapNotNull { gymId ->
                try {
                    supabase.from("gyms")
                        .select { filter { eq("id", gymId) } }
                        .decodeSingle<Gym>()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("UserService", "fetchUserGyms error", e)
            emptyList()
        }
    }

    /** Seçilen üyeliğe göre aktif salonu günceller ve DB'ye kaydeder. */
    suspend fun selectMembership(userId: String, membership: UserMembership, gym: Gym) {
        selectedGymId = gym.id
        _currentProfile.value = _currentProfile.value?.copy(gymId = gym.id, gymName = gym.name)
        val profileId = _currentProfile.value?.id ?: userId
        runCatching {
            supabase.from("users")
                .update(mapOf("gym_id" to gym.id, "gym_name" to gym.name)) {
                    filter { eq("id", profileId) }
                }
        }.onFailure { Log.e("UserService", "selectMembership persist failed", it) }
    }

    /** Kullanıcının aktif salonunu günceller ve DB'ye kaydeder. */
    suspend fun selectGym(userId: String, gym: Gym) {
        selectedGymId = gym.id
        _currentProfile.value = _currentProfile.value?.copy(gymId = gym.id, gymName = gym.name)
        val profileId = _currentProfile.value?.id ?: userId
        runCatching {
            supabase.from("users")
                .update(mapOf("gym_id" to gym.id, "gym_name" to gym.name)) {
                    filter { eq("id", profileId) }
                }
        }.onFailure { Log.e("UserService", "selectGym persist failed", it) }
    }

    /**
     * Inserts a new profile row for a freshly registered user.
     * Mirrors iOS UserService.createProfile(userId:email:name:gender:).
     */
    suspend fun createProfile(
        userId: String,
        email: String,
        name: String,
        gender: String,
        isIndividual: Boolean = true
    ) {
        _isLoading.value = true
        try {
            val existing = supabase
                .from("users")
                .select { filter { eq("id", userId) } }
                .decodeList<UserProfile>()
            if (existing.isNotEmpty()) return

            val profile = UserProfileInsert(
                id = userId,
                email = email,
                name = name,
                gender = gender,
                notificationsEnabled = true,
                biometricEnabled = false,
                defaultScreen = "home",
                weightUnit = "kg",
                role = if (isIndividual) "individual" else "member",
                gymId = if (isIndividual) null else com.app.coachup.app.config.SupabaseConfig.GYM_ID,
                gymName = if (isIndividual) null else com.app.coachup.app.config.SupabaseConfig.GYM_NAME,
                defaultLocation = if (isIndividual) null else com.app.coachup.app.config.SupabaseConfig.DEFAULT_LOCATION
            )
            supabase.from("users").insert(profile)
            _currentProfile.value = fetchProfile(userId)
        } finally {
            _isLoading.value = false
        }
    }

    /** Kayıt veya giriş sonrası profil satırının var olduğundan emin olur. */
    suspend fun ensureProfileExists(
        userId: String,
        email: String,
        name: String,
        gender: String,
        isIndividual: Boolean = true
    ): UserProfile? {
        fetchProfile(userId)?.let { return it }
        createProfile(userId, email, name, gender, isIndividual)
        return fetchProfile(userId)
    }

    /**
     * Partial profile update: name, surname, email, gender, birth_date.
     * Mirrors iOS UserService.updateProfile(userId:name:surname:email:gender:birthDate:).
     */
    suspend fun updateUserProfile(
        userId: String,
        name: String,
        surname: String,
        email: String,
        gender: String,
        birthDate: String? = null  // yyyy-MM-dd
    ) {
        _isLoading.value = true
        try {
            val update = ProfileFieldsUpdateRequest(
                name = name,
                surname = surname,
                email = email,
                gender = gender,
                birthDate = birthDate
            )
            supabase
                .from("users")
                .update(update) {
                    filter { eq("id", userId) }
                }
            // Refresh cache
            _currentProfile.value = fetchProfile(userId)
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Address — "addresses" table
    // Mirrors iOS fetchAddress(userId:) / saveAddress(_:)
    // -----------------------------------------------------------------------

    /**
     * Returns the default address for the given user, or null.
     * Mirrors iOS fetchAddress(userId:) — eq("is_default", true).
     */
    suspend fun fetchAddresses(userId: String): Address? {
        val rows = supabase
            .from("addresses")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("is_default", true)
                }
            }
            .decodeList<Address>()
        val first = rows.firstOrNull()
        _currentAddress.value = first
        return first
    }

    /**
     * Upserts an address: inserts if not found by id, updates otherwise.
     * Mirrors iOS saveAddress(_:).
     */
    suspend fun saveAddress(address: Address) {
        _isLoading.value = true
        try {
            val existing = supabase
                .from("addresses")
                .select {
                    filter { eq("id", address.id) }
                }
                .decodeList<Address>()

            if (existing.isEmpty()) {
                supabase.from("addresses").insert(address)
            } else {
                supabase.from("addresses").update(address) {
                    filter { eq("id", address.id) }
                }
            }
            _currentAddress.value = address
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Emergency Contacts — "emergency_contacts" table
    // Mirrors iOS fetchEmergencyContact(userId:) / saveEmergencyContact(_:)
    // -----------------------------------------------------------------------

    /**
     * Returns the first emergency contact for the given user.
     */
    suspend fun fetchEmergencyContacts(userId: String): EmergencyContact? {
        val rows = supabase
            .from("emergency_contacts")
            .select {
                filter { eq("user_id", userId) }
                limit(1)
            }
            .decodeList<EmergencyContact>()
        val first = rows.firstOrNull()
        _emergencyContact.value = first
        return first
    }

    /**
     * Upserts an emergency contact: inserts if new, updates if exists.
     * Mirrors iOS saveEmergencyContact(_:).
     */
    suspend fun saveEmergencyContact(contact: EmergencyContact) {
        _isLoading.value = true
        try {
            val existing = supabase
                .from("emergency_contacts")
                .select {
                    filter { eq("id", contact.id) }
                }
                .decodeList<EmergencyContact>()

            if (existing.isEmpty()) {
                supabase.from("emergency_contacts").insert(contact)
            } else {
                supabase.from("emergency_contacts").update(contact) {
                    filter { eq("id", contact.id) }
                }
            }
            _emergencyContact.value = contact
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Settings — partial updates on "users" table
    // Mirrors iOS updateNotificationSetting / updateBiometricSetting /
    //           updateDefaultScreen / updateWeightUnit
    // -----------------------------------------------------------------------

    suspend fun updateNotificationSetting(userId: String, enabled: Boolean) {
        supabase.from("users").update(NotificationSettingUpdate(enabled)) {
            filter { eq("id", userId) }
        }
    }

    suspend fun updateBiometricSetting(userId: String, enabled: Boolean) {
        supabase.from("users").update(BiometricSettingUpdate(enabled)) {
            filter { eq("id", userId) }
        }
    }

    suspend fun updateDefaultScreen(userId: String, screen: String) {
        supabase.from("users").update(DefaultScreenUpdate(screen)) {
            filter { eq("id", userId) }
        }
    }

    suspend fun updateWeightUnit(userId: String, unit: String) {
        supabase.from("users").update(WeightUnitUpdate(unit)) {
            filter { eq("id", userId) }
        }
    }

    // -----------------------------------------------------------------------
    // Password — delegates to AuthService
    // Mirrors iOS UserService.updatePassword(newPassword:)
    // -----------------------------------------------------------------------

    /** Delegates password change to the Supabase Auth layer. */
    suspend fun changePassword(newPassword: String) {
        AuthService.updatePassword(newPassword)
    }

    // -----------------------------------------------------------------------
    // Activity Streak — "user_activities" table
    // Mirrors iOS fetchUserStreak(userId:) / recordActivity(userId:type:...)
    // -----------------------------------------------------------------------

    /**
     * Calculates the current consecutive activity streak.
     * Walks backwards from today until a day with no activity is found.
     * Mirrors iOS UserService.fetchUserStreak(userId:) exactly.
     */
    suspend fun calculateStreak(userId: String): Int {
        var streak = 0
        var currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        while (true) {
            val startOfDay = currentDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            val endOfDay = currentDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

            val activities = supabase
                .from("user_activities")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("activity_date", startOfDay.toString())
                        lt("activity_date", endOfDay.toString())
                    }
                }
                .decodeList<UserActivity>()

            if (activities.isEmpty()) {
                // If today has no activity, skip to yesterday and keep counting
                if (currentDate == LocalDate.now()) {
                    currentDate = currentDate.minusDays(1)
                    continue
                }
                break
            }

            streak++
            currentDate = currentDate.minusDays(1)
        }

        return streak
    }

    /**
     * Returns all activities for a specific date (YYYY-MM-DD).
     */
    suspend fun fetchActivitiesForDate(userId: String, date: LocalDate): List<UserActivity> {
        val dateStr = date.toString() // yyyy-MM-dd
        return try {
            supabase.from("user_activities")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("activity_date", dateStr)
                        lt("activity_date", date.plusDays(1).toString())
                    }
                }
                .decodeList<UserActivity>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Inserts a new activity record into "user_activities".
     * Mirrors iOS UserService.recordActivity(userId:type:duration:calories:).
     */
    suspend fun recordActivity(
        userId: String,
        type: String,
        duration: Int? = null,
        calories: Int? = null
    ) {
        val activity = UserActivityInsert(
            userId = userId,
            activityDate = java.time.LocalDate.now().toString(),
            activityType = type,
            duration = duration,
            caloriesBurned = calories
        )
        supabase.from("user_activities").insert(activity)
    }
}
