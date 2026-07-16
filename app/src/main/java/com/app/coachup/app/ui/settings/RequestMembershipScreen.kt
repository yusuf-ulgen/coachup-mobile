package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.app.coachup.app.navigation.NavigationStateHolder
import com.app.coachup.app.services.MembershipRequestService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RequestMembershipScreen(
    navController: NavController
) {
    var notes by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val planId = remember { NavigationStateHolder.pendingMembershipPlanId }
    val currentProfile by UserService.currentProfile.collectAsState()

    LaunchedEffect(currentProfile?.id) {
        val uid = currentProfile?.id ?: return@LaunchedEffect
        try {
            MembershipRequestService.fetchPendingRequest(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; navController.popBackStack() },
            title = { Text("Talep İletildi") },
            text = { Text("Üyelik talebiniz salona iletildi. Salon sizinle iletişime geçecektir.") },
            confirmButton = {
                TextButton(onClick = { showSuccess = false; navController.popBackStack() }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 24.dp),
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
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "Plan Talebi",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(Radius.card)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Salon Onayı",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Talebiniz doğrudan salona iletilecektir. Ödeme ve üyelik işlemleri salon tarafından yürütülür.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Notlar (opsiyonel)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Salona iletmek istediğiniz not...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(Radius.item)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val uid = currentProfile?.id
                val selectedPlanId = planId
                when {
                    uid == null -> errorMessage = "Oturum bulunamadı"
                    selectedPlanId.isNullOrBlank() -> errorMessage = "Lütfen önce bir plan seçin"
                    else -> {
                        isLoading = true
                        scope.launch {
                            try {
                                MembershipRequestService.createRequest(
                                    userId = uid,
                                    planId = selectedPlanId,
                                    notes = notes.takeIf { it.isNotBlank() }
                                )
                                NavigationStateHolder.pendingMembershipPlanId = null
                                isLoading = false
                                showSuccess = true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = e.message ?: "Talep gönderilemedi"
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = 40.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(Radius.pill),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(text = "Salona Talep Gönder", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}
