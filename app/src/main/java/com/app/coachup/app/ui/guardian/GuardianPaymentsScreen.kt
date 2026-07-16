package com.app.coachup.app.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.FinancialTransaction
import com.app.coachup.app.services.GuardianChildInfo
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.theme.Primary
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// GuardianPaymentsScreen — mirrors iOS GuardianPaymentsView.swift
// ---------------------------------------------------------------------------

data class ChildTransaction(
    val childName: String,
    val transaction: FinancialTransaction
)

@Composable
fun GuardianPaymentsScreen() {
    val children by GuardianService.children.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var transactions by remember { mutableStateOf<List<ChildTransaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(children) {
        isLoading = true
        val allTx = mutableListOf<ChildTransaction>()
        try {
            for (relation in children) {
                val member = relation.member ?: continue
                try {
                    val result = SupabaseConfig.client.from("financial_transactions")
                        .select {
                            filter { eq("user_id", member.id) }
                            order("created_at", Order.DESCENDING)
                            limit(20)
                        }
                        .decodeList<FinancialTransaction>()
                    result.forEach { allTx.add(ChildTransaction(childName = member.fullName, transaction = it)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("GuardianPayments", "Error: ${e.message}")
                }
            }
            transactions = allTx.sortedByDescending { it.transaction.createdAt }
        } catch (e: CancellationException) {
            throw e
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8)),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Text(
                text = "Ödemeler",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        } else if (transactions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.CreditCard, null, tint = Color(0xFF9E9E9E).copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
                    Text("Ödeme kaydı bulunamadı", fontSize = 13.sp, color = Color(0xFF9E9E9E))
                }
            }
        } else {
            // Summary card
            item {
                val total = transactions.sumOf { it.transaction.amount }
                val paid = transactions.filter { it.transaction.status == "completed" }.sumOf { it.transaction.amount }
                val pending = transactions.filter { it.transaction.status == "pending" }.sumOf { it.transaction.amount }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(modifier = Modifier.padding(vertical = 14.dp)) {
                        PaymentSummaryCell(label = "Toplam", value = "%.0f TL".format(total), color = Color(0xFF1A1A1A))
                        PaymentSummaryCell(label = "Ödenen", value = "%.0f TL".format(paid), color = Color(0xFF4CAF50))
                        PaymentSummaryCell(label = "Bekleyen", value = "%.0f TL".format(pending), color = Color(0xFFFF9800))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Transaction rows
            items(transactions) { item ->
                TransactionRow(item = item)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RowScope.PaymentSummaryCell(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color(0xFF9E9E9E))
    }
}

@Composable
private fun TransactionRow(item: ChildTransaction) {
    val statusColor = when (item.transaction.status) {
        "completed" -> Color(0xFF4CAF50)
        "pending" -> Color(0xFFFF9800)
        "failed" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    val statusIcon = when (item.transaction.status) {
        "completed" -> Icons.Filled.CheckCircle
        "pending" -> Icons.Filled.Schedule
        "failed" -> Icons.Filled.Cancel
        else -> Icons.Filled.Help
    }
    val statusLabel = when (item.transaction.status) {
        "completed" -> "Ödendi"
        "pending" -> "Bekliyor"
        "failed" -> "Başarısız"
        "refunded" -> "İade"
        else -> item.transaction.status
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.transaction.description ?: item.transaction.type,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1
                )
                Text(item.childName, fontSize = 11.sp, color = Color(0xFF9E9E9E))
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.0f TL".format(item.transaction.amount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = statusColor)
            }
        }
    }
}
