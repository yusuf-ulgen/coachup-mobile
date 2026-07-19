package com.app.coachup.app.models

// ---------------------------------------------------------------------------
// Training UI enums and presentation models
// ---------------------------------------------------------------------------

enum class TrainingSource {
    BUILTIN,
    GYM,
    AI
}

enum class TrainingCategory(
    val label: String,
    val dbValue: String,
    val iconName: String,
    val emoji: String
) {
    FITNESS("Fitness", "fitness", "fitness_center", "🏋️"),
    RUNNING("Koşu", "running", "directions_run", "🏃"),
    WALKING("Yürüyüş", "walking", "directions_walk", "🚶"),
    CYCLING("Bisiklet", "cycling", "directions_bike", "🚴"),
    SWIMMING("Yüzme", "swimming", "pool", "🏊"),
    COMBAT("Dövüş Sporları", "combat", "sports_martial_arts", "🥊"),
    YOGA("Yoga", "yoga", "self_improvement", "🧘"),
    PILATES("Pilates", "pilates", "accessibility_new", "🤸"),
    CROSSFIT("CrossFit", "crossfit", "fitness_center", "🔥"),
    FUNCTIONAL("Functional Fitness", "functional", "sports_gymnastics", "⚡"),
    HYROX("Hyrox", "hyrox", "directions_run", "🏁"),
    CUSTOM("Özel Aktivite", "custom", "edit", "📋"),
    GYM_PROGRAM("Salon Programı", "gym_program", "business", "🏢"),
    AI_PROGRAM("AI Program", "ai_program", "smart_toy", "🤖");

    /** GPS + mesafe/süre hedefi (Strava tarzı) */
    val isOutdoor: Boolean
        get() = this in setOf(RUNNING, WALKING, CYCLING, HYROX)

    val isProgramBased: Boolean get() = this == GYM_PROGRAM || this == AI_PROGRAM

    /**
     * Bu aktivite tipi için gösterilecek / kaydedilecek metrikler.
     * Süre her zaman gösterilir, burada ekstra metrikler tanımlanır.
     * Nabız / Kalori sadece akıllı saatten gelir — veri yoksa UI'da "—" gösterilir.
     */
    val trackedMetrics: Set<ActivityMetric> get() = when (this) {
        FITNESS          -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        RUNNING          -> setOf(ActivityMetric.DISTANCE, ActivityMetric.AVG_PACE, ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        WALKING          -> setOf(ActivityMetric.DISTANCE, ActivityMetric.AVG_PACE, ActivityMetric.AVG_HR, ActivityMetric.CALORIES)
        CYCLING          -> setOf(ActivityMetric.DISTANCE, ActivityMetric.AVG_SPEED, ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        SWIMMING         -> setOf(ActivityMetric.DISTANCE, ActivityMetric.AVG_HR, ActivityMetric.CALORIES)
        COMBAT           -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        YOGA             -> setOf(ActivityMetric.AVG_HR, ActivityMetric.CALORIES)
        PILATES          -> setOf(ActivityMetric.AVG_HR, ActivityMetric.CALORIES)
        CROSSFIT         -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        FUNCTIONAL       -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        HYROX            -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        CUSTOM           -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        GYM_PROGRAM      -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
        AI_PROGRAM       -> setOf(ActivityMetric.AVG_HR, ActivityMetric.MAX_HR, ActivityMetric.CALORIES)
    }

    /** Bu aktivite mesafe takip ediyor mu? */
    val tracksDistance: Boolean get() = ActivityMetric.DISTANCE in trackedMetrics

    /** Bu aktivite tempo (min/km) gösterecek mi? */
    val tracksPace: Boolean get() = ActivityMetric.AVG_PACE in trackedMetrics

    /** Bu aktivite hız (km/h) gösterecek mi? */
    val tracksSpeed: Boolean get() = ActivityMetric.AVG_SPEED in trackedMetrics

    companion object {
        val defaultModules: List<TrainingCategory> = listOf(
            FITNESS, RUNNING, WALKING, CYCLING, SWIMMING, COMBAT,
            YOGA, PILATES, CROSSFIT, FUNCTIONAL, HYROX, CUSTOM
        )

        fun fromDbValue(v: String): TrainingCategory =
            entries.firstOrNull { it.dbValue == v } ?: FITNESS
    }
}

enum class Difficulty(val label: String) {
    BEGINNER("Başlangıç"),
    INTERMEDIATE("Orta"),
    ADVANCED("İleri");

    companion object {
        fun fromDbValue(v: String): Difficulty = when (v) {
            "beginner" -> BEGINNER
            "advanced" -> ADVANCED
            else -> INTERMEDIATE
        }
    }
}

data class Training(
    val id: String,
    val title: String,
    val category: TrainingCategory,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val iconName: String = "fitness_center",
    val source: TrainingSource,
    val description: String? = null,
    val exerciseNames: List<String> = emptyList(),
    val programText: String? = null,
    val privacy: String? = null,
    val visibleMemberIds: List<String> = emptyList()
) {
    val isBuiltIn: Boolean get() = source == TrainingSource.BUILTIN
    val isOutdoor: Boolean get() = category.isOutdoor

    companion object {
        fun builtin(category: TrainingCategory) = Training(
            id = "builtin_${category.dbValue}",
            title = category.label,
            category = category,
            iconName = category.iconName,
            source = TrainingSource.BUILTIN
        )

        fun fromProgram(
            program: TrainingProgram,
            source: TrainingSource,
            exerciseNames: List<String> = emptyList()
        ) = Training(
            id = program.id,
            title = program.name,
            category = when (source) {
                TrainingSource.AI -> TrainingCategory.AI_PROGRAM
                TrainingSource.GYM -> TrainingCategory.GYM_PROGRAM
                TrainingSource.BUILTIN -> TrainingCategory.fromDbValue(program.category ?: "fitness")
            },
            difficulty = Difficulty.fromDbValue(program.difficulty ?: "intermediate"),
            iconName = program.iconName ?: "fitness_center",
            source = source,
            description = program.description,
            exerciseNames = exerciseNames,
            programText = program.programText,
            privacy = program.privacy,
            visibleMemberIds = program.visibleMemberIds
        )
    }
}

/** Antrenman oturumu notlarına gömülü süre / mesafe meta verisi. */
object SessionWorkoutMeta {
    private const val META_SEP = "|"

    fun encodeCompletionNotes(
        categoryDbValue: String?,
        durationSeconds: Int,
        distanceKm: Double = 0.0
    ): String {
        val base = categoryDbValue?.let { "builtin:$it" }.orEmpty()
        val meta = buildMeta(durationSeconds, distanceKm)
        return if (base.isEmpty()) meta else "$base$META_SEP$meta"
    }

    fun builtinCategoryDbValue(notes: String?): String? {
        if (notes.isNullOrBlank()) return null
        val head = notes.substringBefore(META_SEP)
        if (!head.startsWith("builtin:")) return null
        return head.removePrefix("builtin:")
    }

    fun durationSeconds(notes: String?): Int? {
        metaPart(notes)?.let { meta ->
            meta.split(';').firstOrNull { it.startsWith("duration_s=") }
                ?.removePrefix("duration_s=")?.toIntOrNull()?.let { return it }
        }
        return null
    }

    fun distanceKm(notes: String?): Double? {
        metaPart(notes)?.let { meta ->
            meta.split(';').firstOrNull { it.startsWith("distance_km=") }
                ?.removePrefix("distance_km=")?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun buildMeta(durationSeconds: Int, distanceKm: Double): String {
        val parts = mutableListOf("duration_s=$durationSeconds")
        if (distanceKm > 0.01) parts.add("distance_km=${"%.3f".format(distanceKm)}")
        return parts.joinToString(";")
    }

    private fun metaPart(notes: String?): String? {
        if (notes.isNullOrBlank() || !notes.contains(META_SEP)) return null
        return notes.substringAfter(META_SEP)
    }
}

// ---------------------------------------------------------------------------
// Activity Metrics — defines which data fields each activity type tracks
// ---------------------------------------------------------------------------

enum class ActivityMetric(val label: String, val unit: String, val emoji: String) {
    DISTANCE("Mesafe", "km", "📏"),
    AVG_PACE("Ort. Tempo", "dk/km", "⏱️"),
    AVG_SPEED("Ort. Hız", "km/sa", "💨"),
    AVG_HR("Ort. Nabız", "bpm", "❤️"),
    MAX_HR("Maks. Nabız", "bpm", "❤️"),
    CALORIES("Kalori", "kcal", "🔥");

    /** True if this metric requires a smartwatch / Health Connect */
    val requiresWearable: Boolean get() = this in setOf(AVG_HR, MAX_HR, CALORIES)
}

// ---------------------------------------------------------------------------
// Perceived Effort — post-workout "Nasıl hissediyorsun?" response
// ---------------------------------------------------------------------------

enum class PerceivedEffort(
    val label: String,
    val emoji: String,
    val dbValue: String
) {
    GREAT("Harika", "😁", "great"),
    GOOD("İyi", "🙂", "good"),
    NORMAL("Normal", "😐", "normal"),
    HARD("Zor", "😤", "hard"),
    VERY_HARD("Çok Zor", "🥵", "very_hard");

    companion object {
        fun fromDbValue(v: String): PerceivedEffort? =
            entries.firstOrNull { it.dbValue == v }
    }
}
