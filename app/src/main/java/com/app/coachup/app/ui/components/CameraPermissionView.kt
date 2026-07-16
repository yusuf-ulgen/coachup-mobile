package com.app.coachup.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.app.coachup.app.theme.AppNormalMediumStyle
import com.app.coachup.app.theme.CoachUpTheme
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.PrimaryLight
import com.app.coachup.app.theme.Radius
import com.app.coachup.app.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Camera permission request screen – mirrors iOS CameraPermissionView.
 *
 * States handled:
 *  - NOT_DETERMINED: shows "İzin Ver" button that calls [onRequestPermission].
 *  - DENIED / PERMANENTLY_DENIED: shows "Ayarlara Git" button that deep-links
 *    into the app's system settings page.
 *
 * The caller is responsible for providing the [permissionState] obtained via
 * [rememberPermissionState] so this composable stays testable and preview-friendly.
 *
 * @param permissionState  Accompanist [PermissionState] for [android.Manifest.permission.CAMERA].
 * @param modifier         Optional layout modifier.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionView(
    permissionState: PermissionState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRequesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isDenied = permissionState.status == PermissionStatus.Denied(shouldShowRationale = false)
    val shouldShowRationale = permissionState.status == PermissionStatus.Denied(shouldShowRationale = true)
    val isNotDetermined = !permissionState.status.isGranted && !isDenied

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
    ) {
        // Camera icon inside a circular primary-tinted background
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(color = PrimaryLight, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(48.dp)
            )
        }

        // Title + description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Kamera İzni Gerekli",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Text(
                text = "QR kodu okutabilmek için kamera erişimine izin vermelisiniz.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xxxl)
            )
        }

        // Action button section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            when {
                isNotDetermined || shouldShowRationale -> {
                    // Request permission button
                    Button(
                        onClick = {
                            scope.launch {
                                isRequesting = true
                                permissionState.launchPermissionRequest()
                                isRequesting = false
                            }
                        },
                        enabled = !isRequesting,
                        shape = RoundedCornerShape(Radius.pill),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl)
                            .height(52.dp)
                    ) {
                        if (isRequesting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "İzin Ver",
                                style = AppNormalMediumStyle,
                                color = Color.White
                            )
                        }
                    }
                }

                isDenied -> {
                    // Open system settings button
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(Radius.pill),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl)
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        Text(
                            text = "Ayarlara Git",
                            style = AppNormalMediumStyle,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Kamera iznini Ayarlar > CoachUp'tan etkinleştirebilirsiniz.",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.xxxl)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview helpers – stub PermissionState to avoid a real permission call
// ---------------------------------------------------------------------------

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true, name = "Not Determined")
@Composable
private fun CameraPermissionNotDeterminedPreview() {
    CoachUpTheme {
        // A stub that behaves as "not yet asked"
        val stub = object : PermissionState {
            override val permission = android.Manifest.permission.CAMERA
            override val status = PermissionStatus.Denied(shouldShowRationale = true)
            override fun launchPermissionRequest() {}
        }
        CameraPermissionView(permissionState = stub)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true, name = "Permanently Denied")
@Composable
private fun CameraPermissionDeniedPreview() {
    CoachUpTheme {
        val stub = object : PermissionState {
            override val permission = android.Manifest.permission.CAMERA
            override val status = PermissionStatus.Denied(shouldShowRationale = false)
            override fun launchPermissionRequest() {}
        }
        CameraPermissionView(permissionState = stub)
    }
}
