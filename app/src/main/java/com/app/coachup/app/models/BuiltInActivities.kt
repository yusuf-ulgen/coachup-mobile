package com.app.coachup.app.models

/**
 * Varsayılan antrenman modülleri — bireysel ve salon üyeleri için her zaman görünür.
 */
object BuiltInActivities {
    /** Tüm yerleşik aktiviteler için tek FK hedefi — migration ile seed edilir */
    const val UNIVERSAL_PROGRAM_ID = "b0000000-0000-4000-8000-000000000000"

    /** Kategori başına program satırı (opsiyonel; migration ile seed) */
    private val programIds: Map<TrainingCategory, String> = mapOf(
        TrainingCategory.FITNESS to "b0000001-0000-4000-8000-000000000001",
        TrainingCategory.RUNNING to "b0000002-0000-4000-8000-000000000002",
        TrainingCategory.WALKING to "b0000003-0000-4000-8000-000000000003",
        TrainingCategory.CYCLING to "b0000004-0000-4000-8000-000000000004",
        TrainingCategory.SWIMMING to "b0000005-0000-4000-8000-000000000005",
        TrainingCategory.COMBAT to "b0000006-0000-4000-8000-000000000006",
        TrainingCategory.YOGA to "b0000007-0000-4000-8000-000000000007",
        TrainingCategory.PILATES to "b0000008-0000-4000-8000-000000000008",
        TrainingCategory.CROSSFIT to "b0000009-0000-4000-8000-000000000009",
        TrainingCategory.FUNCTIONAL to "b000000a-0000-4000-8000-00000000000a",
        TrainingCategory.HYROX to "b000000b-0000-4000-8000-00000000000b",
        TrainingCategory.CUSTOM to "b000000c-0000-4000-8000-00000000000c"
    )

    fun programId(category: TrainingCategory): String =
        programIds[category] ?: programIds.getValue(TrainingCategory.FITNESS)

    /** FK zorunluysa önce universal, sonra kategoriye özel ID döner */
    fun programIdCandidates(category: TrainingCategory): List<String> =
        listOf(UNIVERSAL_PROGRAM_ID, programId(category)).distinct()

    fun isBuiltinProgramId(id: String): Boolean =
        id == UNIVERSAL_PROGRAM_ID || programIds.containsValue(id)

    fun categoryForProgramId(id: String): TrainingCategory? =
        programIds.entries.firstOrNull { it.value == id }?.key

    fun all(): List<Training> =
        TrainingCategory.defaultModules.map { Training.builtin(it) }
}
