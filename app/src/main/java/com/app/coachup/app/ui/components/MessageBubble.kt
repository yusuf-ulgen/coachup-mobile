package com.app.coachup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.coachup.app.theme.AppTimestampStyle
import com.app.coachup.app.theme.CoachUpTheme
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.Radius
import com.app.coachup.app.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat message bubble – mirrors iOS MessageBubble exactly.
 *
 * Layout behaviour:
 *  - User messages: right-aligned, primary background, white text.
 *  - Coach/bot messages: left-aligned, White100 background, dark text.
 *  - A minimum spacer of 60 dp pushes bubbles away from the opposite edge,
 *    preventing bubbles from stretching full-width (same as iOS minLength: 60).
 *
 * @param message     Plain-text content of the message.
 * @param isFromUser  True when the authenticated user authored the message.
 * @param timestamp   Date/time at which the message was sent.
 * @param modifier    Optional layout modifier.
 */
@Composable
fun MessageBubble(
    message: String,
    isFromUser: Boolean,
    timestamp: Date,
    modifier: Modifier = Modifier
) {
    val timeString = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp)
    }

    val bubbleColor: Color = if (isFromUser) Primary else MaterialTheme.colorScheme.surfaceVariant
    val onTextColor: Color = if (isFromUser) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        // Push bubble away from the opposite side (matches iOS Spacer minLength: 60)
        if (!isFromUser) {
            // No leading spacer for received messages — the spacer is on the trailing side.
        } else {
            Spacer(modifier = Modifier.widthIn(min = 60.dp).weight(1f, fill = false))
        }

        Column(
            horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start
        ) {
            // Bubble body
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = onTextColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.bubble))
                    .background(bubbleColor)
                    .padding(horizontal = Spacing.lg, vertical = 12.dp)
            )

            // Timestamp label
            Text(
                text = timeString,
                style = AppTimestampStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
        }

        if (isFromUser) {
            // No trailing spacer for sent messages.
        } else {
            Spacer(modifier = Modifier.widthIn(min = 60.dp).weight(1f, fill = false))
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun MessageBubblePreview() {
    CoachUpTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MessageBubble(
                message = "Merhaba, antrenman programı hakkında bilgi alabilir miyim?",
                isFromUser = true,
                timestamp = Date()
            )
            MessageBubble(
                message = "Tabii ki! Size özel bir program hazırlayabilirim.",
                isFromUser = false,
                timestamp = Date()
            )
        }
    }
}
