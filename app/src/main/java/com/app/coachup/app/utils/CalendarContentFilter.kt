package com.app.coachup.app.utils

import com.app.coachup.app.models.ClassBooking
import com.app.coachup.app.models.ScheduledProgramDB

/**
 * Shared calendar filtering rules:
 * - Home / joined view: only scheduled, completed, or active bookings
 * - Full calendar view: all non-cancelled programs + all joinable group classes
 */
object CalendarContentFilter {

    fun joinedPrograms(programs: List<ScheduledProgramDB>): List<ScheduledProgramDB> =
        programs.filter { it.status == "scheduled" || it.status == "completed" }

    fun allVisiblePrograms(programs: List<ScheduledProgramDB>): List<ScheduledProgramDB> =
        programs.filter { it.status != "cancelled" }

    fun joinedBookings(bookings: List<ClassBooking>): List<ClassBooking> =
        bookings.filter { it.status != "cancelled" }
}
