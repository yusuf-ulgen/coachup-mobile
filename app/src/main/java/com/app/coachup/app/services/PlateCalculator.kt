package com.app.coachup.app.services

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// PlateCalculator — mirrors iOS PlateCalculator.swift
//
// Pure, stateless barbell plate calculation utilities.
// ---------------------------------------------------------------------------

data class LoadedPlate(
    val weight: Double,
    val count: Int
)

data class PlateLoad(
    val barWeight: Double,
    val perSide: List<LoadedPlate>,
    val remainder: Double
) {
    /** Total weight actually represented (bar + perSide × 2). */
    val totalWeight: Double get() =
        barWeight + perSide.sumOf { it.weight * it.count } * 2.0

    val isBarOnly: Boolean get() = perSide.isEmpty()
}

object PlateCalculator {

    /** Standard IPF kg plate set (descending). */
    val defaultPlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    const val defaultBarWeight: Double = 20.0

    /**
     * Greedy per-side plate calculation.
     *
     * @param target  Target total load in kg (bar + plates).
     * @param bar     Barbell weight. Default 20 kg.
     * @param plates  Available plates in kg (descending). Defaults to [defaultPlates].
     * @return [PlateLoad] describing the plates per side and any remainder.
     */
    fun calculate(
        target: Double,
        bar: Double = defaultBarWeight,
        plates: List<Double> = defaultPlates
    ): PlateLoad {
        if (target < bar) {
            return PlateLoad(barWeight = bar, perSide = emptyList(), remainder = target - bar)
        }

        var perSideWeight = (target - bar) / 2.0
        val sortedPlates = plates.sortedDescending()
        val result = mutableListOf<LoadedPlate>()
        val epsilon = 0.001

        for (plate in sortedPlates) {
            if (plate <= 0) continue
            val count = ((perSideWeight + epsilon) / plate).toInt()
            if (count > 0) {
                result.add(LoadedPlate(weight = plate, count = count))
                perSideWeight -= count * plate
            }
        }

        val remainderPerSide = if (perSideWeight < epsilon) 0.0 else perSideWeight
        return PlateLoad(barWeight = bar, perSide = result, remainder = remainderPerSide * 2.0)
    }

    // -----------------------------------------------------------------------
    // 1RM Estimation Formulas — mirrors iOS RecordAttemptService.swift
    // -----------------------------------------------------------------------

    /** Epley formula: weight × (1 + reps/30). Returns weight as-is for reps == 1. */
    fun epley1RM(weight: Double, reps: Int): Double {
        if (reps == 1) return weight
        return weight * (1.0 + reps.toDouble() / 30.0)
    }

    /** Brzycki formula: weight × 36 / (37 − reps). Only valid for reps < 37. */
    fun brzycki1RM(weight: Double, reps: Int): Double {
        if (reps >= 37) return epley1RM(weight, reps)
        return weight * 36.0 / (37.0 - reps.toDouble())
    }

    /** Preferred 1RM estimate using average of Epley and Brzycki. */
    fun calculateEstimated1RM(weight: Double, reps: Int): Double {
        val e = epley1RM(weight, reps)
        val b = brzycki1RM(weight, reps)
        return (e + b) / 2.0
    }

    /** Round a weight to the nearest step (default 2.5 kg). */
    fun roundWeight(value: Double, toNearest: Double = 2.5): Double {
        return Math.round(value / toNearest) * toNearest
    }

    /**
     * Generate a standard warm-up plan for a target lift.
     *
     * Warm-up reps scale with [targetReps] (never fixed 8×5×3 when hedef 10 tekrar).
     */
    fun generateWarmupPlan(
        targetWeight: Double,
        targetReps: Int
    ): List<PlannedSet> {
        val warmupScheme = listOf(
            Pair(0.40, 0.50),
            Pair(0.60, 0.40),
            Pair(0.75, 0.30),
            Pair(0.85, 0.20),
            Pair(0.90, 0.10)
        )

        fun warmupReps(fraction: Double): Int = when {
            targetReps <= 1 -> 1
            else -> maxOf(
                1,
                min(
                    (targetReps * fraction).roundToInt(),
                    targetReps - 1
                )
            )
        }

        val sets = mutableListOf<PlannedSet>()
        for ((pct, repFraction) in warmupScheme) {
            val w = roundWeight(targetWeight * pct)
            sets.add(
                PlannedSet(
                    plannedWeight = w,
                    plannedReps = warmupReps(repFraction),
                    isMain = false,
                    percentOf1RM = pct
                )
            )
        }
        sets.add(PlannedSet(plannedWeight = targetWeight, plannedReps = targetReps, isMain = true, percentOf1RM = 1.0))
        return sets
    }

    /** Tek ana set — koşu, WOD ve vücut ağırlığı rekorları için. */
    fun generateSimpleMainPlan(
        targetValue: Double,
        targetReps: Int = 1
    ): List<PlannedSet> = listOf(
        PlannedSet(
            plannedWeight = targetValue,
            plannedReps = targetReps,
            isMain = true,
            percentOf1RM = 1.0
        )
    )
}

data class PlannedSet(
    val plannedWeight: Double,
    val plannedReps: Int,
    val isMain: Boolean,
    val percentOf1RM: Double,
    // Filled in after set completion
    var actualWeight: Double? = null,
    var actualReps: Int? = null,
    var rpe: Int? = null,
    var notes: String = "",
    var isCompleted: Boolean = false
)
