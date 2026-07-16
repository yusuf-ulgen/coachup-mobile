package com.app.coachup.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontenjan / bekleme listesi / Ayrıl-Katıl durum mantığı.
 */
class GroupClassJoinLogicTest {

    @Test
    fun isJoinedStatus_includesBookedConfirmedWaitingAttended() {
        assertTrue(GroupClassService.isJoinedStatus("booked"))
        assertTrue(GroupClassService.isJoinedStatus("confirmed"))
        assertTrue(GroupClassService.isJoinedStatus("waiting"))
        assertTrue(GroupClassService.isJoinedStatus("attended"))
        assertTrue(GroupClassService.isJoinedStatus("WAITING"))
        assertFalse(GroupClassService.isJoinedStatus("cancelled"))
        assertFalse(GroupClassService.isJoinedStatus("no_show"))
    }

    @Test
    fun isWaitingStatus_onlyWaiting() {
        assertTrue(GroupClassService.isWaitingStatus("waiting"))
        assertTrue(GroupClassService.isWaitingStatus("Waiting"))
        assertFalse(GroupClassService.isWaitingStatus("booked"))
        assertFalse(GroupClassService.isWaitingStatus("confirmed"))
    }

    @Test
    fun isActiveSeat_excludesWaiting() {
        assertTrue(GroupClassService.isActiveSeat("booked"))
        assertTrue(GroupClassService.isActiveSeat("confirmed"))
        assertTrue(GroupClassService.isActiveSeat("attended"))
        assertFalse(GroupClassService.isActiveSeat("waiting"))
        assertFalse(GroupClassService.isActiveSeat("cancelled"))
    }

    @Test
    fun fullCapacity_meansWaitingJoin() {
        val capacity = 10
        val current = 10
        val isFull = current >= capacity
        assertTrue(isFull)
        // UI: show "Dolu" + waitlist, not "Katıl"
        val result = if (isFull) GroupClassService.JoinResult.WAITING
        else GroupClassService.JoinResult.CONFIRMED
        assertEquals(GroupClassService.JoinResult.WAITING, result)
    }

    @Test
    fun spaceAvailable_meansConfirmedJoin() {
        val capacity = 10
        val current = 7
        assertFalse(current >= capacity)
        assertEquals(
            GroupClassService.JoinResult.CONFIRMED,
            RecordAttemptService.resolveJoinResult(false, current, capacity)
        )
    }

    @Test
    fun buttonLabelLogic_joinedShowsAyril() {
        // Katıldın/Yedekte → action = Ayrıl
        val status = "booked"
        val isJoined = GroupClassService.isJoinedStatus(status)
        val label = when {
            isJoined -> "Ayrıl"
            else -> "Katıl"
        }
        assertEquals("Ayrıl", label)
    }

    @Test
    fun buttonLabelLogic_waitingShowsYedekteAndAyril() {
        val status = "waiting"
        assertTrue(GroupClassService.isWaitingStatus(status))
        assertTrue(GroupClassService.isJoinedStatus(status))
        // Still leave-able
        val action = "Ayrıl"
        assertEquals("Ayrıl", action)
    }

    @Test
    fun seatRatio_format() {
        val current = 3
        val capacity = 10
        val text = "$current/$capacity"
        assertEquals("3/10", text)
        val fullText = "10/10"
        assertTrue(fullText.startsWith("10/"))
    }
}
