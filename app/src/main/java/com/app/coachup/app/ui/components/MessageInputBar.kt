package com.app.coachup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.coachup.app.theme.CoachUpTheme
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.Radius
import com.app.coachup.app.theme.Spacing

/**
 * Chat input bar composable – mirrors iOS MessageInputBar.
 *
 * Features:
 *  - Multi-line text field (1–4 visible lines) inside a pill-shaped container.
 *  - Send button uses a circular arrow-up icon; disabled + grey when [text] is blank
 *    or [isSending] is true, coloured primary otherwise.
 *  - Elevated white background with a subtle top shadow matching iOS behaviour.
 *
 * @param text      Current text field value.
 * @param onTextChange  Callback invoked on every keystroke.
 * @param isSending Whether a send operation is already in flight.
 * @param onSend    Invoked when the user taps the send button.
 * @param modifier  Optional layout modifier.
 */
@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSendEnabled = text.isNotBlank() && !isSending
    val sendIconTint = if (isSendEnabled) Primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                spotColor = Color.Black.copy(alpha = 0.05f),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Bottom
        ) {
            // Multi-line text field inside a pill background
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.inputField))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.lg, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(Primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = "Mesajınızı yazın...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = isSendEnabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Gönder",
                    tint = sendIconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun MessageInputBarEmptyPreview() {
    CoachUpTheme {
        MessageInputBar(
            text = "",
            onTextChange = {},
            isSending = false,
            onSend = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputBarFilledPreview() {
    CoachUpTheme {
        MessageInputBar(
            text = "Merhaba, antrenman programı hakkında bilgi alabilir miyim?",
            onTextChange = {},
            isSending = false,
            onSend = {}
        )
    }
}
