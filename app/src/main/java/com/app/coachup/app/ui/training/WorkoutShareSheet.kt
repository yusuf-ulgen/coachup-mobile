package com.app.coachup.app.ui.training

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.app.coachup.app.R
import com.app.coachup.app.models.Training
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

public enum class ShareTemplate(val title: String, val description: String) {
    MAP_FOCUSED("Rota Odaklı", "Harita ve rota çizimini ön plana çıkarır"),
    DETAILED("Detaylı İstatistik", "Tüm performans verilerini şık kartlarda sunar"),
    SIMPLE("Minimal", "Sadece mesafe ve süre vurgusu yapar"),
    CARD_STORY("Hikaye Kartı", "Koyu arka plan ile sosyal medya için ideal kart")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutShareSheet(
    training: Training,
    durationSeconds: Int,
    totalCalories: Int,
    distanceKm: Double = 0.0,
    avgPaceMinPerKm: Double = 0.0,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    // Rota verisini güvenli state'e alalım (kaybolmaması için)
    val cachedRoutePoints = remember(routePoints) { routePoints.toList() }

    val templates = ShareTemplate.entries.toTypedArray()
    val pagerState = rememberPagerState(pageCount = { templates.size })

    val config = LocalConfiguration.current
    val cardWidthDp = min(config.screenWidthDp * 0.82f, 320f).dp
    val cardHeightDp = cardWidthDp * (16f / 9f)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> backgroundUri = uri }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0A0F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // ── Top Bar — Düzgün Hizalama & Padding ──────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "Aktiviteyi Paylaş",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(36.dp))
                }

                // ── Kart Önizlemesi & Pager ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val template = templates[page]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(cardWidthDp)
                                    .height(cardHeightDp)
                                    .shadow(16.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            ) {
                                ShareCardContent(
                                    training = training,
                                    durationSeconds = durationSeconds,
                                    totalCalories = totalCalories,
                                    distanceKm = distanceKm,
                                    avgPaceMinPerKm = avgPaceMinPerKm,
                                    routePoints = cachedRoutePoints,
                                    backgroundUri = backgroundUri,
                                    template = template
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Indicator Dots ──────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(templates.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) Primary
                                        else Color.White.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Template Name & Instruction Label ────────────────────
                    val currentTemplate = templates[pagerState.currentPage]
                    Text(
                        text = currentTemplate.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentTemplate.description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                // ── Action Buttons ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    shareError?.let { msg ->
                        Text(
                            text = msg,
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (backgroundUri == null) "Arka Plan Fotoğrafı Ekle" else "Fotoğrafı Değiştir",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            if (isSharing) return@Button
                            isSharing = true
                            shareError = null
                            scope.launch {
                                val selectedTemplate = templates[pagerState.currentPage]
                                try {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        ShareCardBitmapRenderer.render(
                                            context = context,
                                            training = training,
                                            durationSeconds = durationSeconds,
                                            totalCalories = totalCalories,
                                            distanceKm = distanceKm,
                                            avgPaceMinPerKm = avgPaceMinPerKm,
                                            routePoints = cachedRoutePoints,
                                            backgroundUri = backgroundUri,
                                            template = selectedTemplate
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        val shared = shareWorkoutCard(context, bitmap)
                                        if (!shared) {
                                            shareWorkoutText(context, training, durationSeconds, distanceKm)
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: OutOfMemoryError) {
                                    withContext(Dispatchers.Main) {
                                        shareError = "Görsel oluşturulamadı, metin paylaşılıyor…"
                                        shareWorkoutText(context, training, durationSeconds, distanceKm)
                                    }
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) {
                                        shareWorkoutText(context, training, durationSeconds, distanceKm)
                                    }
                                } finally {
                                    isSharing = false
                                }
                            }
                        },
                        enabled = !isSharing,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Primary.copy(alpha = 0.55f)
                        )
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Görseli Paylaş", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview Card Composable — 4 Strava-Style Templates
// ---------------------------------------------------------------------------

@Composable
private fun ShareCardContent(
    training: Training,
    durationSeconds: Int,
    totalCalories: Int,
    distanceKm: Double,
    avgPaceMinPerKm: Double,
    routePoints: List<Pair<Double, Double>>,
    backgroundUri: Uri?,
    template: ShareTemplate
) {
    val context = LocalContext.current
    val hasRoute = routePoints.size >= 2
    val showDistance = distanceKm > 0.01

    val distanceStr = formatShareDistance(distanceKm)
    val durationStr = formatShareTime(durationSeconds)
    val paceStr     = formatSharePace(avgPaceMinPerKm)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121216))) {
        // ── Background Layer ──────────────────────────────────────────────────
        when {
            backgroundUri != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backgroundUri)
                        .size(Size(1080, 1920))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (hasRoute) {
                    ShareRouteCanvas(
                        routePoints = routePoints,
                        modifier = Modifier.fillMaxSize(),
                        transparentBackground = true
                    )
                }
            }
            hasRoute -> {
                ShareRouteCanvas(
                    routePoints = routePoints,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0F0C20),
                                    Color(0xFF18102B),
                                    Color(0xFF281432)
                                )
                            )
                        )
                )
            }
        }

        // Gradient overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.25f),
                        0.4f to Color.Transparent,
                        0.65f to Color.Black.copy(alpha = 0.6f),
                        1.0f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        // ── Content Overlay based on Template ────────────────────────────────
        when (template) {
            ShareTemplate.MAP_FOCUSED -> {
                // Template 1: Rota Odaklı (Harita üstünde temiz marka + metrikler)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Logo (Right Aligned)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Image(
                            painter = painterResource(R.drawable.coach_logo),
                            contentDescription = "CoachUP",
                            modifier = Modifier.height(22.dp).width(76.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Bottom Stats
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = training.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            letterSpacing = 1.5.sp
                        )
                        if (showDistance) {
                            Text(
                                text = distanceStr,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 44.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(durationStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Süre", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                            if (avgPaceMinPerKm > 0.1) {
                                Column {
                                    Text(paceStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Tempo", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                            if (totalCalories > 0) {
                                Column {
                                    Text("$totalCalories kcal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Kalori", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }

            ShareTemplate.DETAILED -> {
                // Template 2: Detaylı İstatistik (Alt panelde kutucuklu metrikler)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Image(
                            painter = painterResource(R.drawable.coach_logo),
                            contentDescription = "CoachUP",
                            modifier = Modifier.height(20.dp).width(72.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = training.title.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Antrenman tamamlandı",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }

                        if (showDistance) {
                            Text(
                                text = distanceStr,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 42.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShareMetricChip(value = durationStr, label = "Süre", modifier = Modifier.weight(1f))
                            if (showDistance) {
                                ShareMetricChip(value = "%.2f km".format(distanceKm), label = "Mesafe", modifier = Modifier.weight(1f))
                            }
                            if (avgPaceMinPerKm > 0.1) {
                                ShareMetricChip(value = paceStr, label = "Tempo", modifier = Modifier.weight(1f))
                            }
                            if (totalCalories > 0) {
                                ShareMetricChip(value = "$totalCalories", label = "Kalori", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            ShareTemplate.SIMPLE -> {
                // Template 3: Minimal (Sadece büyük mesafe / süre ortada)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Image(
                            painter = painterResource(R.drawable.coach_logo),
                            contentDescription = "CoachUP",
                            modifier = Modifier.height(22.dp).width(76.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = training.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.2.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (showDistance) "Mesafe" else "Süre",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (showDistance) distanceStr else durationStr,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "Antrenman Tamamlandı",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }

            ShareTemplate.CARD_STORY -> {
                // Template 4: Hikaye Kartı (Sosyal medya için özel kart görünümü)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Image(
                                painter = painterResource(R.drawable.coach_logo),
                                contentDescription = "CoachUP",
                                modifier = Modifier.height(20.dp).width(72.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = training.title.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.5.sp
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                        if (showDistance) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MESAFE", fontSize = 10.sp, color = Primary, fontWeight = FontWeight.Bold)
                                Text(distanceStr, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SÜRE", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text(durationStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            if (avgPaceMinPerKm > 0.1) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TEMPO", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                    Text(paceStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            if (totalCalories > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("KALORİ", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                    Text("$totalCalories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareMetricChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun ShareRouteCanvas(
    routePoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    transparentBackground: Boolean = false
) {
    val routeColor = Primary
    val points = remember(routePoints) {
        routePoints.map { Offset(it.second.toFloat(), it.first.toFloat()) }
    }

    val canvasModifier = if (transparentBackground) modifier else modifier.background(Color(0xFF121218))

    Canvas(modifier = canvasModifier) {
        val normalized = normalizeRouteForCanvas(points, size.width, size.height, 56f)
        if (normalized.size < 2) return@Canvas

        if (!transparentBackground) {
            for (x in 0..size.width.toInt() step 44) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height)
                )
            }
            for (y in 0..size.height.toInt() step 44) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat())
                )
            }
        }

        // Glow line
        for (i in 0 until normalized.lastIndex) {
            drawLine(
                color = routeColor.copy(alpha = 0.35f),
                start = normalized[i],
                end = normalized[i + 1],
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
        }
        // Main route line
        for (i in 0 until normalized.lastIndex) {
            drawLine(
                color = routeColor,
                start = normalized[i],
                end = normalized[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = Color(0xFF4CAF50), radius = 9f, center = normalized.first())
        drawCircle(color = routeColor, radius = 9f, center = normalized.last())
    }
}

// ---------------------------------------------------------------------------
// Bitmap Renderer — 100% WYSIWYG matching for all 4 templates
// ---------------------------------------------------------------------------

private object ShareCardBitmapRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val PRIMARY = 0xFFFF6047.toInt()

    fun render(
        context: Context,
        training: Training,
        durationSeconds: Int,
        totalCalories: Int,
        distanceKm: Double,
        avgPaceMinPerKm: Double,
        routePoints: List<Pair<Double, Double>>,
        backgroundUri: Uri?,
        template: ShareTemplate
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = loadBackground(context, backgroundUri, WIDTH, HEIGHT)
        if (bg != null) {
            canvas.drawBitmap(bg, 0f, 0f, null)
            if (bg !== bitmap) bg.recycle()
            if (routePoints.size >= 2) {
                drawRoutePathOnCanvas(canvas, routePoints, drawBackground = false)
            }
        } else if (routePoints.size >= 2) {
            drawRoutePathOnCanvas(canvas, routePoints, drawBackground = true)
        } else {
            drawDefaultGradient(canvas)
        }

        drawOverlayGradient(canvas)

        when (template) {
            ShareTemplate.MAP_FOCUSED -> drawMapFocusedTemplate(context, canvas, training, durationSeconds, totalCalories, distanceKm, avgPaceMinPerKm)
            ShareTemplate.DETAILED    -> drawDetailedTemplate(context, canvas, training, durationSeconds, totalCalories, distanceKm, avgPaceMinPerKm)
            ShareTemplate.SIMPLE      -> drawSimpleTemplate(context, canvas, training, durationSeconds, distanceKm)
            ShareTemplate.CARD_STORY  -> drawCardStoryTemplate(context, canvas, training, durationSeconds, totalCalories, distanceKm, avgPaceMinPerKm)
        }

        return bitmap
    }

    private fun drawMapFocusedTemplate(
        context: Context,
        canvas: Canvas,
        training: Training,
        durationSeconds: Int,
        totalCalories: Int,
        distanceKm: Double,
        avgPaceMinPerKm: Double
    ) {
        val padding = 80f
        drawTopLogo(context, canvas, padding, 120f)

        val showDistance = distanceKm > 0.01
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 140f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 30f
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bottomY = HEIGHT - 160f
        canvas.drawText(training.title.uppercase(), padding, bottomY - 240f, titlePaint)
        if (showDistance) {
            canvas.drawText(formatShareDistance(distanceKm), padding, bottomY - 90f, heroPaint)
        }

        var startX = padding
        // Duration
        canvas.drawText(formatShareTime(durationSeconds), startX, bottomY + 30f, valPaint)
        canvas.drawText("Süre", startX, bottomY + 70f, labelPaint)
        startX += 220f

        if (avgPaceMinPerKm > 0.1) {
            canvas.drawText(formatSharePace(avgPaceMinPerKm), startX, bottomY + 30f, valPaint)
            canvas.drawText("Tempo", startX, bottomY + 70f, labelPaint)
            startX += 220f
        }
        if (totalCalories > 0) {
            canvas.drawText("$totalCalories kcal", startX, bottomY + 30f, valPaint)
            canvas.drawText("Kalori", startX, bottomY + 70f, labelPaint)
        }
    }

    private fun drawDetailedTemplate(
        context: Context,
        canvas: Canvas,
        training: Training,
        durationSeconds: Int,
        totalCalories: Int,
        distanceKm: Double,
        avgPaceMinPerKm: Double
    ) {
        val padding = 80f
        val chipHeight = 130f
        val chipTop = HEIGHT - chipHeight - 120f
        val headerY = chipTop - 220f

        drawTopLogo(context, canvas, padding, headerY - 50f)

        val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt()
            textSize = 38f
        }
        canvas.drawText(training.title.uppercase(), padding, headerY, categoryPaint)
        canvas.drawText("Antrenman tamamlandı", padding, headerY + 50f, subtitlePaint)

        val showDistance = distanceKm > 0.01
        if (showDistance) {
            val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 140f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(formatShareDistance(distanceKm), padding, chipTop - 30f, heroPaint)
        }

        val chips = buildList {
            add(formatShareTime(durationSeconds) to "Süre")
            if (showDistance) add("%.2f km".format(distanceKm) to "Mesafe")
            if (avgPaceMinPerKm > 0.1) add(formatSharePace(avgPaceMinPerKm) to "Tempo")
            if (totalCalories > 0) add("$totalCalories" to "Kalori")
        }

        if (chips.isNotEmpty()) {
            val chipWidth = (WIDTH - padding * 2 - (chips.size - 1) * 24f) / chips.size
            chips.forEachIndexed { index, (value, label) ->
                val left = padding + index * (chipWidth + 24f)
                val chipRect = RectF(left, chipTop, left + chipWidth, chipTop + chipHeight)
                val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
                canvas.drawRoundRect(chipRect, 24f, 24f, chipBg)

                val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 42f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x8CFFFFFF.toInt()
                    textSize = 26f
                }
                canvas.drawText(value, left + 20f, chipTop + 55f, valP)
                canvas.drawText(label, left + 20f, chipTop + 95f, lblP)
            }
        }
    }

    private fun drawSimpleTemplate(
        context: Context,
        canvas: Canvas,
        training: Training,
        durationSeconds: Int,
        distanceKm: Double
    ) {
        val centerX = WIDTH / 2f
        val showDistance = distanceKm > 0.01

        drawTopLogo(context, canvas, 80f, 120f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(training.title.uppercase(), WIDTH - 80f, 210f, titlePaint)

        val centerY = HEIGHT * 0.46f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
            textAlign = Paint.Align.CENTER
        }
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 160f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(if (showDistance) "MESAFE" else "SÜRE", WIDTH / 2f, centerY, labelPaint)
        canvas.drawText(if (showDistance) formatShareDistance(distanceKm) else formatShareTime(durationSeconds), WIDTH / 2f, centerY + 140f, heroPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73FFFFFF.toInt()
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Antrenman Tamamlandı", WIDTH / 2f, HEIGHT - 100f, footerPaint)
    }

    private fun drawCardStoryTemplate(
        context: Context,
        canvas: Canvas,
        training: Training,
        durationSeconds: Int,
        totalCalories: Int,
        distanceKm: Double,
        avgPaceMinPerKm: Double
    ) {
        val cardMargin = 80f
        val cardTop = HEIGHT * 0.28f
        val cardBottom = HEIGHT * 0.72f
        val cardRect = RectF(cardMargin, cardTop, WIDTH - cardMargin, cardBottom)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC8000000.toInt() }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 130
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, bgPaint)
        canvas.drawRoundRect(cardRect, 48f, 48f, strokePaint)

        drawTopLogo(context, canvas, cardMargin + 40f, cardTop + 50f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(training.title.uppercase(), WIDTH - cardMargin - 40f, cardTop + 140f, titlePaint)

        val showDistance = distanceKm > 0.01
        if (showDistance) {
            val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PRIMARY
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 110f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("MESAFE", centerX, cardTop + 240f, lblP)
            canvas.drawText(formatShareDistance(distanceKm), centerX, cardTop + 350f, valP)
        }

        // Bottom stats row
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt()
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        val rowY = cardBottom - 100f
        canvas.drawText(formatShareTime(durationSeconds), centerX - 240f, rowY, valPaint)
        canvas.drawText("SÜRE", centerX - 240f, rowY + 36f, lblPaint)

        if (avgPaceMinPerKm > 0.1) {
            canvas.drawText(formatSharePace(avgPaceMinPerKm), centerX, rowY, valPaint)
            canvas.drawText("TEMPO", centerX, rowY + 36f, lblPaint)
        }
        if (totalCalories > 0) {
            canvas.drawText("$totalCalories", centerX + 240f, rowY, valPaint)
            canvas.drawText("KALORİ", centerX + 240f, rowY + 36f, lblPaint)
        }
    }

    private fun drawRoutePathOnCanvas(
        canvas: Canvas,
        routePoints: List<Pair<Double, Double>>,
        drawBackground: Boolean = true
    ) {
        if (drawBackground) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF121218.toInt() }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

            val gridPaint = Paint().apply { color = 0x0AFFFFFF }
            var x = 0f
            while (x < WIDTH) {
                canvas.drawLine(x, 0f, x, HEIGHT.toFloat(), gridPaint)
                x += 44f
            }
            var y = 0f
            while (y < HEIGHT) {
                canvas.drawLine(0f, y, WIDTH.toFloat(), y, gridPaint)
                y += 44f
            }
        }

        val offsets = routePoints.map { Offset(it.second.toFloat(), it.first.toFloat()) }
        val normalized = normalizeRouteForBitmap(offsets, WIDTH.toFloat(), HEIGHT.toFloat(), 120f)
        if (normalized.size < 2) return

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            strokeWidth = 24f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            alpha = 90
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            strokeWidth = 10f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        path.moveTo(normalized.first().x, normalized.first().y)
        for (i in 1 until normalized.size) {
            path.lineTo(normalized[i].x, normalized[i].y)
        }
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, routePaint)

        val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
        val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRIMARY }
        canvas.drawCircle(normalized.first().x, normalized.first().y, 16f, startPaint)
        canvas.drawCircle(normalized.last().x, normalized.last().y, 16f, endPaint)
    }

    private fun loadBackground(context: Context, uri: Uri?, width: Int, height: Int): Bitmap? {
        if (uri == null) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = calculateInSampleSize(bounds, width, height)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream, null, options) ?: return null
                centerCrop(decoded, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun centerCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = max(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val scaledW = (source.width * scale).toInt()
        val scaledH = (source.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        if (scaled !== source) source.recycle()
        val x = (scaledW - targetW) / 2
        val y = (scaledH - targetH) / 2
        val cropped = Bitmap.createBitmap(scaled, x, y, targetW, targetH)
        if (cropped !== scaled) scaled.recycle()
        return cropped
    }

    private fun drawDefaultGradient(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(0xFF0F0C20.toInt(), 0xFF18102B.toInt(), 0xFF281432.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawOverlayGradient(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(0x40000000, 0x00000000, 0x99000000.toInt(), 0xEB000000.toInt()),
                floatArrayOf(0f, 0.4f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawTopLogo(context: Context, canvas: Canvas, paddingRight: Float, top: Float) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.coach_logo) ?: return
        val logoH = 48
        val logoW = (logoH * (320f / 96f)).roundToInt()
        val left = (WIDTH - paddingRight - logoW).toInt()
        drawable.setBounds(left, top.toInt(), left + logoW, top.toInt() + logoH)
        drawable.draw(canvas)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatShareDistance(distanceKm: Double): String {
    return "%.2f km".format(distanceKm)
}

private fun formatShareTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatSharePace(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0) return "--'--\""
    val min = paceMinPerKm.toInt()
    val sec = ((paceMinPerKm - min) * 60).toInt()
    return "%d'%02d\" /km".format(min, sec)
}

private fun normalizeRouteForCanvas(
    points: List<Offset>,
    width: Float,
    height: Float,
    padding: Float
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val dx = (maxX - minX).coerceAtLeast(0.00001f)
    val dy = (maxY - minY).coerceAtLeast(0.00001f)
    val drawW = (width - 2 * padding).coerceAtLeast(1f)
    val drawH = (height - 2 * padding).coerceAtLeast(1f)

    val scale = min(drawW / dx, drawH / dy)
    val offsetX = padding + (drawW - dx * scale) / 2f
    val offsetY = padding + (drawH - dy * scale) / 2f

    return points.map { p ->
        val x = offsetX + (p.x - minX) * scale
        val y = offsetY + (maxY - p.y) * scale
        Offset(x, y)
    }
}

private fun normalizeRouteForBitmap(
    points: List<Offset>,
    width: Float,
    height: Float,
    padding: Float
): List<Offset> = normalizeRouteForCanvas(points, width, height, padding)

private fun shareWorkoutCard(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val cacheDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(cacheDir, "coachup_workout_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val authority = "${context.packageName}.provider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("CoachUP Workout", contentUri)
        }
        val chooser = Intent.createChooser(shareIntent, "Antrenmanını Paylaş")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (_: Exception) {
        false
    }
}

private fun shareWorkoutText(
    context: Context,
    training: Training,
    durationSeconds: Int,
    distanceKm: Double
) {
    val text = buildString {
        append("CoachUP ile ")
        append(training.title)
        append(" tamamladım! ")
        if (distanceKm > 0.01) {
            append("%.2f km · ".format(distanceKm))
        }
        val m = durationSeconds / 60
        append("$m dk")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Antrenmanını Paylaş"))
}
