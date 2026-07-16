package com.app.coachup.app.utils

import java.time.LocalDate

/**
 * Takvim günü eşleştirmesi — admin paneli (JS/PG DOW) ile uygulama (0=Pzt) uyumu.
 */
object CalendarDayOfWeek {

    /** Uygulama: 0 = Pazartesi … 6 = Pazar */
    fun appIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    /** JavaScript getDay() / PostgreSQL EXTRACT(DOW): 0 = Pazar … 6 = Cumartesi */
    fun jsOrPgIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** ISO-8601: 1 = Pazartesi … 7 = Pazar */
    fun isoIndex(date: LocalDate): Int = date.dayOfWeek.value

    fun classMatchesDate(storedDay: Int, date: LocalDate): Boolean {
        val app = appIndex(date)
        val js = jsOrPgIndex(date)
        val iso = isoIndex(date)
        return storedDay == app
            || storedDay == js
            || storedDay == iso
            || storedDay == app + 1
    }
}
