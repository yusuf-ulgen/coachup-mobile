package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.app.coachup.app.models.MembershipPlan
import com.app.coachup.app.models.UserMembership
import com.app.coachup.app.services.MembershipRequestService
import com.app.coachup.app.services.MembershipService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MembershipScreen(
    navController: NavController
) {
    val currentProfile by UserService.currentProfile.collectAsState()
    val isLoadingProfile by UserService.isLoading.collectAsState()
    val isIndividual = UserService.isIndividualUser(currentProfile)

    val scope = rememberCoroutineScope()
    val gymId = currentProfile?.gymId
    val gymName = currentProfile?.gymName

    var isLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var currentMembership by remember { mutableStateOf<UserMembership?>(null) }
    var plans by remember { mutableStateOf<List<MembershipPlan>>(emptyList()) }
    var showRenewalDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var planToRequest by remember { mutableStateOf<MembershipPlan?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentProfile?.id, gymId) {
        val userId = currentProfile?.id ?: run {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            currentMembership = MembershipService.fetchCurrentMembership(userId, gymId)
            plans = MembershipService.fetchPlans(gymId ?: com.app.coachup.app.config.GymConfig.GYM_ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Üyelik bilgileri yüklenemedi"
        } finally {
            isLoading = false
        }
    }

    fun submitPlanRequest(plan: MembershipPlan) {
        val userId = currentProfile?.id
        if (userId == null) {
            errorMessage = "Oturum bulunamadı"
            return
        }
        isSubmitting = true
        scope.launch {
            try {
                MembershipRequestService.createRequest(userId = userId, planId = plan.id)
                isSubmitting = false
                showPlanDialog = false
                showRenewalDialog = false
                showSuccessDialog = true
                planToRequest = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isSubmitting = false
                errorMessage = e.message ?: "Talep gönderilemedi"
            }
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

    if (showRenewalDialog && !isIndividual) {
        AlertDialog(
            onDismissRequest = { showRenewalDialog = false },
            title = { Text("Üyelik Yenileme") },
            text = { Text("Üyeliğinizi yenilemek için bir plan seçin. Talebiniz salona iletilecektir.") },
            confirmButton = {
                TextButton(onClick = {
                    showRenewalDialog = false
                    currentMembership?.planId?.let { planId ->
                        plans.firstOrNull { it.id == planId }?.let { plan ->
                            planToRequest = plan
                            showPlanDialog = true
                        }
                    } ?: run {
                        if (plans.isNotEmpty()) {
                            planToRequest = plans.first()
                            showPlanDialog = true
                        }
                    }
                }) {
                    Text("Plan Seç", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenewalDialog = false }) { Text("İptal") }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Talep İletildi") },
            text = {
                Text(
                    "Üyelik talebiniz ${gymName ?: "salona"} iletildi. " +
                        "Salon sizinle iletişime geçerek işlemi tamamlayacaktır."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    if (showPlanDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) {
                    showPlanDialog = false
                    planToRequest = null
                }
            },
            title = { Text("Plan Seçimi") },
            text = {
                planToRequest?.let { plan ->
                    Text(
                        "'${plan.name}' planı için talep göndermek istiyor musunuz? " +
                            "Ödeme ve onay işlemleri salon tarafından yürütülür."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { planToRequest?.let { submitPlanRequest(it) } },
                    enabled = !isSubmitting && planToRequest != null
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    } else {
                        Text("Salona Gönder", color = Primary)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPlanDialog = false; planToRequest = null },
                    enabled = !isSubmitting
                ) {
                    Text("İptal")
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
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = if (isIndividual) "Paketim" else "Üyeliğim",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (currentProfile == null && isLoadingProfile) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (isIndividual) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    IndividualMembershipCard()
                }
                
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Salon Planları",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = gymName ?: com.app.coachup.app.config.GymConfig.GYM_NAME,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (plans.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Görüntülenecek plan bulunamadı",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(plans) { plan ->
                    MembershipPlanCard(
                        plan = plan,
                        isCurrent = false,
                        onSelect = {
                            planToRequest = plan
                            showPlanDialog = true
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentMembership != null) {
                    item {
                        CurrentMembershipCard(
                            membership = currentMembership!!,
                            onRenew = { showRenewalDialog = true }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aktif üyeliğiniz bulunmuyor",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Planlar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!gymName.isNullOrBlank()) {
                            Text(
                                text = gymName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (plans.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Görüntülenecek plan bulunamadı",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(plans) { plan ->
                    MembershipPlanCard(
                        plan = plan,
                        isCurrent = currentMembership?.planId == plan.id,
                        onSelect = {
                            planToRequest = plan
                            showPlanDialog = true
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun IndividualMembershipCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Purple100)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(text = "Mevcut Paket", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                Text(text = "Bireysel", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Süresiz", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

        Text(
            text = "Kişisel antrenman, rekor takibi ve ilerleme özelliklerine sınırsız erişim.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
            lineHeight = 20.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(text = "Aktif paket", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun CurrentMembershipCard(
    membership: UserMembership,
    onRenew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Purple100)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(text = "Mevcut Üyelik", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                Text(text = membership.plan?.name ?: "Premium", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (membership.isActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (membership.isActive) "Aktif" else "Pasif",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (membership.isActive) Color(0xFF4CAF50) else Color.Red
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Bitiş Tarihi", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Text(text = membership.endDate?.take(10) ?: "-", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Başlangıç", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Text(text = membership.startDate?.take(10) ?: "-", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }

        Button(
            onClick = onRenew,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(Radius.pill)
        ) {
            Text(text = "Plan Yenile", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Purple100)
        }
    }
}

@Composable
private fun MembershipPlanCard(
    plan: MembershipPlan,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) Primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (plan.isPopular) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 0.dp))
                        .background(Primary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "En Popüler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(text = plan.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (isCurrent) Text(text = "Mevcut Plan", fontSize = 12.sp, color = Primary)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "₺${plan.price.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "/ay", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                        Text(text = feature, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (!isCurrent) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Text(text = "Plan Seç", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }
    }
}
