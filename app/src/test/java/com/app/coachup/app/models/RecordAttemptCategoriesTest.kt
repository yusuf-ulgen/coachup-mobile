package com.app.coachup.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for rekor kategorileri / CrossFit AMRAP meta.
 */
class RecordAttemptCategoriesTest {

    @Test
    fun all_containsExpectedCategories() {
        val ids = RecordAttemptCategories.all.map { it.id }
        assertTrue(ids.containsAll(listOf("strength", "bodyweight", "running", "cardio", "benchmark")))
    }

    @Test
    fun strength_exercisesAreWeight() {
        val strength = RecordAttemptCategories.findCategory("strength")
        assertNotNull(strength)
        assertTrue(strength!!.exercises.isNotEmpty())
        assertTrue(strength.exercises.all { it.measureType == RecordMeasureType.WEIGHT })
        assertTrue(strength.exercises.any { it.id == "back_squat" })
    }

    @Test
    fun bodyweight_exercisesAreReps() {
        val bw = RecordAttemptCategories.findCategory("bodyweight")!!
        assertTrue(bw.exercises.all { it.measureType == RecordMeasureType.REPS })
        assertTrue(bw.exercises.any { it.id == "pull_up" })
    }

    @Test
    fun running_exercisesAreTime() {
        val run = RecordAttemptCategories.findCategory("running")!!
        assertTrue(run.exercises.all { it.measureType == RecordMeasureType.TIME })
        assertTrue(run.exercises.any { it.id == "run_5k" })
    }

    @Test
    fun benchmark_includesFranAndCindy() {
        val bench = RecordAttemptCategories.findCategory("benchmark")!!
        val ids = bench.exercises.map { it.id }
        assertTrue(ids.contains("fran"))
        assertTrue(ids.contains("cindy"))
        assertEquals(11, bench.exercises.size)
    }

    @Test
    fun cindy_isAmrapWith20MinuteCap() {
        assertTrue(RecordAttemptCategories.isAmrapCatalog("cindy"))
        assertTrue(RecordAttemptCategories.isAmrapCatalog("CINDY"))
        assertFalse(RecordAttemptCategories.isAmrapCatalog("fran"))
        assertFalse(RecordAttemptCategories.isAmrapCatalog(null))
        assertEquals(20 * 60, RecordAttemptCategories.amrapCapSeconds("cindy"))
        assertEquals(0, RecordAttemptCategories.amrapCapSeconds("fran"))
    }

    @Test
    fun cindy_defaultTargetIs1200Seconds() {
        val cindy = RecordAttemptCategories.findCategory("benchmark")!!
            .exercises.first { it.id == "cindy" }
        assertEquals(1200.0, cindy.defaultTarget!!, 0.01)
        assertEquals(RecordMeasureType.TIME, cindy.measureType)
    }

    @Test
    fun fran_isForTimeNotAmrap() {
        val fran = RecordAttemptCategories.findCategory("benchmark")!!
            .exercises.first { it.id == "fran" }
        assertFalse(RecordAttemptCategories.isAmrapCatalog(fran.id))
        assertEquals(RecordMeasureType.TIME, fran.measureType)
    }
}
