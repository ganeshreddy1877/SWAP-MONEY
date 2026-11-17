package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) {
                _isOnline.value = true
            }
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun checkCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                return true
            }
        }
        return cm.activeNetworkInfo?.isConnected == true
    }

    fun setSimulatedOfflineForTesting(offline: Boolean) {
        _isOnline.value = !offline
    }
}
