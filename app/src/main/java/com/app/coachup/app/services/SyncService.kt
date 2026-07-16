package com.app.coachup.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Android equivalent of iOS SyncService.
 *
 * Uses [ConnectivityManager.NetworkCallback] to mirror iOS NWPathMonitor behaviour.
 * Triggers [OfflineService]-assisted data sync whenever connectivity is restored.
 *
 * Must be initialised with a [Context] via [startMonitoring] before use.
 */
object SyncService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var offlineService: OfflineService? = null

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    /**
     * Call once from Application.onCreate().
     */
    fun startMonitoring(context: Context, offlineService: OfflineService) {
        this.offlineService = offlineService

        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check initial state
        val activeNetwork = connectivityManager!!.activeNetwork
        val capabilities = connectivityManager!!.getNetworkCapabilities(activeNetwork)
        _isConnected.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        // Register callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
                syncDataIfNeeded()
            }

            override fun onLost(network: Network) {
                _isConnected.value = false
            }
        }

        connectivityManager!!.registerNetworkCallback(request, networkCallback!!)

        // Initial sync if connected
        if (_isConnected.value) {
            syncDataIfNeeded()
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    // -------------------------------------------------------------------------
    // Sync Logic
    // -------------------------------------------------------------------------

    fun syncDataIfNeeded() {
        if (!_isConnected.value || _isSyncing.value) return

        // Only sync if last sync was more than 5 minutes ago (300 000 ms)
        val last = _lastSyncTime.value
        if (last != null && System.currentTimeMillis() - last < 300_000L) return

        scope.launch { performSync() }
    }

    private suspend fun performSync() {
        _isSyncing.value = true
        try {
            offlineService?.syncAll()
            _lastSyncTime.value = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log but don't propagate — sync is best-effort
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun forceSyncAll() {
        performSync()
    }
}
