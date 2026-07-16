package com.app.coachup.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.AppNotification
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.NotificationService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val notifications by NotificationService.notifications.collectAsState()
    val isLoading by NotificationService.isLoading.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var userId by remember { mutableStateOf<String?>(null) }

    val unreadCount = notifications.count { !it.isRead }

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId() ?: return@LaunchedEffect
        userId = uid
        try {
            NotificationService.fetchNotifications(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Bildirimler yüklenemedi"
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Bildirimleri Sil") },
            text = { Text("Tüm bildirimleri silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    val uid = userId
                    showDeleteAllDialog = false
                    if (uid != null) {
                        scope.launch {
                            try {
                                NotificationService.clearAllNotifications(uid)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Bildirimler silinemedi"
                            }
                        }
                    }
                }) {
                    Text("Sil", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("İptal", color = Primary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bildirimler",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount okunmamış",
                        fontSize = 12.sp,
                        color = Primary
                    )
                }
            }
            if (notifications.isNotEmpty()) {
                Row {
                    if (unreadCount > 0) {
                        IconButton(
                            onClick = {
                                val uid = userId ?: return@IconButton
                                scope.launch {
                                    try {
                                        NotificationService.markAllAsRead(uid)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "İşlem başarısız"
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Tümünü Okundu İşaretle",
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Tümünü Sil",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (isLoading && notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Bildirim yok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Yeni bildirimler burada görünecek.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = notifications.sortedByDescending { it.createdAt },
                    key = { it.id }
                ) { notification ->
                    NotificationItem(
                        notification = notification,
                        onRead = {
                            scope.launch {
                                try {
                                    NotificationService.markAsRead(notification.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "İşlem başarısız"
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                try {
                                    NotificationService.deleteNotification(notification.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Silme başarısız"
                                }
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, iconColor) = notificationIconAndColor(notification.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (!notification.isRead) Primary.copy(alpha = 0.04f) else Color.Transparent
            )
            .clickable { if (!notification.isRead) onRead() }
            .padding(horizontal = Spacing.xl, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.title,
                    fontSize = 14.sp,
                    fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Primary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatNotificationTime(notification.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun notificationIconAndColor(type: String): Pair<ImageVector, Color> {
    return when (type) {
        "workout" -> Pair(Icons.Default.FitnessCenter, Color(0xFFFF6047))
        "membership" -> Pair(Icons.Default.CardMembership, Color(0xFF9C27B0))
        "system" -> Pair(Icons.Default.Settings, Color(0xFF607D8B))
        "coach" -> Pair(Icons.Default.Person, Color(0xFF2196F3))
        "payment" -> Pair(Icons.Default.Payment, Color(0xFF4CAF50))
        "reminder" -> Pair(Icons.Default.Schedule, Color(0xFFFF9800))
        else -> Pair(Icons.Default.Notifications, Color(0xFFFF6047))
    }
}

private fun formatNotificationTime(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    return try {
        createdAt.take(16).replace("T", " ")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        createdAt
    }
}
