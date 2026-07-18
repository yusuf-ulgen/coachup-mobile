package com.app.coachup.app.ui.training

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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

private enum class ShareTemplate { SIMPLE, DETAILED }

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
    val pagerState = rememberPagerState(pageCount = { 2 })

    val config = LocalConfiguration.current
    val cardWidthDp = min(config.screenWidthDp * 0.88f, 340f).dp
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                    Text(
                        text = "Aktiviteyi Paylaş",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val template = if (page == 0) ShareTemplate.SIMPLE else ShareTemplate.DETAILED
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
                                    .shadow(24.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            ) {
                                ShareCardContent(
                                    training = training,
                                    durationSeconds = durationSeconds,
                                    totalCalories = totalCalories,
                                    distanceKm = distanceKm,
                                    avgPaceMinPerKm = avgPaceMinPerKm,
                                    routePoints = routePoints,
                                    backgroundUri = backgroundUri,
                                    template = template
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) Color.White
                                        else Color.White.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (pagerState.currentPage == 0) {
                            "Detaylı kart için kaydırın"
                        } else {
                            "Basit kart için geri kaydırın"
                        },
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (backgroundUri == null) "Fotoğraf Ekle" else "Fotoğrafı Değiştir",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            if (isSharing) return@Button
                            isSharing = true
                            shareError = null
                            scope.launch {
                                val selectedTemplate =
                                    if (pagerState.currentPage == 0) ShareTemplate.SIMPLE else ShareTemplate.DETAILED
                                try {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        ShareCardBitmapRenderer.render(
                                            context = context,
                                            training = training,
                                            durationSeconds = durationSeconds,
                                            totalCalories = totalCalories,
                                            distanceKm = distanceKm,
                                            avgPaceMinPerKm = avgPaceMinPerKm,
                                            routePoints = routePoints,
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
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Primary.copy(alpha = 0.55f)
                        )
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Paylaş", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview card — Strava-style story layout
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
    template: ShareTemplate = ShareTemplate.DETAILED
) {
    val context = LocalContext.current
    val hasRoute = routePoints.size >= 2
    val showDistance = distanceKm > 0.01
    val heroValue = when {
        template == ShareTemplate.SIMPLE && showDistance -> formatShareDistance(distanceKm)
        template == ShareTemplate.SIMPLE -> formatShareTime(durationSeconds)
        showDistance -> formatShareDistance(distanceKm)
        else -> formatShareTime(durationSeconds)
    }
    val heroLabel = when {
        template == ShareTemplate.SIMPLE && showDistance -> "Mesafe"
        template == ShareTemplate.SIMPLE -> "Süre"
        showDistance -> "Mesafe"
        else -> "Süre"
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141418))) {
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
                                    Color(0xFF101018),
                                    Color(0xFF1A1428),
                                    Color(0xFF2A1838)
                                )
                            )
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1.0f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )


        if (template == ShareTemplate.SIMPLE) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Training title at top
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(
                        painter = painterResource(R.drawable.coach_logo),
                        contentDescription = "CoachUP",
                        modifier = Modifier.height(22.dp).width(76.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = training.title.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                }
                // Hero value centered
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = heroLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.65f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = heroValue,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-1).sp,
                        lineHeight = 58.sp,
                        textAlign = TextAlign.Center
                    )
                }
                // Bottom label
                Text(
                    text = "Antrenman Tamamlandı",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.coach_logo),
                        contentDescription = "CoachUP",
                        modifier = Modifier
                            .height(22.dp)
                            .width(76.dp),
                        contentScale = ContentScale.Fit
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = training.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Antrenman tamamlandı",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }

                    Text(
                        text = heroValue,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 46.sp
                    )
                    Text(
                        text = heroLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Always show duration chip
                        ShareMetricChip(
                            value = formatShareTime(durationSeconds),
                            label = "Süre",
                            modifier = Modifier.weight(1f)
                        )
                        if (showDistance) {
                            ShareMetricChip(
                                value = "%.2f km".format(distanceKm),
                                label = "Mesafe",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (avgPaceMinPerKm > 0.1) {
                            ShareMetricChip(
                                value = formatSharePace(avgPaceMinPerKm),
                                label = "Tempo",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (totalCalories > 0) {
                            ShareMetricChip(
                                value = "$totalCalories",
                                label = "Kalori",
                                modifier = Modifier.weight(1f)
                            )
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun ShareRouteCanvas(
    routePoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val routeColor = Primary
    val points = remember(routePoints) {
        routePoints.map { Offset(it.second.toFloat(), it.first.toFloat()) }
    }

    Canvas(modifier = modifier.background(Color(0xFF1A1A22))) {
        val normalized = normalizeRouteForCanvas(points, size.width, size.height, 56f)
        if (normalized.size < 2) return@Canvas

        for (x in 0..size.width.toInt() step 48) {
            drawLine(
                color = Color.White.copy(alpha = 0.03f),
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height)
            )
        }
        for (y in 0..size.height.toInt() step 48) {
            drawLine(
                color = Color.White.copy(alpha = 0.03f),
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat())
            )
        }

        for (i in 0 until normalized.lastIndex) {
            drawLine(
                color = routeColor.copy(alpha = 0.35f),
                start = normalized[i],
                end = normalized[i + 1],
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0 until normalized.lastIndex) {
            drawLine(
                color = routeColor,
                start = normalized[i],
                end = normalized[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = Color(0xFF4CAF50), radius = 10f, center = normalized.first())
        drawCircle(color = routeColor, radius = 10f, center = normalized.last())
    }
}

// ---------------------------------------------------------------------------
// Bitmap renderer — mirrors preview, no ComposeView / PixelCopy
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
        template: ShareTemplate = ShareTemplate.DETAILED
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = loadBackground(context, backgroundUri, WIDTH, HEIGHT)
        if (bg != null) {
            canvas.drawBitmap(bg, 0f, 0f, null)
            if (bg !== bitmap) bg.recycle()
        } else if (routePoints.size >= 2) {
            drawRouteBackground(canvas, routePoints)
        } else {
            drawDefaultGradient(canvas)
        }

        drawOverlayGradient(canvas)
        if (template == ShareTemplate.SIMPLE) {
            drawLogo(context, canvas)
        }

        drawStravaPanel(
            context = context,
            canvas = canvas,
            training = training,
            durationSeconds = durationSeconds,
            totalCalories = totalCalories,
            distanceKm = distanceKm,
            avgPaceMinPerKm = avgPaceMinPerKm,
            template = template
        )

        return bitmap
    }

    private fun drawRouteBackground(canvas: Canvas, routePoints: List<Pair<Double, Double>>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A22.toInt() }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        val gridPaint = Paint().apply { color = 0x08FFFFFF }
        var x = 0f
        while (x < WIDTH) {
            canvas.drawLine(x, 0f, x, HEIGHT.toFloat(), gridPaint)
            x += 48f
        }
        var y = 0f
        while (y < HEIGHT) {
            canvas.drawLine(0f, y, WIDTH.toFloat(), y, gridPaint)
            y += 48f
        }

        val offsets = routePoints.map { Offset(it.second.toFloat(), it.first.toFloat()) }
        val normalized = normalizeRouteForBitmap(offsets, WIDTH.toFloat(), HEIGHT.toFloat(), 120f)
        if (normalized.size < 2) return

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            strokeWidth = 22f
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
                intArrayOf(0xFF101018.toInt(), 0xFF1A1428.toInt(), 0xFF2A1838.toInt()),
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
                intArrayOf(0x26000000, 0x00000000, 0x8C000000.toInt(), 0xEB000000.toInt()),
                floatArrayOf(0f, 0.45f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawLogo(context: Context, canvas: Canvas) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.coach_logo) ?: return
        val logoH = 56
        val logoW = (logoH * (320f / 96f)).roundToInt()
        val left = (WIDTH - logoW) / 2
        val top = HEIGHT - logoH - 72
        drawable.setBounds(left, top, left + logoW, top + logoH)
        drawable.draw(canvas)
    }

    private fun drawDetailedLogo(context: Context, canvas: Canvas, left: Float, aboveTextY: Float) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.coach_logo) ?: return
        val logoH = 44
        val logoW = (logoH * (320f / 96f)).roundToInt()
        val top = (aboveTextY - 16f - logoH).toInt()
        drawable.setBounds(left.toInt(), top, left.toInt() + logoW, top + logoH)
        drawable.draw(canvas)
    }

    private fun drawStravaPanel(
        context: Context,
        canvas: Canvas,
        training: Training,
        durationSeconds: Int,
        totalCalories: Int,
        distanceKm: Double,
        avgPaceMinPerKm: Double,
        template: ShareTemplate
    ) {
        val showDistance = distanceKm > 0.01
        val heroValue = when {
            template == ShareTemplate.SIMPLE && showDistance -> formatShareDistance(distanceKm)
            template == ShareTemplate.SIMPLE -> formatShareTime(durationSeconds)
            showDistance -> formatShareDistance(distanceKm)
            else -> formatShareTime(durationSeconds)
        }
        val heroLabel = when {
            template == ShareTemplate.SIMPLE && showDistance -> "MESAFE"
            template == ShareTemplate.SIMPLE -> "SÜRE"
            showDistance -> "MESAFE"
            else -> "SÜRE"
        }

        if (template == ShareTemplate.SIMPLE) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.12f
                textAlign = Paint.Align.CENTER
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xA6FFFFFF.toInt()
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.1f
                textAlign = Paint.Align.CENTER
            }
            val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 148f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x73FFFFFF.toInt()
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            val centerX = WIDTH / 2f
            // Draw logo at top center
            drawLogo(context, canvas)
            // Training title below logo
            canvas.drawText(training.title.uppercase(), centerX, 200f, titlePaint)
            // Hero value centered
            val centerY = HEIGHT * 0.46f
            canvas.drawText(heroLabel, centerX, centerY, labelPaint)
            canvas.drawText(heroValue, centerX, centerY + 120f, heroPaint)
            // Bottom label
            canvas.drawText("Antrenman Tamamland\u0131", centerX, HEIGHT - 80f, footerPaint)
            return
        }

        val padding = 60f
        val chipHeight = 110f
        val chipTop = HEIGHT - chipHeight - 48f

        val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt()
            textSize = 34f
        }
        val headerY = chipTop - 200f
        drawDetailedLogo(context, canvas, padding, headerY)
        canvas.drawText(training.title.uppercase(), padding, headerY, categoryPaint)
        canvas.drawText("Antrenman tamamlandı", padding, headerY + 48f, subtitlePaint)

        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 132f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(heroValue, padding, chipTop - 36f, heroPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
        }
        canvas.drawText(heroLabel, padding, chipTop - 4f, labelPaint)

        if (template != ShareTemplate.SIMPLE) {
            val chips = buildList {
                // Always show duration
                add(formatShareTime(durationSeconds) to "S\u00fcre")
                if (showDistance) add("%.2f km".format(distanceKm) to "Mesafe")
                if (avgPaceMinPerKm > 0.1) add(formatSharePace(avgPaceMinPerKm) to "Tempo")
                if (totalCalories > 0) add("$totalCalories" to "Kalori")
            }
            if (chips.isNotEmpty()) {
                val chipWidth = (WIDTH - padding * 2 - 24f) / chips.size
                chips.forEachIndexed { index, (value, label) ->
                    val left = padding + index * (chipWidth + 12f)
                    val chipRect = RectF(left, chipTop, left + chipWidth, chipTop + chipHeight)
                    val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF }
                    canvas.drawRoundRect(chipRect, 24f, 24f, chipBg)

                    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = 42f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val chipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x8CFFFFFF.toInt()
                        textSize = 24f
                    }
                    canvas.drawText(value, left + 24f, chipTop + 52f, valuePaint)
                    canvas.drawText(label, left + 24f, chipTop + 88f, chipLabelPaint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Share helpers
// ---------------------------------------------------------------------------

private fun shareWorkoutCard(context: Context, bitmap: Bitmap): Boolean {
    val activity = context.findActivity() ?: run {
        bitmap.recycle()
        return false
    }
    return try {
        val file = File(activity.cacheDir, "coachup_workout_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) return false
        }
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "CoachUP ile antrenmanımı tamamladım 💪")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(activity.contentResolver, "workout", uri)
        }
        val chooser = Intent.createChooser(intent, "Antrenmanı Paylaş")
        val receivers = activity.packageManager.queryIntentActivities(
            chooser,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in receivers) {
            val packageName = resolveInfo.activityInfo.packageName
            activity.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        activity.startActivity(chooser)
        true
    } catch (_: Exception) {
        false
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

private fun shareWorkoutText(
    context: Context,
    training: Training,
    durationSeconds: Int,
    distanceKm: Double
) {
    val activity = context.findActivity() ?: return
    val distancePart = if (distanceKm > 0.01) " · ${formatShareDistance(distanceKm)}" else ""
    val text = "CoachUP'ta antrenmanımı tamamladım! 💪\n" +
        "${training.title} · ${formatShareTime(durationSeconds)}$distancePart\n" +
        "coachup.app"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        activity.startActivity(Intent.createChooser(intent, "Antrenmanı Paylaş"))
    } catch (_: Exception) {
        // no-op
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private data class PointF(val x: Float, val y: Float)

private fun normalizeRouteForCanvas(
    points: List<Offset>,
    width: Float,
    height: Float,
    padding: Float
): List<Offset> {
    if (points.size < 2) return emptyList()
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanX = max(maxX - minX, 0.0001f)
    val spanY = max(maxY - minY, 0.0001f)
    val usableW = width - padding * 2
    val usableH = height - padding * 2
    val scale = min(usableW / spanX, usableH / spanY)
    val offsetX = padding + (usableW - spanX * scale) / 2f
    val offsetY = padding + (usableH - spanY * scale) / 2f
    return points.map { p ->
        Offset(
            x = offsetX + (p.x - minX) * scale,
            y = offsetY + (maxY - p.y) * scale
        )
    }
}

private fun normalizeRouteForBitmap(
    points: List<Offset>,
    width: Float,
    height: Float,
    padding: Float
): List<PointF> =
    normalizeRouteForCanvas(points, width, height, padding).map { PointF(it.x, it.y) }

private fun formatShareDistance(km: Double): String =
    if (km >= 10) "%.1f km".format(km) else "%.2f km".format(km)

private fun formatShareTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatSharePace(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0) return "--"
    val min = paceMinPerKm.toInt()
    val sec = ((paceMinPerKm - min) * 60).toInt()
    return "%d'%02d\"".format(min, sec)
}
