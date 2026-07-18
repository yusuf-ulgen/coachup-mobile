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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.app.coachup.app.models.*
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

    var selectedTab by remember { mutableStateOf(CommunityScopeTab.GENEL) }
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
                            },
                            onVote = { pollId, optionId ->
                                val uid = currentUser?.id ?: return@PostCard
                                scope.launch {
                                    try {
                                        CommunityService.votePoll(pollId, optionId, uid)
                                        loadFeed()
                                    } catch (_: Exception) {
                                        snackbar = "Oy kullanılamadı"
                                    }
                                }
                            },
                            onCommentAction = {
                                scope.launch {
                                    loadFeed()
                                }
                            },
                            currentUserId = currentUser?.id
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
            onSubmit = { text, imageUri, pollQuestion, pollOptions ->
                val userId = currentUser?.id ?: return@ComposePostDialog
                scope.launch {
                    try {
                        val imageUrl = imageUri?.let {
                            CommunityService.uploadImageFromUri(context, userId, it)
                        }
                        val postId = CommunityService.createPost(
                            authorId = userId,
                            scope = selectedTab.scope,
                            content = text,
                            imageUrl = imageUrl,
                            gymId = if (selectedTab == CommunityScopeTab.SALON) gymId else null,
                            groupId = selectedGroupId
                        )
                        if (!pollQuestion.isNullOrBlank() && !pollOptions.isNullOrEmpty()) {
                            CommunityService.createPoll(postId, pollQuestion, pollOptions)
                        }
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
    onDelete: () -> Unit,
    onVote: (String, String) -> Unit,
    onCommentAction: () -> Unit,
    currentUserId: String?
) {
    val post = item.post
    var commentsExpanded by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<CommunityCommentUi>>(emptyList()) }
    var isCommentsLoading by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(commentsExpanded) {
        if (commentsExpanded) {
            isCommentsLoading = true
            comments = runCatching { CommunityService.fetchComments(post.id) }.getOrDefault(emptyList())
            isCommentsLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(Radius.card))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        // Poll UI
        item.poll?.let { pollUi ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Anket: ${pollUi.poll.question}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val hasVoted = pollUi.myVoteOptionId != null
                pollUi.options.forEach { opt ->
                    if (hasVoted) {
                        val isMyVote = opt.option.id == pollUi.myVoteOptionId
                        val bgColor = if (isMyVote) Primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val borderColor = if (isMyVote) Primary else Color.Transparent

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (opt.percentage > 0) opt.percentage / 100f else 0.01f)
                                    .background(bgColor)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opt.option.optionText,
                                        fontSize = 13.sp,
                                        fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isMyVote) Primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isMyVote) {
                                        Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = "%${opt.percentage}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMyVote) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onVote(pollUi.poll.id, opt.option.id) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(opt.option.optionText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (pollUi.totalVotes > 0) {
                    Text(
                        text = "Toplam ${pollUi.totalVotes} oy",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { commentsExpanded = !commentsExpanded }
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Yorumlar",
                    tint = if (commentsExpanded) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (item.commentCount > 0) "${item.commentCount} Yorum" else "Yorum Yap",
                    fontSize = 13.sp,
                    color = if (commentsExpanded) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
        }

        if (commentsExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isCommentsLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                    }
                } else if (comments.isEmpty()) {
                    Text(
                        text = "Henüz yorum yok. İlk yorumu sen yap!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    comments.forEach { comm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = comm.authorName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(comm.authorName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(comm.comment.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                            }
                            if (comm.comment.authorId == currentUserId) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { CommunityService.deleteComment(comm.comment.id, currentUserId ?: "") }
                                            comments = runCatching { CommunityService.fetchComments(post.id) }.getOrDefault(emptyList())
                                            onCommentAction()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("Yorum yaz...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).heightIn(max = 50.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(
                        onClick = {
                            if (newCommentText.isBlank() || currentUserId == null) return@IconButton
                            scope.launch {
                                val result = runCatching { CommunityService.createComment(post.id, currentUserId, newCommentText) }
                                if (result.isSuccess) {
                                    newCommentText = ""
                                    comments = runCatching { CommunityService.fetchComments(post.id) }.getOrDefault(emptyList())
                                    onCommentAction()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Yorum gönderilemedi: ${result.exceptionOrNull()?.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = newCommentText.isNotBlank(),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (newCommentText.isNotBlank()) Primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposePostDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Uri?, String?, List<String>?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    
    // Poll state
    var showPollCreator by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("", "") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Yeni Paylaşım", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ne düşünüyorsun?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    maxLines = 6
                )

                // Triggers Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Image picker trigger
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !isSending && !showPollCreator) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Görsel Ekle",
                            tint = if (imageUri != null) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Poll creator trigger
                    IconButton(onClick = { showPollCreator = !showPollCreator }, enabled = !isSending && imageUri == null) {
                        Icon(
                            imageVector = Icons.Default.Poll,
                            contentDescription = "Anket Oluştur",
                            tint = if (showPollCreator) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Selected Image Preview
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

                // Poll Creator Section
                if (showPollCreator) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Anket Detayları", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                            
                            OutlinedTextField(
                                value = pollQuestion,
                                onValueChange = { pollQuestion = it },
                                placeholder = { Text("Bir soru sor...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                            )

                            pollOptions.forEachIndexed { idx, opt ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = opt,
                                        onValueChange = { pollOptions[idx] = it },
                                        placeholder = { Text("Seçenek ${idx + 1}") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                                    )
                                    if (pollOptions.size > 2) {
                                        IconButton(onClick = { pollOptions.removeAt(idx) }) {
                                            Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (pollOptions.size < 4) {
                                TextButton(
                                    onClick = { pollOptions.add("") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Seçenek Ekle", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = when {
                showPollCreator -> pollQuestion.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2
                else -> text.isNotBlank() || imageUri != null
            }
            TextButton(
                onClick = {
                    if (!canSubmit) return@TextButton
                    isSending = true
                    val q = if (showPollCreator) pollQuestion else null
                    val opts = if (showPollCreator) pollOptions.toList() else null
                    onSubmit(text, imageUri, q, opts)
                },
                enabled = !isSending && canSubmit
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
