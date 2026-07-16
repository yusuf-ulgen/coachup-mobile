package com.app.coachup.app.ui.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.app.coachup.app.models.CommunityGroup
import com.app.coachup.app.models.CommunityPostUi
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.CommunityService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class CommunityScopeTab(val label: String, val scope: String) {
    SALON("Salon", "gym"),
    GENEL("Genel", "general")
}

@Composable
fun CommunityScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AuthService.currentUser.collectAsState(initial = null)
    val currentProfile by UserService.currentProfile.collectAsState(initial = null)
    val gymId = remember(currentProfile) {
        UserService.resolveActiveGymIdForContent(currentProfile)
    }

    var selectedTab by remember { mutableStateOf(CommunityScopeTab.SALON) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<CommunityPostUi>>(emptyList()) }
    var groups by remember { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var canAccess by remember { mutableStateOf(true) }
    var accessMessage by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun loadFeed() {
        val userId = currentUser?.id ?: return
        isLoading = true
        try {
            when (selectedTab) {
                CommunityScopeTab.SALON -> {
                    val allowed = CommunityService.canAccessGymFeed(userId, gymId)
                    canAccess = allowed
                    accessMessage = when {
                        gymId.isNullOrBlank() -> "Aktif salon üyeliğin yok. Salon topluluğuna sadece üyeler erişebilir."
                        !allowed -> "Salon topluluğu kapalı veya üyeliğin aktif değil."
                        else -> null
                    }
                    if (allowed && gymId != null) {
                        groups = CommunityService.fetchGroups("gym", gymId)
                        posts = CommunityService.fetchFeed(
                            userId = userId,
                            scope = "gym",
                            gymId = gymId,
                            groupId = selectedGroupId
                        )
                    } else {
                        groups = emptyList()
                        posts = emptyList()
                    }
                }
                CommunityScopeTab.GENEL -> {
                    val allowed = CommunityService.canAccessGeneralFeed()
                    canAccess = allowed
                    accessMessage = if (!allowed) "Genel topluluk şu an kapalı." else null
                    if (allowed) {
                        groups = CommunityService.fetchGroups("general")
                        posts = CommunityService.fetchFeed(
                            userId = userId,
                            scope = "general",
                            groupId = selectedGroupId
                        )
                    } else {
                        groups = emptyList()
                        posts = emptyList()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            canAccess = false
            accessMessage = "Topluluk verisi yüklenemedi. Migration uygulandı mı?"
            posts = emptyList()
            groups = emptyList()
        } finally {
            isLoading = false
        }
    }

    // Reset subgroup filter when switching Salon / Genel
    LaunchedEffect(selectedTab) {
        selectedGroupId = null
    }

    LaunchedEffect(selectedTab, selectedGroupId, currentUser?.id, gymId) {
        loadFeed()
    }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it)
            snackbar = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed header: Salon | Genel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = 8.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CommunityScopeTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (selected) Primary else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Text(
                text = if (selectedTab == CommunityScopeTab.SALON) "Salon Topluluğu" else "Genel Topluluk",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp)
            )

            if (groups.isNotEmpty() && canAccess) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GroupChip(
                            label = "Tümü",
                            selected = selectedGroupId == null,
                            onClick = { selectedGroupId = null }
                        )
                    }
                    items(groups, key = { it.id }) { group ->
                        GroupChip(
                            label = group.name,
                            selected = selectedGroupId == group.id,
                            onClick = { selectedGroupId = group.id }
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                !canAccess -> LockedState(message = accessMessage ?: "Erişim yok")
                posts.isEmpty() -> EmptyFeedState(
                    onCreate = { showComposer = true },
                    canCreate = true
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(posts, key = { it.post.id }) { item ->
                        PostCard(
                            item = item,
                            isMine = item.post.authorId == currentUser?.id,
                            onLike = {
                                val uid = currentUser?.id ?: return@PostCard
                                scope.launch {
                                    try {
                                        CommunityService.toggleLike(item.post.id, uid)
                                        loadFeed()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        snackbar = "Beğeni kaydedilemedi"
                                    }
                                }
                            },
                            onDelete = {
                                val uid = currentUser?.id ?: return@PostCard
                                scope.launch {
                                    try {
                                        CommunityService.deletePost(item.post.id, uid)
                                        loadFeed()
                                        snackbar = "Gönderi silindi"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        snackbar = "Silinemedi"
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }

        if (canAccess && !isLoading) {
            FloatingActionButton(
                onClick = { showComposer = true },
                containerColor = Primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.xl, bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Paylaş")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }

    if (showComposer) {
        ComposePostDialog(
            onDismiss = { showComposer = false },
            onSubmit = { text, imageUri ->
                val userId = currentUser?.id ?: return@ComposePostDialog
                scope.launch {
                    try {
                        val imageUrl = imageUri?.let {
                            CommunityService.uploadImageFromUri(context, userId, it)
                        }
                        CommunityService.createPost(
                            authorId = userId,
                            scope = selectedTab.scope,
                            content = text,
                            imageUrl = imageUrl,
                            gymId = if (selectedTab == CommunityScopeTab.SALON) gymId else null,
                            groupId = selectedGroupId
                        )
                        showComposer = false
                        loadFeed()
                        snackbar = "Paylaşıldı"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar = e.message ?: "Paylaşım başarısız"
                    }
                }
            }
        )
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        )
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LockedState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun EmptyFeedState(onCreate: () -> Unit, canCreate: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💬", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Henüz paylaşım yok",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "İlk yazıyı veya görseli sen paylaş.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        if (canCreate) {
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text("Paylaşım Yap")
            }
        }
    }
}

@Composable
private fun PostCard(
    item: CommunityPostUi,
    isMine: Boolean,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    val post = item.post
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(Radius.card))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.authorName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.authorName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    formatPostTime(post.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }
            if (isMine) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                }
            }
        }

        post.content?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 20.sp
            )
        }

        post.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { onLike() }
        ) {
            Icon(
                imageVector = if (item.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Beğen",
                tint = if (item.likedByMe) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (item.likeCount > 0) "${item.likeCount}" else "Beğen",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun ComposePostDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Uri?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Yeni Paylaşım", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ne düşünüyorsun?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    maxLines = 6
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { imagePicker.launch("image/*") }, enabled = !isSending) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (imageUri != null) "Görsel seçildi" else "Görsel ekle")
                    }
                }
                imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank() && imageUri == null) return@TextButton
                    isSending = true
                    onSubmit(text, imageUri)
                },
                enabled = !isSending && (text.isNotBlank() || imageUri != null)
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                else Text("Paylaş", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("İptal") }
        }
    )
}

private fun formatPostTime(iso: String): String {
    return try {
        val instant = Instant.parse(iso.replace(" ", "T").let {
            if (it.endsWith("Z") || it.contains("+")) it else "${it}Z"
        })
        DateTimeFormatter.ofPattern("d MMM HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        iso.take(16)
    }
}
