package com.app.coachup.app.services

import com.app.coachup.app.models.Training
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveWorkoutManager {
    data class ActiveSession(
        val training: Training,
        val sessionId: String,
        val programId: String,
        val elapsedSeconds: MutableStateFlow<Int> = MutableStateFlow(0),
        val isPaused: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _showFloatingOverlay = MutableStateFlow(false)
    val showFloatingOverlay = _showFloatingOverlay.asStateFlow()

    fun startSession(training: Training, sessionId: String) {
        stopSession()

        val programId = if (training.isBuiltIn) "builtin_${training.category.dbValue}" else training.id
        val session = ActiveSession(training, sessionId, programId)
        _activeSession.value = session
        _showFloatingOverlay.value = false
        // Timer is NOT started here — it starts explicitly when the user presses "Başlat"
        // via startTimer(). This prevents the elapsed time from counting before the workout begins.
    }

    fun startTimer() {
        val session = _activeSession.value ?: return
        if (session.isPaused.value) {
            session.isPaused.value = false
        }
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                if (!session.isPaused.value) {
                    session.elapsedSeconds.value++
                }
            }
        }
    }

    fun pauseTimer() {
        val session = _activeSession.value ?: return
        session.isPaused.value = true
    }

    fun resumeTimer() {
        val session = _activeSession.value ?: return
        session.isPaused.value = false
    }

    fun togglePause() {
        val session = _activeSession.value ?: return
        session.isPaused.value = !session.isPaused.value
    }

    fun showOverlay() {
        if (_activeSession.value != null) {
            _showFloatingOverlay.value = true
        }
    }

    fun hideOverlay() {
        _showFloatingOverlay.value = false
    }

    fun stopSession() {
        timerJob?.cancel()
        timerJob = null
        _activeSession.value = null
        _showFloatingOverlay.value = false
    }
}
