package com.app.coachup.app.models

enum class RecordMeasureType {
    WEIGHT,  // kg (barbell lifts)
    REPS,    // maksimum tekrar
    TIME,    // süre (saniye)
    DISTANCE,
    CALORIES
}

data class RecordExercise(
    val id: String,
    val name: String,
    val measureType: RecordMeasureType = RecordMeasureType.WEIGHT,
    val equipment: String? = null,
    val defaultTarget: Double? = null,
    val defaultReps: Int = 1
)

data class RecordCategory(
    val id: String,
    val name: String,
    val exercises: List<RecordExercise>
) {
    val exerciseCount: Int get() = exercises.size
}

object RecordAttemptCategories {

    val all: List<RecordCategory> = listOf(

        RecordCategory(
            id = "strength",
            name = "Güç Rekorları",
            exercises = listOf(
                wb("back_squat",       "Back Squat"),
                wb("front_squat",      "Front Squat"),
                wb("deadlift",         "Deadlift"),
                wb("bench_press",      "Bench Press"),
                wb("strict_press",     "Strict Press"),
                wb("push_press",       "Push Press"),
                wb("thruster",         "Thruster"),
                wb("overhead_squat",   "Overhead Squat"),
                wb("power_clean",      "Power Clean"),
                wb("squat_clean",      "Squat Clean"),
                wb("power_snatch",     "Power Snatch"),
                wb("squat_snatch",     "Squat Snatch"),
                wb("clean_jerk",       "Clean & Jerk")
            )
        ),

        RecordCategory(
            id = "bodyweight",
            name = "Vücut Ağırlığı Rekorları",
            exercises = listOf(
                rep("pull_up",         "Pull-Up",              default = 10),
                rep("chest_to_bar",    "Chest to Bar",         default = 8),
                rep("bar_muscle_up",   "Bar Muscle-Up",        default = 5),
                rep("ring_muscle_up",  "Ring Muscle-Up",       default = 5),
                rep("push_up",         "Push-Up",              default = 50),
                rep("dips",            "Dips",                 default = 20),
                rep("hspu",            "Handstand Push-Up",    default = 10),
                rep("toes_to_bar",     "Toes to Bar",          default = 15)
            )
        ),

        RecordCategory(
            id = "running",
            name = "Koşu Rekorları",
            exercises = listOf(
                time("run_400m",  "400m",                 default = 90.0),
                time("run_800m",  "800m",                 default = 210.0),
                time("run_1k",    "1 km",                 default = 300.0),
                time("run_3k",    "3 km",                 default = 900.0),
                time("run_5k",    "5 km",                 default = 1500.0),
                time("run_10k",   "10 km",                default = 3000.0),
                time("run_half",  "21 km (Yarı Maraton)", default = 6300.0),
                time("run_full",  "42 km (Maraton)",      default = 12600.0)
            )
        ),

        RecordCategory(
            id = "cardio",
            name = "Kardiyo Rekorları",
            exercises = listOf(
                time("row_500m",    "500m Row",              default = 105.0),
                time("row_1000m",   "1000m Row",             default = 240.0),
                time("row_2000m",   "2000m Row",             default = 480.0),
                time("row_5000m",   "5000m Row",             default = 1200.0),
                cal("bike_10cal",   "Assault Bike 10 Cal",   default = 10.0),
                cal("bike_50cal",   "Assault Bike 50 Cal",   default = 50.0),
                cal("echo_100cal",  "Echo Bike 100 Cal",     default = 100.0),
                time("ski_1000m",   "SkiErg 1000m",          default = 240.0)
            )
        ),

        RecordCategory(
            id = "benchmark",
            name = "CrossFit Benchmark WOD'ları",
            exercises = listOf(
                wod("fran",    "Fran"),
                wod("grace",   "Grace"),
                wod("helen",   "Helen"),
                wod("cindy",   "Cindy"),
                wod("murph",   "Murph"),
                wod("annie",   "Annie"),
                wod("diane",   "Diane"),
                wod("karen",   "Karen"),
                wod("jackie",  "Jackie"),
                wod("nancy",   "Nancy"),
                wod("amanda",  "Amanda")
            )
        )
    )

    fun findCategory(id: String): RecordCategory? = all.firstOrNull { it.id == id }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun wb(id: String, name: String) = RecordExercise(
        id = id, name = name,
        measureType = RecordMeasureType.WEIGHT,
        equipment = "barbell"
    )

    private fun rep(id: String, name: String, default: Int) = RecordExercise(
        id = id, name = name,
        measureType = RecordMeasureType.REPS,
        defaultReps = default
    )

    private fun time(id: String, name: String, default: Double) = RecordExercise(
        id = id, name = name,
        measureType = RecordMeasureType.TIME,
        defaultTarget = default
    )

    private fun cal(id: String, name: String, default: Double) = RecordExercise(
        id = id, name = name,
        measureType = RecordMeasureType.CALORIES,
        defaultTarget = default
    )

    private fun wod(id: String, name: String) = RecordExercise(
        id = id, name = name,
        measureType = RecordMeasureType.TIME,
        defaultTarget = 600.0
    )
}
