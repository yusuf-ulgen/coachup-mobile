package com.app.coachup.app.utils

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant
import java.util.UUID

/**
 * Audit logging service – mirrors iOS AuditLogger exactly.
 *
 * Log entries are written to the `audit_logs` Supabase table.  On failure the
 * entry is printed to Logcat instead so no user-facing operation is blocked.
 *
 * Initialise once at application startup:
 * ```kotlin
 * AuditLogger.init(supabaseClient)
 * ```
 * Then call via the singleton:
 * ```kotlin
 * AuditLogger.shared.logCreate(...)
 * ```
 */
class AuditLogger private constructor(private val supabase: SupabaseClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "AuditLogger"

    // -----------------------------------------------------------------------
    // Action enum
    // -----------------------------------------------------------------------

    enum class Action(val rawValue: String) {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        EXPORT("export"),
        LOGIN("login"),
        LOGOUT("logout"),
        QR_ENTRY("qr_entry"),
        MANUAL_ENTRY("manual_entry"),
        BULK_OPERATION("bulk_operation");
    }

    // -----------------------------------------------------------------------
    // ResourceType enum
    // -----------------------------------------------------------------------

    enum class ResourceType(val rawValue: String) {
        USER("user"),
        ADMIN("admin"),
        COACH("coach"),
        MEMBER("member"),
        MEMBERSHIP("membership"),
        FINANCIAL_TRANSACTION("financial_transaction"),
        QR_ENTRY("qr_entry"),
        SURVEY("survey"),
        TRAINING("training"),
        ATTENDANCE("attendance");
    }

    // -----------------------------------------------------------------------
    // LogEntry model  (Supabase-serialisable)
    // -----------------------------------------------------------------------

    @Serializable
    data class LogEntry(
        val id: String,
        @SerialName("user_id") val userId: String?,
        @SerialName("user_email") val userEmail: String?,
        @SerialName("user_role") val userRole: String?,
        val action: String,
        @SerialName("resource_type") val resourceType: String,
        @SerialName("resource_id") val resourceId: String?,
        val description: String,
        val changes: JsonElement? = null,
        val metadata: JsonElement? = null,
        @SerialName("gym_id") val gymId: String?,
        val status: String,
        @SerialName("error_message") val errorMessage: String?,
        @SerialName("created_at") val createdAt: String
    )

    // -----------------------------------------------------------------------
    // Core log function
    // -----------------------------------------------------------------------

    /**
     * Writes a structured audit entry to Supabase asynchronously.
     *
     * Failures are caught and printed to Logcat; they never propagate to
     * the caller (matching iOS silent-fail behaviour).
     *
     * @param userId       ID of the acting user.
     * @param userEmail    Email of the acting user.
     * @param userRole     Role string of the acting user.
     * @param action       The [Action] being audited.
     * @param resourceType The [ResourceType] affected.
     * @param resourceId   Optional ID of the affected resource.
     * @param description  Human-readable Turkish description of the action.
     * @param changes      Before/after map for update operations.
     * @param metadata     Additional context key-value pairs.
     * @param gymId        Optional gym scope.
     * @param status       "success" (default) or "failure".
     * @param error        Optional error that caused a failure status.
     */
    fun log(
        userId: String?,
        userEmail: String?,
        userRole: String?,
        action: Action,
        resourceType: ResourceType,
        resourceId: String?,
        description: String,
        changes: Map<String, Any>? = null,
        metadata: Map<String, Any>? = null,
        gymId: String? = null,
        status: String = "success",
        error: Throwable? = null
    ) {
        scope.launch {
            val entry = LogEntry(
                id = UUID.randomUUID().toString(),
                userId = userId,
                userEmail = userEmail,
                userRole = userRole,
                action = action.rawValue,
                resourceType = resourceType.rawValue,
                resourceId = resourceId,
                description = description,
                changes = changes?.let { encodeMap(it) },
                metadata = buildMetadata(additional = metadata),
                gymId = gymId,
                status = status,
                errorMessage = error?.message,
                createdAt = Instant.now().toString()
            )

            try {
                supabase.postgrest["audit_logs"].insert(entry)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist audit entry: ${e.message}")
                Log.d(tag, "Unlogged entry: $description")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Convenience helpers
    // -----------------------------------------------------------------------

    /** Logs a QR or manual gym entry/exit event. */
    fun logQREntry(
        userId: String,
        userEmail: String?,
        code: String,
        method: String,
        gymId: String?,
        entryType: String = "entry"
    ) {
        val actionLabel = if (method == "qr") "QR" else "Manuel"
        val directionLabel = if (entryType == "entry") "giriş" else "çıkış"
        log(
            userId = userId,
            userEmail = userEmail,
            userRole = "user",
            action = if (method == "qr") Action.QR_ENTRY else Action.MANUAL_ENTRY,
            resourceType = ResourceType.QR_ENTRY,
            resourceId = null,
            description = "$actionLabel ile $directionLabel yapıldı",
            metadata = mapOf(
                "code" to code,
                "method" to method,
                "entry_type" to entryType
            ),
            gymId = gymId
        )
    }

    /** Logs a CSV/data export operation. */
    fun logExport(
        userId: String,
        userEmail: String?,
        resourceType: ResourceType,
        recordCount: Int,
        gymId: String?
    ) {
        log(
            userId = userId,
            userEmail = userEmail,
            userRole = "gym_manager",
            action = Action.EXPORT,
            resourceType = resourceType,
            resourceId = null,
            description = "${resourceType.rawValue} verileri dışa aktarıldı ($recordCount kayıt)",
            metadata = mapOf(
                "record_count" to recordCount,
                "export_format" to "csv"
            ),
            gymId = gymId
        )
    }

    /** Logs the creation of a resource. */
    fun logCreate(
        userId: String,
        userEmail: String?,
        userRole: String,
        resourceType: ResourceType,
        resourceId: String,
        data: Map<String, Any>,
        gymId: String?
    ) {
        log(
            userId = userId,
            userEmail = userEmail,
            userRole = userRole,
            action = Action.CREATE,
            resourceType = resourceType,
            resourceId = resourceId,
            description = "${resourceType.rawValue} oluşturuldu",
            changes = mapOf("after" to data),
            gymId = gymId
        )
    }

    /** Logs an update to an existing resource, capturing before/after state. */
    fun logUpdate(
        userId: String,
        userEmail: String?,
        userRole: String,
        resourceType: ResourceType,
        resourceId: String,
        before: Map<String, Any>,
        after: Map<String, Any>,
        gymId: String?
    ) {
        log(
            userId = userId,
            userEmail = userEmail,
            userRole = userRole,
            action = Action.UPDATE,
            resourceType = resourceType,
            resourceId = resourceId,
            description = "${resourceType.rawValue} güncellendi",
            changes = mapOf("before" to before, "after" to after),
            gymId = gymId
        )
    }

    /** Logs the deletion of a resource, capturing the final state for recovery. */
    fun logDelete(
        userId: String,
        userEmail: String?,
        userRole: String,
        resourceType: ResourceType,
        resourceId: String,
        data: Map<String, Any>,
        gymId: String?
    ) {
        log(
            userId = userId,
            userEmail = userEmail,
            userRole = userRole,
            action = Action.DELETE,
            resourceType = resourceType,
            resourceId = resourceId,
            description = "${resourceType.rawValue} silindi",
            changes = mapOf("before" to data),
            gymId = gymId
        )
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Builds the standard metadata object, injecting platform info and
     * timestamp, then merging [additional] entries on top.
     */
    private fun buildMetadata(additional: Map<String, Any>?): JsonElement {
        val base: MutableMap<String, Any> = mutableMapOf(
            "timestamp" to Instant.now().toString(),
            "platform" to "Android"
        )
        additional?.forEach { (k, v) -> base[k] = v }
        return encodeMap(base)
    }

    /** Converts an untyped [Map] to a [JsonElement] via the Kotlin serialisation runtime. */
    private fun encodeMap(map: Map<String, Any>): JsonElement {
        val jsonMap: Map<String, JsonElement> = map.entries.associate { (k, v) ->
            k to when (v) {
                is String -> Json.encodeToJsonElement(v)
                is Int -> Json.encodeToJsonElement(v)
                is Long -> Json.encodeToJsonElement(v)
                is Double -> Json.encodeToJsonElement(v)
                is Boolean -> Json.encodeToJsonElement(v)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    encodeMap(v as Map<String, Any>)
                }
                else -> Json.encodeToJsonElement(v.toString())
            }
        }
        return Json.encodeToJsonElement(jsonMap)
    }

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    companion object {
        @Volatile
        private var _instance: AuditLogger? = null

        /** Singleton accessor – call [init] before first use. */
        val shared: AuditLogger
            get() = _instance
                ?: error("AuditLogger not initialised. Call AuditLogger.init(supabaseClient) in Application.onCreate().")

        /**
         * Initialises the singleton with a [SupabaseClient].
         *
         * Must be called once in `Application.onCreate()` before any logging
         * call is made.
         */
        fun init(supabase: SupabaseClient) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        _instance = AuditLogger(supabase)
                    }
                }
            }
        }
    }
}
