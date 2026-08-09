package com.app.coachup.app.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.app.coachup.app.MainActivity
import com.app.coachup.app.ui.training.KmSplit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// GPS Workout Foreground Service
// ---------------------------------------------------------------------------
// Bu servis, ekran kilitliyken veya uygulama arka planda olsa bile GPS
// güncellemelerini kesintisiz alır. LocationTrackingService companion object
// üzerinden state'e erişilir; servis lifecycle'ı startForegroundTracking /
// stopForegroundTracking ile yönetilir.
// ---------------------------------------------------------------------------

class LocationTrackingService : Service() {

    companion object {
        private const val CHANNEL_ID    = "coachup_gps_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.app.coachup.START_TRACKING"
        const val ACTION_STOP  = "com.app.coachup.STOP_TRACKING"
        const val EXTRA_AUTO_PAUSE = "auto_pause"

        // ── Public state ──────────────────────────────────────────────────────
        private val _isTracking       = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _isPaused         = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _isAutoPaused     = MutableStateFlow(false)
        val isAutoPaused: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

        private val _distanceKm       = MutableStateFlow(0.0)
        val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

        private val _currentSpeedKmh  = MutableStateFlow(0.0)
        val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

        private val _averageSpeedKmh  = MutableStateFlow(0.0)
        val averageSpeedKmh: StateFlow<Double> = _averageSpeedKmh.asStateFlow()

        private val _paceMinPerKm     = MutableStateFlow(0.0)
        val paceMinPerKm: StateFlow<Double> = _paceMinPerKm.asStateFlow()

        private val _altitudeGainM    = MutableStateFlow(0.0)
        val altitudeGainM: StateFlow<Double> = _altitudeGainM.asStateFlow()

        private val _splits           = MutableStateFlow<List<KmSplit>>(emptyList())
        val splits: StateFlow<List<KmSplit>> = _splits.asStateFlow()

        private val _isGPSSignalWeak  = MutableStateFlow(false)
        val isGPSSignalWeak: StateFlow<Boolean> = _isGPSSignalWeak.asStateFlow()

        private val _routePoints      = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
        val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints.asStateFlow()

        // ── Internal tracking state ───────────────────────────────────────────
        internal var autoPauseEnabled = false
        internal var trackingStartMs = 0L
        internal var totalPausedMs = 0L
        internal var pauseStartMs = 0L
        internal var totalDistanceM = 0.0
        internal var lastSplitDistanceM = 0.0
        internal var lastLocation: Location? = null
        internal var lastAltitude: Double? = null
        internal var lowSpeedCount = 0

        internal val routeBuffer = mutableListOf<Pair<Double, Double>>()
        internal var lastRoutePublishMs = 0L
        internal var lastPublishedRouteSize = 0

        private const val ROUTE_PUBLISH_INTERVAL_MS = 2_000L
        private const val ROUTE_PUBLISH_MIN_NEW_POINTS = 5
        internal const val AUTO_PAUSE_THRESHOLD   = 1.0  // km/h
        internal const val AUTO_RESUME_THRESHOLD  = 2.5  // km/h

        // ── Convenience helpers ───────────────────────────────────────────────

        /**
         * Start GPS foreground service tracking.
         * Call this instead of the old startTracking() — it works when screen is locked.
         */
        fun startForegroundTracking(context: Context, enableAutoPause: Boolean = true) {
            if (_isTracking.value) return
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUTO_PAUSE, enableAutoPause)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop GPS foreground service. */
        fun stopForegroundTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        // ── Pause / Resume (no service restart needed) ────────────────────────

        fun pauseTracking(manual: Boolean = true) {
            if (!_isTracking.value || _isPaused.value) return
            _isPaused.value = true
            _isAutoPaused.value = !manual
            pauseStartMs = System.currentTimeMillis()
            // Actual GPS update removal is handled inside the service instance
            LocationTrackingService.instance?.removeLocationUpdates()
        }

        @SuppressLint("MissingPermission")
        fun resumeTracking() {
            if (!_isTracking.value || !_isPaused.value) return
            _isPaused.value = false
            _isAutoPaused.value = false
            totalPausedMs += System.currentTimeMillis() - pauseStartMs
            lastLocation = null  // avoid distance jump on resume
            LocationTrackingService.instance?.requestLocationUpdates()
        }

        fun stopTracking() {
            _isTracking.value = false
            _isPaused.value = false
            _isAutoPaused.value = false
            autoPauseEnabled = false
            publishRoutePoints(force = true)
        }

        // ── Internal helpers (called from service instance or companion) ──────

        internal fun reset() {
            lastLocation = null
            lastAltitude = null
            totalDistanceM = 0.0
            lastSplitDistanceM = 0.0
            totalPausedMs = 0L
            pauseStartMs = 0L
            lowSpeedCount = 0
            routeBuffer.clear()
            lastRoutePublishMs = 0L
            lastPublishedRouteSize = 0
            _distanceKm.value = 0.0
            _currentSpeedKmh.value = 0.0
            _averageSpeedKmh.value = 0.0
            _paceMinPerKm.value = 0.0
            _altitudeGainM.value = 0.0
            _splits.value = emptyList()
            _routePoints.value = emptyList()
            _isGPSSignalWeak.value = false
        }

        internal fun appendRoutePoint(point: Pair<Double, Double>) {
            routeBuffer.add(point)
            publishRoutePoints(force = false)
        }

        internal fun publishRoutePoints(force: Boolean) {
            if (routeBuffer.isEmpty()) return
            val now = System.currentTimeMillis()
            val newPoints = routeBuffer.size - lastPublishedRouteSize
            val shouldPublish = force ||
                lastPublishedRouteSize == 0 ||
                newPoints >= ROUTE_PUBLISH_MIN_NEW_POINTS ||
                now - lastRoutePublishMs >= ROUTE_PUBLISH_INTERVAL_MS
            if (!shouldPublish) return
            _routePoints.value = routeBuffer.toList()
            lastPublishedRouteSize = routeBuffer.size
            lastRoutePublishMs = now
        }

        internal fun processLocation(location: Location) {
            _isGPSSignalWeak.value = location.accuracy > 20f
            appendRoutePoint(Pair(location.latitude, location.longitude))

            val prev = lastLocation
            if (prev != null) {
                val delta = prev.distanceTo(location)
                if (delta > 0 && delta < 200) {
                    totalDistanceM += delta
                    _distanceKm.value = totalDistanceM / 1000.0
                }
            }
            lastLocation = location

            val speedMs  = if (location.hasSpeed()) location.speed else 0f
            val speedKmh = speedMs * 3.6
            _currentSpeedKmh.value = speedKmh

            val elapsedMs = (System.currentTimeMillis() - trackingStartMs - totalPausedMs).coerceAtLeast(1L)
            val avgSpeed  = (totalDistanceM / 1000.0) / (elapsedMs / 3_600_000.0)
            _averageSpeedKmh.value = avgSpeed
            _paceMinPerKm.value = if (avgSpeed > 0) 60.0 / avgSpeed else 0.0

            if (location.hasAltitude()) {
                val alt  = location.altitude
                val prev2 = lastAltitude
                if (prev2 != null && alt > prev2) _altitudeGainM.value += alt - prev2
                lastAltitude = alt
            }

            // Auto-pause (only after initial 15s and at least 10m to prevent instant pause on workout start)
            val elapsedTotalMs = System.currentTimeMillis() - trackingStartMs
            val initialGracePeriod = elapsedTotalMs > 15_000L && totalDistanceM > 10.0
            if (autoPauseEnabled && initialGracePeriod && speedKmh < AUTO_PAUSE_THRESHOLD) {
                lowSpeedCount++
                if (lowSpeedCount >= 5 && !_isPaused.value) pauseTracking(manual = false)
            } else if (speedKmh >= AUTO_RESUME_THRESHOLD && _isPaused.value) {
                lowSpeedCount = 0
                resumeTracking()
            } else {
                lowSpeedCount = 0
            }

            // Per-km splits
            val currentKm = (totalDistanceM / 1000.0).toInt()
            if (currentKm > 0 && totalDistanceM - lastSplitDistanceM >= 1000.0) {
                val splitElapsedMs  = System.currentTimeMillis() - trackingStartMs - totalPausedMs
                val splitDistanceKm = (totalDistanceM - lastSplitDistanceM) / 1000.0
                val splitPace       = if (splitDistanceKm > 0) (splitElapsedMs / 60_000.0) / splitDistanceKm else 0.0
                _splits.value = _splits.value + KmSplit(km = _splits.value.size + 1, paceMinPerKm = splitPace)
                lastSplitDistanceM = totalDistanceM
            }
        }

        fun formattedPace(paceMinPerKm: Double): String {
            if (paceMinPerKm <= 0) return "--'--\""
            val min = paceMinPerKm.toInt()
            val sec = ((paceMinPerKm - min) * 60).toInt()
            return "%d'%02d\"".format(min, sec)
        }

        // Kept for backward-compat callers that used the old object API
        @Deprecated("Use startForegroundTracking(context) instead", ReplaceWith("startForegroundTracking(context, enableAutoPause)"))
        fun startTracking(enableAutoPause: Boolean = true) {
            // No-op: caller must use startForegroundTracking(context)
        }

        /** Reference to the currently-running service instance (set in onCreate/onDestroy). */
        internal var instance: LocationTrackingService? = null
    }

    // ── Service lifecycle ──────────────────────────────────────────────────────

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationLooper: Looper? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (locationLooper == null) {
            locationLooper = HandlerThread("CoachUpLocation").apply { start() }.looper
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val enableAutoPause = intent.getBooleanExtra(EXTRA_AUTO_PAUSE, true)
                startTracking(enableAutoPause)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopTracking()
    }

    // ── Tracking logic ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startTracking(enableAutoPause: Boolean) {
        if (_isTracking.value) return
        _isTracking.value = true
        _isPaused.value = false
        _isAutoPaused.value = false
        autoPauseEnabled = enableAutoPause
        reset()
        trackingStartMs = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, buildNotification())
        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    internal fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val looper = locationLooper ?: Looper.getMainLooper()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, looper)
    }

    internal fun removeLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Antrenman Takibi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Antrenman sırasında GPS konumunuzu takip eder"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoachUP — Antrenman Aktif")
            .setContentText("GPS konum takibi devam ediyor...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
