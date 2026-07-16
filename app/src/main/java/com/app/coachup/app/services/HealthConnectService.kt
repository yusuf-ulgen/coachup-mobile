package com.app.coachup.app.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * Android equivalent of iOS HealthKitService — reads live heart rate from Health Connect.
 *
 * On devices without Health Connect (API < 26 or app not installed), all values
 * stay at 0 and [isAvailable] is false.
 */
object HealthConnectService {

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _averageHeartRate = MutableStateFlow(0)
    val averageHeartRate: StateFlow<Int> = _averageHeartRate.asStateFlow()

    private val _maxHeartRate = MutableStateFlow(0)
    val maxHeartRate: StateFlow<Int> = _maxHeartRate.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    private var client: HealthConnectClient? = null

    fun permissionController(): PermissionController? = client?.permissionController

    suspend fun hasAllPermissions(): Boolean {
        val c = client ?: return false
        return try {
            val granted = c.permissionController.getGrantedPermissions()
            permissions.all { it in granted }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Call once from Application.onCreate — initialises the client if available. */
    fun init(context: Context) {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                client = HealthConnectClient.getOrCreate(context)
                _isAvailable.value = true
            }
        } catch (_: Exception) {
            client = null
            _isAvailable.value = false
        }
    }

    /** Reads the most recent heart rate sample from the last 60 seconds. */
    suspend fun refreshHeartRate() {
        val c = client ?: return
        try {
            val now = Instant.now()
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(60), now)
            )
            val response = c.readRecords(request)
            val samples = response.records.flatMap { it.samples }
            if (samples.isNotEmpty()) {
                val latest = samples.last().beatsPerMinute.toInt()
                val avg = samples.map { it.beatsPerMinute }.average().toInt()
                val max = samples.maxOf { it.beatsPerMinute }.toInt()
                _currentHeartRate.value = latest
                _averageHeartRate.value = avg
                _maxHeartRate.value = max
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Permission not granted or Health Connect unavailable — stay at 0
        }
    }

    fun reset() {
        _currentHeartRate.value = 0
        _averageHeartRate.value = 0
        _maxHeartRate.value = 0
    }
}
