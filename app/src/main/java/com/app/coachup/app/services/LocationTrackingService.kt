package com.app.coachup.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import android.os.Looper
import com.google.android.gms.location.*
import com.app.coachup.app.ui.training.KmSplit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * GPS workout tracking service — Android equivalent of iOS LocationTrackingService.
 *
 * Must call [init] once with an application Context before using.
 */
object LocationTrackingService {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationLooper: Looper? = null

    // ── Public state ──────────────────────────────────────────────────────────
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isAutoPaused = MutableStateFlow(false)
    val isAutoPaused: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

    private var autoPauseEnabled = false

    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0.0)
    val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

    private val _averageSpeedKmh = MutableStateFlow(0.0)
    val averageSpeedKmh: StateFlow<Double> = _averageSpeedKmh.asStateFlow()

    private val _paceMinPerKm = MutableStateFlow(0.0)
    val paceMinPerKm: StateFlow<Double> = _paceMinPerKm.asStateFlow()

    private val _altitudeGainM = MutableStateFlow(0.0)
    val altitudeGainM: StateFlow<Double> = _altitudeGainM.asStateFlow()

    private val _splits = MutableStateFlow<List<KmSplit>>(emptyList())
    val splits: StateFlow<List<KmSplit>> = _splits.asStateFlow()

    private val _isGPSSignalWeak = MutableStateFlow(false)
    val isGPSSignalWeak: StateFlow<Boolean> = _isGPSSignalWeak.asStateFlow()

    private val _routePoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routePoints: StateFlow<List<Pair<Double, Double>>> = _routePoints.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────
    private val routeBuffer = mutableListOf<Pair<Double, Double>>()
    private var lastRoutePublishMs = 0L
    private var lastPublishedRouteSize = 0

    private var lastLocation: Location? = null
    private var lastAltitude: Double? = null
    private var totalDistanceM = 0.0
    private var lastSplitDistanceM = 0.0
    private var trackingStartMs = 0L
    private var totalPausedMs = 0L
    private var pauseStartMs = 0L
    private var lowSpeedCount = 0
    private val autoPauseThreshold = 1.0  // km/h
    private val autoResumeThreshold = 2.5 // km/h

    private const val ROUTE_PUBLISH_INTERVAL_MS = 2_000L
    private const val ROUTE_PUBLISH_MIN_NEW_POINTS = 5

    fun init(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (locationLooper == null) {
            locationLooper = HandlerThread("CoachUpLocation").apply { start() }.looper
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(enableAutoPause: Boolean = true) {
        if (_isTracking.value) return
        _isTracking.value = true
        _isPaused.value = false
        _isAutoPaused.value = false
        autoPauseEnabled = enableAutoPause
        reset()
        trackingStartMs = System.currentTimeMillis()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val looper = locationLooper ?: Looper.getMainLooper()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, looper)
    }

    fun pauseTracking(manual: Boolean = true) {
        if (!_isTracking.value || _isPaused.value) return
        _isPaused.value = true
        _isAutoPaused.value = !manual
        pauseStartMs = System.currentTimeMillis()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun resumeTracking() {
        if (!_isTracking.value || !_isPaused.value) return
        _isPaused.value = false
        _isAutoPaused.value = false
        totalPausedMs += System.currentTimeMillis() - pauseStartMs
        lastLocation = null  // avoid jump on resume
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val looper = locationLooper ?: Looper.getMainLooper()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, looper)
    }

    fun stopTracking() {
        _isTracking.value = false
        _isPaused.value = false
        _isAutoPaused.value = false
        autoPauseEnabled = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        publishRoutePoints(force = true)
    }

    private fun reset() {
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

    private fun appendRoutePoint(point: Pair<Double, Double>) {
        routeBuffer.add(point)
        publishRoutePoints(force = false)
    }

    private fun publishRoutePoints(force: Boolean) {
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

    private fun processLocation(location: Location) {
        // GPS signal quality
        _isGPSSignalWeak.value = location.accuracy > 20f

        // Route — O(1) append, throttled UI publish
        appendRoutePoint(Pair(location.latitude, location.longitude))

        // Distance
        val prev = lastLocation
        if (prev != null) {
            val delta = prev.distanceTo(location)
            if (delta > 0 && delta < 200) { // ignore teleports
                totalDistanceM += delta
                _distanceKm.value = totalDistanceM / 1000.0
            }
        }
        lastLocation = location

        // Speed
        val speedMs = if (location.hasSpeed()) location.speed else 0f
        val speedKmh = speedMs * 3.6
        _currentSpeedKmh.value = speedKmh

        // Average speed
        val elapsedMs = (System.currentTimeMillis() - trackingStartMs - totalPausedMs).coerceAtLeast(1L)
        val avgSpeed = (totalDistanceM / 1000.0) / (elapsedMs / 3_600_000.0)
        _averageSpeedKmh.value = avgSpeed
        _paceMinPerKm.value = if (avgSpeed > 0) 60.0 / avgSpeed else 0.0

        // Altitude gain
        if (location.hasAltitude()) {
            val alt = location.altitude
            val prev2 = lastAltitude
            if (prev2 != null && alt > prev2) {
                _altitudeGainM.value += alt - prev2
            }
            lastAltitude = alt
        }

        // Auto-pause (yalnızca antrenman başladıktan sonra)
        if (autoPauseEnabled && speedKmh < autoPauseThreshold) {
            lowSpeedCount++
            if (lowSpeedCount >= 3 && !_isPaused.value) pauseTracking(manual = false)
        } else if (speedKmh >= autoResumeThreshold && _isPaused.value) {
            lowSpeedCount = 0
            resumeTracking()
        } else {
            lowSpeedCount = 0
        }

        // Per-km splits
        val currentKm = (totalDistanceM / 1000.0).toInt()
        if (currentKm > 0 && totalDistanceM - lastSplitDistanceM >= 1000.0) {
            val splitElapsedMs = System.currentTimeMillis() - trackingStartMs - totalPausedMs
            val splitDistanceKm = (totalDistanceM - lastSplitDistanceM) / 1000.0
            val splitPace = if (splitDistanceKm > 0) (splitElapsedMs / 60_000.0) / splitDistanceKm else 0.0
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
}
