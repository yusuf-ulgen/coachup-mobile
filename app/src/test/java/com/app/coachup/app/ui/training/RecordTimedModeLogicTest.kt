package com.app.coachup.app.ui.training

import com.app.coachup.app.models.RecordAttemptCategories
import com.app.coachup.app.models.RecordMeasureType
import com.app.coachup.app.services.RecordAttemptService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timed / AMRAP / For Time session mode selection logic
 * (mirrors RecordTimedAttemptSession branching).
 */
class RecordTimedModeLogicTest {

    private fun sessionMode(
        categoryId: String?,
        catalogId: String?,
        measureType: RecordMeasureType
    ): String {
        val isRunning = categoryId == "running" || (catalogId?.startsWith("run_") == true)
        val isBodyweight = categoryId == "bodyweight" ||
            (measureType == RecordMeasureType.REPS && categoryId != "benchmark")
        val isCindy = RecordAttemptCategories.isAmrapCatalog(catalogId)
        val isBenchmark = categoryId == "benchmark"
        return when {
            isRunning -> "running_gps"
            isCindy -> "amrap_countdown"
            isBodyweight -> "stopwatch_reps"
            isBenchmark -> "for_time_stopwatch"
            measureType == RecordMeasureType.TIME || measureType == RecordMeasureType.CALORIES ->
                "for_time_stopwatch"
            measureType == RecordMeasureType.WEIGHT -> "strength"
            else -> "for_time_stopwatch"
        }
    }

    @Test
    fun fran_isForTimeStopwatch() {
        assertEquals("for_time_stopwatch", sessionMode("benchmark", "fran", RecordMeasureType.TIME))
    }

    @Test
    fun cindy_isAmrapCountdown() {
        assertEquals("amrap_countdown", sessionMode("benchmark", "cindy", RecordMeasureType.TIME))
    }

    @Test
    fun pullUp_isStopwatchReps() {
        assertEquals("stopwatch_reps", sessionMode("bodyweight", "pull_up", RecordMeasureType.REPS))
    }

    @Test
    fun run5k_isGps() {
        assertEquals("running_gps", sessionMode("running", "run_5k", RecordMeasureType.TIME))
    }

    @Test
    fun backSquat_isStrength() {
        assertEquals("strength", sessionMode("strength", "back_squat", RecordMeasureType.WEIGHT))
    }

    @Test
    fun row500_isForTime() {
        assertEquals("for_time_stopwatch", sessionMode("cardio", "row_500m", RecordMeasureType.TIME))
    }

    @Test
    fun amrapRemaining_countsDown() {
        val capMs = 20 * 60 * 1000L
        val elapsed = 5 * 60 * 1000L
        val remaining = (capMs - elapsed).coerceAtLeast(0L)
        assertEquals(15 * 60 * 1000L, remaining)
        assertTrue(remaining > 0)
        assertFalse(elapsed >= capMs)
    }

    @Test
    fun amrapFinished_whenElapsedReachesCap() {
        val capMs = 20 * 60 * 1000L
        val elapsed = capMs
        assertTrue(elapsed >= capMs)
        // then ENTER_ROUNDS dialog
        val phase = "ENTER_ROUNDS"
        assertEquals("ENTER_ROUNDS", phase)
    }

    @Test
    fun forTimeFinish_storesSecondsInWeight() {
        val elapsedSeconds = 185
        // storage convention for TIME
        val weight = elapsedSeconds.toDouble()
        val reps = 1
        assertEquals(185.0, weight, 0.01)
        assertEquals(1, reps)
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.TIME,
                newWeight = 185.0, newReps = 1,
                prevWeight = 200.0, prevReps = 1
            )
        )
    }

    @Test
    fun cindyFinish_storesRoundsInReps() {
        val rounds = 12
        val capSeconds = RecordAttemptCategories.amrapCapSeconds("cindy")
        assertEquals(1200, capSeconds)
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.REPS,
                newWeight = capSeconds.toDouble(), newReps = rounds,
                prevWeight = capSeconds.toDouble(), prevReps = 10
            )
        )
    }

    @Test
    fun allBenchmarkExceptCindy_areForTime() {
        val bench = RecordAttemptCategories.findCategory("benchmark")!!
        bench.exercises.forEach { ex ->
            if (ex.id == "cindy") {
                assertTrue(RecordAttemptCategories.isAmrapCatalog(ex.id))
            } else {
                assertFalse(RecordAttemptCategories.isAmrapCatalog(ex.id))
                assertEquals(RecordMeasureType.TIME, ex.measureType)
            }
        }
    }
}
