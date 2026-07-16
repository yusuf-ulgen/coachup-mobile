package com.app.coachup.app.ui.surveys

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.NormalizedSurveyQuestion
import com.app.coachup.app.models.Survey
import com.app.coachup.app.models.normalizedQuestions
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.SurveyService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun SurveysScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }
    var surveys by remember { mutableStateOf<List<Survey>>(emptyList()) }
    var respondedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSurvey by remember { mutableStateOf<Survey?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val currentProfile by UserService.currentProfile.collectAsState()

    suspend fun loadData() {
        if (currentProfile == null) return
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            surveys = SurveyService.fetchActiveSurveys()
            val ids = mutableSetOf<String>()
            surveys.forEach { s ->
                if (SurveyService.hasResponded(s.id, userId)) ids.add(s.id)
            }
            respondedIds = ids
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(currentProfile?.id, UserService.isIndividualUser(currentProfile)) {
        if (currentProfile == null) {
            isLoading = true
        } else {
            loadData()
        }
    }

    selectedSurvey?.let { survey ->
        SurveyFormDialog(
            survey = survey,
            onDismiss = { selectedSurvey = null },
            onSubmitted = {
                selectedSurvey = null
                coroutineScope.launch { loadData() }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Anketler", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else if (surveys.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Text(text = "Aktif anket bulunamadı", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(surveys) { survey ->
                    SurveyCard(
                        survey = survey,
                        isResponded = respondedIds.contains(survey.id),
                        onTap = { if (!respondedIds.contains(survey.id)) selectedSurvey = survey }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SurveyCard(survey: Survey, isResponded: Boolean, onTap: () -> Unit) {
    val questionCount = survey.normalizedQuestions().size

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface)
            .then(if (!isResponded && questionCount > 0) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(text = survey.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (isResponded) {
                Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(Color(0xFF4CAF50).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(text = "Cevaplandı", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
                }
            } else if (questionCount == 0) {
                Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(text = "Soru yok", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        survey.description?.takeIf { it.isNotEmpty() }?.let {
            Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 2)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "$questionCount soru", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Text(text = "Son: ${survey.endDate.take(10)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SurveyFormDialog(survey: Survey, onDismiss: () -> Unit, onSubmitted: () -> Unit) {
    val formQuestions = remember(survey) { survey.normalizedQuestions() }
    val answers = remember { mutableStateMapOf<String, String>() }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val allAnswered = formQuestions.isNotEmpty() &&
        formQuestions.all { q -> !answers[q.id].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(survey.title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                survey.description?.let {
                    Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }

                if (formQuestions.isEmpty()) {
                    Text(
                        text = "Bu anket için henüz soru tanımlanmamış. Lütfen salon yönetimiyle iletişime geçin.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                formQuestions.forEachIndexed { index, q ->
                    SurveyQuestionInput(
                        index = index,
                        question = q,
                        selectedAnswer = answers[q.id],
                        onAnswer = { answers[q.id] = it }
                    )
                }

                errorMessage?.let {
                    Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        errorMessage = null
                        try {
                            val userId = AuthService.getCurrentUserId() ?: return@launch
                            val jsonAnswers = buildJsonObject {
                                answers.forEach { (k, v) -> put(k, v) }
                                survey.category?.let { put("category", it) }
                            }
                            SurveyService.submitResponse(survey.id, userId, jsonAnswers)
                            onSubmitted()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Cevap gönderilemedi"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && allAnswered
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    Text("Gönder", color = if (allAnswered) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
private fun SurveyQuestionInput(
    index: Int,
    question: NormalizedSurveyQuestion,
    selectedAnswer: String?,
    onAnswer: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${index + 1}. ${question.text}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        when (question.type) {
            "rating" -> {
                val numbers = (question.ratingMin..question.ratingMax).toList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    numbers.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { num ->
                                val sel = selectedAnswer == "$num"
                                OutlinedButton(
                                    onClick = { onAnswer("$num") },
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (sel) Primary else Color.Transparent
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        width = if (sel) 0.dp else 1.dp
                                    )
                                ) {
                                    Text(
                                        text = "$num",
                                        fontSize = 14.sp,
                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "multiple_choice", "vote", "poll", "single_choice" -> {
                question.options.forEach { opt ->
                    val sel = selectedAnswer == opt
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background)
                            .clickable { onAnswer(opt) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = opt, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        if (sel) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            else -> {
                OutlinedTextField(
                    value = selectedAnswer ?: "",
                    onValueChange = onAnswer,
                    placeholder = { Text("Cevabınızı yazın...") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }
    }
}
