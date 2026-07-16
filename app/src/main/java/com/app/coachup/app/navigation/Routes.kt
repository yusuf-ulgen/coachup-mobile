package com.app.coachup.app.navigation

/**
 * Centralised route constants for Navigation Compose.
 *
 * Parametrised routes use {placeholder} syntax; helper functions
 * produce the resolved path string.
 */
object Routes {

    // ─── Auth ────────────────────────────────────────────────────────────────
    const val LOGIN = "login"
    const val REGISTER = "register"

    // ─── Main tabs ───────────────────────────────────────────────────────────
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val TRAINING = "training"
    const val QR_ENTRY = "qr_entry"

    // ─── Feature screens ─────────────────────────────────────────────────────
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val COACHES = "coaches"
    const val COACH_DETAIL = "coach_detail/{id}"
    const val COACH_CHAT = "coach_chat/{id}"
    const val RESULTS = "results"
    const val RESULT_DETAIL = "result_detail/{id}"
    const val PERSONAL_RECORDS = "personal_records"
    const val STREAK = "streak"
    const val APPOINTMENTS = "appointments"
    const val GROUP_CLASSES = "group_classes"
    const val NUTRITION = "nutrition"
    const val PROGRESS = "progress"
    const val GOALS = "goals"
    const val MEMBERSHIP = "membership"
    const val PAYMENTS = "payments"
    const val SURVEYS = "surveys"
    const val RESERVATIONS = "reservations"
    const val ALL_ENTRY_HISTORY = "all_entry_history"

    // ─── Active workout ──────────────────────────────────────────────────────
    const val ACTIVE_WORKOUT = "active_workout/{sessionId}/{programId}"

    // ─── Record Attempt ──────────────────────────────────────────────────────
    const val RECORD_ATTEMPT_SETUP   = "record_attempt_setup"
    const val RECORD_ATTEMPT_SESSION = "record_attempt_session"
    const val RECORD_ATTEMPT_SUMMARY = "record_attempt_summary"

    // ─── Settings sub-screens ────────────────────────────────────────────────
    const val SETTINGS = "settings"
    const val SETTINGS_DEFAULT_SCREEN = "settings/default_screen"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_LANGUAGE = "settings/language"
    const val SETTINGS_PASSWORD = "settings/password"
    const val SETTINGS_ADDRESS = "settings/address"
    const val SETTINGS_EMERGENCY_CONTACT = "settings/emergency_contact"
    const val SETTINGS_MEMBERSHIP = "settings/membership_settings"
    const val SETTINGS_REQUEST_MEMBERSHIP = "settings/request_membership"

    // ─── Admin ───────────────────────────────────────────────────────────────
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_COACHES = "admin_coaches"
    const val ADMIN_GYMS = "admin_gyms"
    const val ADMIN_MEMBERSHIP_PLANS = "admin_membership_plans"
    const val ADMIN_PROGRAMS = "admin_programs"
    const val ADMIN_AUDIT_LOGS = "admin_audit_logs"
    const val ADMIN_QR_ENTRIES = "admin_qr_entries"
    const val ADMIN_REPORTS = "admin_reports"

    // ─── Resolved path helpers ───────────────────────────────────────────────
    fun coachDetail(id: String) = "coach_detail/$id"
    fun coachChat(id: String) = "coach_chat/$id"
    fun resultDetail(id: String) = "result_detail/$id"
    fun activeWorkout(sessionId: String, programId: String) = "active_workout/$sessionId/$programId"
}
