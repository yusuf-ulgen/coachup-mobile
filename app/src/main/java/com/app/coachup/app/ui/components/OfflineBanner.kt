package com.app.coachup.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.theme.CoachUpTheme
import com.app.coachup.app.theme.Radius
import com.app.coachup.app.theme.Spacing

/**
 * Network status banner – mirrors iOS OfflineBanner.
 *
 * Displayed at the top of the screen when the device has no internet
 * connectivity. The banner uses an animated enter/exit transition so it
 * slides in smoothly rather than popping into view.
 *
 * @param isVisible  Controls whether the banner is shown.
 * @param modifier   Optional layout modifier applied to the outer [AnimatedVisibility].
 */
@Composable
fun OfflineBanner(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xFFFF9800), // orange – matches iOS Color.orange
            shape = RoundedCornerShape(Radius.banner),
            shadowElevation = 2.dp,
            modifier = Modifier.padding(top = 50.dp)  // mirrors iOS .padding(.top, 50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = "Çevrimdışı",
                    tint = Color.White
                )

                Text(
                    text = "Offline Mode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFFAF9F8)
@Composable
private fun OfflineBannerPreview() {
    CoachUpTheme {
        OfflineBanner(isVisible = true)
    }
}
