package com.app.coachup.app.ui.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.FinancialTransaction
import com.app.coachup.app.models.PaymentInstallment
import com.app.coachup.app.models.PaymentPlan
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.PaymentService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException

@Composable
fun PaymentsScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }
    var transactions by remember { mutableStateOf<List<FinancialTransaction>>(emptyList()) }
    var activePlan by remember { mutableStateOf<PaymentPlan?>(null) }
    var installments by remember { mutableStateOf<List<PaymentInstallment>>(emptyList()) }

    LaunchedEffect(Unit) {
        val userId = AuthService.getCurrentUserId() ?: run { isLoading = false; return@LaunchedEffect }
        try {
            transactions = PaymentService.fetchTransactions(userId)
            activePlan = PaymentService.fetchActivePaymentPlan(userId)
            activePlan?.let { plan ->
                installments = PaymentService.fetchInstallments(plan.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
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
            Text(text = "Ödemeler", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                activePlan?.let { plan ->
                    item { InstallmentSection(plan = plan, installments = installments) }
                }
                item { TransactionSection(transactions = transactions) }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun InstallmentSection(plan: PaymentPlan, installments: List<PaymentInstallment>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(text = "Taksit Planı", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0xFF6C5DD3)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Toplam: ${String.format("%.0f₺", plan.totalAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(text = "${installments.count { it.status == "paid" }}/${plan.installmentCount} ödendi", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            }
            installments.forEach { inst ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Taksit ${inst.installmentNumber}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                    Text(text = String.format("%.0f₺", inst.amount), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    when (inst.status) {
                        "paid" -> Text(text = "✓ Ödendi", fontSize = 12.sp, color = Color(0xFF4CAF50))
                        "overdue" -> Text(text = "Gecikmiş", fontSize = 12.sp, color = Color(0xFFFF5252))
                        else -> Text(text = inst.dueDate.take(10), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionSection(transactions: List<FinancialTransaction>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(text = "İşlem Geçmişi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text(text = "Henüz işlem yok", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
        } else {
            transactions.forEach { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = tx.description ?: txTypeLabel(tx.type), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = tx.createdAt.take(10), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Text(
                        text = String.format("%.0f₺", tx.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tx.type == "refund") Color(0xFF4CAF50) else Primary
                    )
                }
            }
        }
    }
}

private fun txTypeLabel(type: String): String = when (type) {
    "membership_payment" -> "Üyelik Ödemesi"
    "class_payment" -> "Ders Ödemesi"
    "pt_payment" -> "PT Ödemesi"
    "refund" -> "İade"
    else -> type.replaceFirstChar { it.uppercase() }
}
