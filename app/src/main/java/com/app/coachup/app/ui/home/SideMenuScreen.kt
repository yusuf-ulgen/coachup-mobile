package com.app.coachup.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.UserProfile
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.Primary

// ─────────────────────────────────────────────────────────────────────────────
// Menu item model
// ─────────────────────────────────────────────────────────────────────────────

private data class MenuItemData(
    val title: String,
    val icon: ImageVector,
    val route: String
)

private val menuItems = listOf(
    MenuItemData("Aktivite Geçmişi", Icons.Filled.BarChart,       Routes.PERSONAL_RECORDS),
    MenuItemData("Randevular",     Icons.Filled.CalendarToday,  Routes.APPOINTMENTS),
    MenuItemData("Takvim",         Icons.Filled.CalendarMonth,  Routes.CALENDAR),
    MenuItemData("Grup Dersleri",  Icons.Filled.Group,          Routes.GROUP_CLASSES),
    MenuItemData("Beslenme",       Icons.Filled.Book,           Routes.NUTRITION),
    MenuItemData("İlerleme",       Icons.Filled.BarChart,       Routes.PROGRESS),
    MenuItemData("Hedefler",       Icons.Filled.TrackChanges,   Routes.GOALS),
    MenuItemData("Üyelik",         Icons.Filled.CreditCard,     Routes.MEMBERSHIP),
    MenuItemData("Ödemeler",       Icons.Filled.MonetizationOn, Routes.PAYMENTS),
    MenuItemData("Anketler",       Icons.Filled.Assignment,     Routes.SURVEYS),
    MenuItemData("Rezervasyon",    Icons.Filled.Place,          Routes.RESERVATIONS),
    MenuItemData("Koçlar",         Icons.Filled.People,         Routes.COACHES),
    MenuItemData("Ayarlar",        Icons.Filled.Settings,       Routes.SETTINGS),
)

private val gymOnlyRoutes = setOf(
    Routes.GROUP_CLASSES,
    Routes.RESERVATIONS,
    Routes.PAYMENTS,
)

private fun visibleMenuItems(isIndividual: Boolean): List<MenuItemData> =
    if (isIndividual) menuItems.filter { it.route !in gymOnlyRoutes } else menuItems

// ─────────────────────────────────────────────────────────────────────────────
// SideMenuContent – the drawer sheet content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Side menu drawer content – Android equivalent of iOS SideMenuView.
 *
 * This composable is rendered inside a [ModalNavigationDrawer] in [HomeScreen].
 * It does NOT use a [ModalDrawerSheet] wrapper so we can control the exact
 * 75 % width and background colour from iOS.
 *
 * @param currentProfile The authenticated user's profile (nullable while loading).
 * @param onClose        Called to close the drawer.
 * @param onNavigate     Called with the destination route when a menu item is tapped.
 * @param onLogout       Called when the user confirms logout.
 */
@Composable
fun SideMenuContent(
    currentProfile: UserProfile?,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val authUser by AuthService.currentUser.collectAsState(initial = null)
    val isIndividual = remember(currentProfile, authUser) {
        when {
            currentProfile?.isIndividual == true -> true
            currentProfile != null -> UserService.isIndividualUser(currentProfile)
            else -> AuthService.isIndividualAuthUser(authUser)
        }
    }
    val items = remember(isIndividual) { visibleMenuItems(isIndividual) }

    // The drawer container – 75 % width, full height, white100 background
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.75f)
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // ── Close button ────────────────────────────────────────────────────
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(top = 16.dp)
                .size(40.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Kapat",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── User info card ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(100.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onNavigate(Routes.PROFILE)
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                val displayName = buildString {
                    append(currentProfile?.name ?: "Kullanıcı")
                    if (!currentProfile?.surname.isNullOrBlank()) {
                        append(" ")
                        append(currentProfile!!.surname)
                    }
                }
                Text(
                    text = displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isIndividual -> "Bireysel"
                        else -> currentProfile?.gymName?.takeIf { it.isNotBlank() } ?: "Bağlı Salon Yok"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── "Menü" heading ─────────────────────────────────────────────────
        Text(
            text = "Menü",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Menu items (scrollable) ────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { item ->
                MenuItemRow(
                    item = item,
                    onClick = {
                        onNavigate(item.route)
                    }
                )
            }

            if (currentProfile?.role == "admin" || currentProfile?.role == "gym_manager") {
                MenuItemRow(
                    item = MenuItemData("Admin Panel", Icons.Filled.Build, Routes.ADMIN_DASHBOARD),
                    onClick = { onNavigate(Routes.ADMIN_DASHBOARD) }
                )
            }
        }

        // ── Logout button ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showLogoutDialog = true }
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Çıkış Yap",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Çıkış Yap",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── Logout confirmation dialog ─────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Çıkış Yap") },
            text = {
                Text("Hesabınızdan çıkış yapmak istediğinize emin misiniz?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Çıkış Yap", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("İptal", color = Primary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single menu item row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MenuItemRow(
    item: MenuItemData,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "›",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Primary
        )
    }
}
