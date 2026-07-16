package com.app.coachup.app.navigation

import java.time.LocalDate

/**
 * In-process store for the main bottom-tab selection and home date strip.
 *
 * Survives Compose disposal during token refresh (when [AuthService.currentUser]
 * briefly emits null) and complements [rememberSaveable] for process death.
 */
object HomeTabState {
    var selectedTabName: String = "HOME"
    var selectedDate: String = LocalDate.now().toString()
    var hasAppliedDefaultTab: Boolean = false
    var hasUserSelectedTab: Boolean = false

    fun reset() {
        selectedTabName = "HOME"
        selectedDate = LocalDate.now().toString()
        hasAppliedDefaultTab = false
        hasUserSelectedTab = false
    }
}
