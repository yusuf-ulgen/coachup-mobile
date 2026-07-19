package com.app.coachup.app.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
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
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
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

    suspend fun fetchCaloriesBurned(startTime: Instant, endTime: Instant): Int? {
        val c = client ?: return null
        return try {
            val activeRequest = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val activeResponse = c.readRecords(activeRequest)
            val activeCalories = activeResponse.records.sumOf { it.energy.inKilocalories }.toInt()

            if (activeCalories > 0) {
                activeCalories
            } else {
                val totalRequest = ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                val totalResponse = c.readRecords(totalRequest)
                totalResponse.records.sumOf { it.energy.inKilocalories }.toInt().takeIf { it > 0 }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun reset() {
        _currentHeartRate.value = 0
        _averageHeartRate.value = 0
        _maxHeartRate.value = 0
    }
}
