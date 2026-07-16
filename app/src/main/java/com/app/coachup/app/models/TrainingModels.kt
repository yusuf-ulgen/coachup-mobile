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

    val estimatedBPM: Int get() = when (this) {
        RUNNING, HYROX -> 155
        WALKING -> 110
        CYCLING -> 140
        SWIMMING -> 145
        CROSSFIT, COMBAT, FUNCTIONAL -> 160
        FITNESS -> 120
        YOGA, PILATES -> 95
        GYM_PROGRAM, AI_PROGRAM, CUSTOM -> 115
    }

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
    val exerciseNames: List<String> = emptyList()
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
            exerciseNames = exerciseNames
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
