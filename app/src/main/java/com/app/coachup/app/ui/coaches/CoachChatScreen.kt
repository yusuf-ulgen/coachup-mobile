package com.app.coachup.app.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.app.coachup.app.models.CoachMessage
import com.app.coachup.app.models.LocalCoach
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.CoachService
import com.app.coachup.app.services.RealtimeService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CoachChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<CoachMessage>>(emptyList())
    val messages: StateFlow<List<CoachMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeSubscriptionChannelId: String? = null

    fun loadMessages(userId: String, coachId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fetched = CoachService.fetchMessages(userId, coachId)
                _messages.value = fetched

                // Subscribe to real-time messages
                activeSubscriptionChannelId = "coach-messages:$userId:$coachId"
                RealtimeService.subscribeToCoachMessages(userId, coachId) { incoming ->
                    // Avoid duplicate inserts
                    if (_messages.value.none { it.id == incoming.id }) {
                        _messages.value = _messages.value + incoming
                        markAsRead(userId, coachId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Mesajlar yüklenemedi"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsRead(userId: String, coachId: String) {
        viewModelScope.launch {
            try { CoachService.markAsRead(userId, coachId) } catch (_: Exception) {}
        }
    }

    fun sendMessage(userId: String, coachId: String, text: String) {
        if (text.isBlank()) return
        _isSending.value = true

        // Optimistic local message
        val optimistic = CoachMessage(
            id = "temp_${System.currentTimeMillis()}",
            userId = userId,
            coachId = coachId,
            message = text,
            isFromCoach = false,
            isRead = false,
            sentAt = Instant.now().toString()
        )
        _messages.value = _messages.value + optimistic

        viewModelScope.launch {
            try {
                val sent = CoachService.sendMessage(userId, coachId, text)
                _messages.value = _messages.value.map { message ->
                    if (message.id == optimistic.id) sent else message
                }
            } catch (e: CancellationException) {
                _messages.value = _messages.value.filter { it.id != optimistic.id }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoachChatVM", "sendMessage error", e)
                _messages.value = _messages.value.filter { it.id != optimistic.id }
                _error.value = "Mesaj gönderilemedi: ${e.localizedMessage ?: "Bilinmeyen hata"}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        activeSubscriptionChannelId?.let { channelId ->
            RealtimeService.unsubscribe(channelId)
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CoachChatScreen(
    coach: LocalCoach,
    onNavigateBack: () -> Unit,
    vm: CoachChatViewModel = viewModel()
) {
    val userId by AuthService.currentUser.collectAsState(initial = null)

    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isSending by vm.isSending.collectAsState()
    val error by vm.error.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(userId) {
        val uid = userId?.id ?: return@LaunchedEffect
        vm.loadMessages(uid, coach.id)
        vm.markAsRead(uid, coach.id)
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Error dialog
    if (error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Hata") },
            text = { Text(error ?: "") },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text("Tamam", color = Primary) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Chat header
        ChatHeader(coach = coach, onBack = onNavigateBack)

        // Messages
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Primary, strokeWidth = 2.dp) }

                messages.isEmpty() -> EmptyChatState()

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = Spacing.xl,
                        vertical = Spacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isFromUser = !message.isFromCoach
                        val parsedDate = parseMessageDate(message.sentAt)
                        MessageBubble(
                            message = message.message,
                            isFromUser = isFromUser,
                            timestamp = parsedDate,
                            isRead = message.isRead
                        )
                    }
                }
            }
        }

        // Input bar
        MessageInputBar(
            text = messageText,
            onTextChange = { messageText = it },
            isSending = isSending,
            onSend = {
                val uid = userId?.id ?: return@MessageInputBar
                val text = messageText.trim()
                messageText = ""
                vm.sendMessage(uid, coach.id, text)
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Chat header
// ---------------------------------------------------------------------------

@Composable
private fun ChatHeader(coach: LocalCoach, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.xl, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Geri",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(24.dp)
                .clickable { onBack() }
        )

        // Coach avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Purple100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (coach.profileImageUrl != null) {
                AsyncImage(
                    model = coach.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = "${coach.name.firstOrNull() ?: ""}${coach.surname.firstOrNull() ?: ""}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Coach info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = coach.fullName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!coach.specialty.isNullOrEmpty()) {
                Text(
                    text = coach.specialty,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message bubble
// ---------------------------------------------------------------------------

@Composable
private fun MessageBubble(
    message: String,
    isFromUser: Boolean,
    timestamp: Date?,
    isRead: Boolean
) {
    val timeStr = timestamp?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
    } ?: ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Bubble
            Box(
                modifier = Modifier
                    .background(
                        if (isFromUser) Primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(
                            topStart = Radius.bubble,
                            topEnd = Radius.bubble,
                            bottomStart = if (isFromUser) Radius.bubble else 4.dp,
                            bottomEnd = if (isFromUser) 4.dp else Radius.bubble
                        )
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = if (isFromUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }

            // Timestamp + read receipt
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeStr,
                    style = AppTimestampStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFromUser) {
                    Icon(
                        imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = null,
                        tint = if (isRead) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message input bar
// ---------------------------------------------------------------------------

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .shadow(elevation = 4.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        // Text input
        Row(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.inputField))
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 20.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("Mesaj yazın...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            )
        }

        // Send button
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (text.isNotBlank() && !isSending) Primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape
                )
                .clickable(enabled = text.isNotBlank() && !isSending) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Gönder",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Message,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Henüz mesaj yok",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "İlk mesajı göndererek konuşmaya başlayın",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Date parsing helper
// ---------------------------------------------------------------------------

private fun parseMessageDate(dateStr: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    )
    formats.forEach { fmt ->
        try {
            return SimpleDateFormat(fmt, Locale.getDefault()).parse(dateStr.take(26))
        } catch (_: Exception) {}
    }
    return null
}
