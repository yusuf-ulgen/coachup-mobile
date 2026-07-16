package com.app.coachup.app.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * UI-ready survey question, normalized from either the legacy [Survey.questions]
 * JSON array or the admin panel's single [Survey.question] + [Survey.category] fields.
 */
data class NormalizedSurveyQuestion(
    val id: String,
    val text: String,
    val type: String,
    val options: List<String> = emptyList(),
    val ratingMin: Int = 1,
    val ratingMax: Int = 5,
)

fun Survey.normalizedQuestions(): List<NormalizedSurveyQuestion> {
    if (questions.isNotEmpty()) {
        return questions.mapIndexedNotNull { index, element -> element.toNormalizedQuestion(index) }
    }

    val text = question?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val (min, max) = parseRatingScale(text, category)
    val type = inferQuestionType(category, text)

    return listOf(
        NormalizedSurveyQuestion(
            id = "answer",
            text = text,
            type = type,
            ratingMin = min,
            ratingMax = max
        )
    )
}

private fun JsonElement.toNormalizedQuestion(index: Int): NormalizedSurveyQuestion? {
    val obj = jsonObjectOrNull() ?: return null
    val id = obj.stringField("id") ?: "q$index"
    val text = obj.stringField("text")
        ?: obj.stringField("question")
        ?: obj.stringField("label")
        ?: "Soru ${index + 1}"
    val type = obj.stringField("type") ?: inferQuestionType(null, text)
    val options = obj["options"]?.jsonArray?.mapNotNull { it.stringOrNull() } ?: emptyList()
    val (min, max) = parseRatingScale(text, obj.stringField("category"), type)

    return NormalizedSurveyQuestion(
        id = id,
        text = text,
        type = type,
        options = options,
        ratingMin = min,
        ratingMax = max
    )
}

private fun inferQuestionType(category: String?, text: String): String {
    return when (category?.lowercase()) {
        "satisfaction", "service_quality", "rating" -> "rating"
        "vote", "poll" -> "single_choice"
        "multiple_choice" -> "multiple_choice"
        else -> when {
            text.contains(Regex("\\(\\d+-\\d+\\)")) -> "rating"
            else -> "text"
        }
    }
}

private fun parseRatingScale(
    text: String,
    category: String?,
    type: String = "rating"
): Pair<Int, Int> {
    if (type != "rating") return 1 to 5

    Regex("\\((\\d+)-(\\d+)\\)").find(text)?.let { match ->
        val min = match.groupValues[1].toIntOrNull() ?: 1
        val max = match.groupValues[2].toIntOrNull() ?: 5
        if (min <= max) return min to max
    }

    return when (category?.lowercase()) {
        "satisfaction" -> 1 to 10
        "service_quality" -> 1 to 5
        else -> 1 to 5
    }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    this as? JsonObject ?: (this as? JsonPrimitive)?.content?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.stringField(key: String): String? =
    this[key]?.stringOrNull()

private fun JsonElement.stringOrNull(): String? = when (this) {
    is JsonPrimitive -> content.takeIf { it.isNotBlank() }
    is JsonObject -> stringField("text") ?: stringField("label") ?: stringField("value")
    is JsonArray -> firstOrNull()?.stringOrNull()
    else -> null
}
