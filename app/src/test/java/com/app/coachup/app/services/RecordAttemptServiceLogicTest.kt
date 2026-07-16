package com.app.coachup.app.services

import com.app.coachup.app.models.RecordMeasureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic tests for rekor denemesi:
 * - koşu mesafe hedefleri
 * - PR karşılaştırması (ağırlık / tekrar / süre / AMRAP tur / kalori)
 * - waitlist join kararı
 */
class RecordAttemptServiceLogicTest {

    // ── Running distances ────────────────────────────────────────────────────

    @Test
    fun runningTargetKm_mapsCatalogIds() {
        assertEquals(0.4, RecordAttemptService.runningTargetKm("run_400m"), 0.0001)
        assertEquals(0.8, RecordAttemptService.runningTargetKm("run_800m"), 0.0001)
        assertEquals(1.0, RecordAttemptService.runningTargetKm("run_1k"), 0.0001)
        assertEquals(3.0, RecordAttemptService.runningTargetKm("run_3k"), 0.0001)
        assertEquals(5.0, RecordAttemptService.runningTargetKm("run_5k"), 0.0001)
        assertEquals(10.0, RecordAttemptService.runningTargetKm("run_10k"), 0.0001)
        assertEquals(21.0975, RecordAttemptService.runningTargetKm("run_half"), 0.0001)
        assertEquals(42.195, RecordAttemptService.runningTargetKm("run_full"), 0.0001)
        assertEquals(1.0, RecordAttemptService.runningTargetKm("unknown"), 0.0001)
    }

    // ── PR comparison ────────────────────────────────────────────────────────

    @Test
    fun isBetter_weight_higher1rmWins() {
        // 100x1 ~ 100 1RM vs 90x1 ~ 90
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.WEIGHT,
                newWeight = 100.0, newReps = 1,
                prevWeight = 90.0, prevReps = 1
            )
        )
        assertFalse(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.WEIGHT,
                newWeight = 80.0, newReps = 1,
                prevWeight = 100.0, prevReps = 1
            )
        )
    }

    @Test
    fun isBetter_reps_higherWins() {
        // Pull-up / Cindy rounds
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.REPS,
                newWeight = 0.0, newReps = 15,
                prevWeight = 0.0, prevReps = 10
            )
        )
        assertFalse(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.REPS,
                newWeight = 1200.0, newReps = 8,
                prevWeight = 1200.0, prevReps = 12
            )
        )
    }

    @Test
    fun isBetter_cindyAmrap_moreRoundsIsBetter() {
        // Cindy: weight=cap, reps=rounds
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.REPS,
                newWeight = 1200.0, newReps = 14,
                prevWeight = 1200.0, prevReps = 11
            )
        )
    }

    @Test
    fun isBetter_time_lowerSecondsWins() {
        // Fran: weight = seconds
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.TIME,
                newWeight = 180.0, newReps = 1,
                prevWeight = 210.0, prevReps = 1
            )
        )
        assertFalse(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.TIME,
                newWeight = 250.0, newReps = 1,
                prevWeight = 200.0, prevReps = 1
            )
        )
    }

    @Test
    fun isBetter_time_zeroNotBetter() {
        assertFalse(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.TIME,
                newWeight = 0.0, newReps = 1,
                prevWeight = 200.0, prevReps = 1
            )
        )
    }

    @Test
    fun isBetter_calories_higherCalOrFasterSameCal() {
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.CALORIES,
                newWeight = 50.0, newReps = 120,
                prevWeight = 10.0, prevReps = 60
            )
        )
        // same cal, faster (lower seconds in reps)
        assertTrue(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.CALORIES,
                newWeight = 50.0, newReps = 90,
                prevWeight = 50.0, prevReps = 120
            )
        )
        assertFalse(
            RecordAttemptService.isBetterPersonalRecord(
                RecordMeasureType.CALORIES,
                newWeight = 50.0, newReps = 150,
                prevWeight = 50.0, prevReps = 100
            )
        )
    }

    // ── Join / waitlist ──────────────────────────────────────────────────────

    @Test
    fun resolveJoinResult_confirmedWhenSpace() {
        assertEquals(
            GroupClassService.JoinResult.CONFIRMED,
            RecordAttemptService.resolveJoinResult(
                alreadyJoined = false,
                currentSeats = 3,
                capacity = 10
            )
        )
    }

    @Test
    fun resolveJoinResult_waitingWhenFull() {
        assertEquals(
            GroupClassService.JoinResult.WAITING,
            RecordAttemptService.resolveJoinResult(
                alreadyJoined = false,
                currentSeats = 10,
                capacity = 10
            )
        )
    }

    @Test
    fun resolveJoinResult_alreadyJoined() {
        assertEquals(
            GroupClassService.JoinResult.ALREADY_JOINED,
            RecordAttemptService.resolveJoinResult(
                alreadyJoined = true,
                currentSeats = 10,
                capacity = 10
            )
        )
    }

    @Test
    fun resolveJoinResult_nullCapacityAlwaysConfirmed() {
        assertEquals(
            GroupClassService.JoinResult.CONFIRMED,
            RecordAttemptService.resolveJoinResult(
                alreadyJoined = false,
                currentSeats = 999,
                capacity = null
            )
        )
    }
}
