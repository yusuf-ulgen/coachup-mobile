package com.app.coachup.app.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.coachup.app.services.GuardianChildInfo
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.services.MemberGuardian
import com.app.coachup.app.theme.Primary

// ---------------------------------------------------------------------------
// GuardianChildrenScreen — mirrors iOS GuardianChildrenView.swift
// ---------------------------------------------------------------------------

@Composable
fun GuardianChildrenScreen(navController: NavHostController) {
    val children by GuardianService.children.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Çocuklarım",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (children.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E).copy(alpha = 0.4f),
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        "Bağlı çocuk bulunamadı",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        } else {
            items(children) { relation ->
                relation.member?.let { member ->
                    ChildListCard(
                        member = member,
                        relation = relation,
                        onClick = {
                            navController.navigate("guardian_child_detail/${member.id}")
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ChildListCard — mirrors iOS ChildListCard
// ---------------------------------------------------------------------------

@Composable
fun ChildListCard(
    member: GuardianChildInfo,
    relation: MemberGuardian,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.initials,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = member.fullName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (relation.canViewHealth) {
                        PermissionBadge(icon = Icons.Filled.Favorite, label = "Sağlık", color = Color(0xFFF44336))
                    }
                    if (relation.canViewAttendance) {
                        PermissionBadge(icon = Icons.Filled.Login, label = "Giriş", color = Color(0xFF2196F3))
                    }
                    if (relation.canViewProgress) {
                        PermissionBadge(icon = Icons.Filled.TrendingUp, label = "İlerleme", color = Color(0xFF4CAF50))
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PermissionBadge — mirrors iOS PermissionBadge
// ---------------------------------------------------------------------------

@Composable
fun PermissionBadge(icon: ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(8.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
