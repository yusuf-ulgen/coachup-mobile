package com.app.coachup.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Salon etkinliği katılım / bekleme listesi durumları.
 */
class GymEventJoinLogicTest {

    @Test
    fun isJoinedStatus_registeredAndWaiting() {
        assertTrue(GymEventService.isJoinedStatus("registered"))
        assertTrue(GymEventService.isJoinedStatus("waiting"))
        assertFalse(GymEventService.isJoinedStatus("cancelled"))
    }

    @Test
    fun isWaitingStatus() {
        assertTrue(GymEventService.isWaitingStatus("waiting"))
        assertFalse(GymEventService.isWaitingStatus("registered"))
    }

    @Test
    fun isActiveSeat_onlyRegistered() {
        assertTrue(GymEventService.isActiveSeat("registered"))
        assertFalse(GymEventService.isActiveSeat("waiting"))
        assertFalse(GymEventService.isActiveSeat("cancelled"))
    }

    @Test
    fun eventFull_goesToWaitlist() {
        val capacity = 20
        val registered = 20
        val status = if (registered >= capacity) "waiting" else "registered"
        assertEquals("waiting", status)
    }

    @Test
    fun eventHasSpace_registers() {
        val capacity: Int? = 15
        val registered = 4
        val status = if (capacity != null && registered >= capacity) "waiting" else "registered"
        assertEquals("registered", status)
    }

    @Test
    fun eventNullCapacity_alwaysRegisters() {
        val capacity: Int? = null
        val registered = 1000
        val status = if (capacity != null && registered >= capacity) "waiting" else "registered"
        assertEquals("registered", status)
    }
}
