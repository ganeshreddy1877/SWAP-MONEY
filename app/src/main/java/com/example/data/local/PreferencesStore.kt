package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.domain.AppPreferences
import com.example.domain.BackendMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.swapMoneyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "swap_money_prefs"
)

class PreferencesStore(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NEARBY_ALERTS_ENABLED = booleanPreferencesKey("nearby_alerts_enabled")
        val SECURITY_ALERTS_ENABLED = booleanPreferencesKey("security_alerts_enabled")
        val SHARE_APPROX_LOCATION = booleanPreferencesKey("share_approx_location")
        val LANGUAGE = stringPreferencesKey("language")
        val BACKEND_MODE = stringPreferencesKey("backend_mode")
        val FASTAPI_BASE_URL = stringPreferencesKey("fastapi_base_url")
    }

    val preferencesFlow: Flow<AppPreferences> = context.swapMoneyDataStore.data.map { prefs ->
        val modeName = prefs[Keys.BACKEND_MODE] ?: BackendMode.COMPLIANCE_SANDBOX.name
        val mode = runCatching { BackendMode.valueOf(modeName) }
            .getOrDefault(BackendMode.COMPLIANCE_SANDBOX)
        AppPreferences(
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            nearbyAlertsEnabled = prefs[Keys.NEARBY_ALERTS_ENABLED] ?: true,
            securityAlertsEnabled = prefs[Keys.SECURITY_ALERTS_ENABLED] ?: true,
            shareApproximateLocation = prefs[Keys.SHARE_APPROX_LOCATION] ?: true,
            language = prefs[Keys.LANGUAGE] ?: "English (India)",
            backendMode = mode,
            fastApiBaseUrl = prefs[Keys.FASTAPI_BASE_URL] ?: BuildConfig.DEFAULT_FASTAPI_BASE_URL
        )
    }

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        context.swapMoneyDataStore.edit { prefs ->
            val currentModeName = prefs[Keys.BACKEND_MODE] ?: BackendMode.COMPLIANCE_SANDBOX.name
            val current = AppPreferences(
                darkMode = prefs[Keys.DARK_MODE] ?: false,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                nearbyAlertsEnabled = prefs[Keys.NEARBY_ALERTS_ENABLED] ?: true,
                securityAlertsEnabled = prefs[Keys.SECURITY_ALERTS_ENABLED] ?: true,
                shareApproximateLocation = prefs[Keys.SHARE_APPROX_LOCATION] ?: true,
                language = prefs[Keys.LANGUAGE] ?: "English (India)",
                backendMode = runCatching { BackendMode.valueOf(currentModeName) }
                    .getOrDefault(BackendMode.COMPLIANCE_SANDBOX),
                fastApiBaseUrl = prefs[Keys.FASTAPI_BASE_URL] ?: BuildConfig.DEFAULT_FASTAPI_BASE_URL
            )
            val updated = transform(current)
            prefs[Keys.DARK_MODE] = updated.darkMode
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.NEARBY_ALERTS_ENABLED] = updated.nearbyAlertsEnabled
            prefs[Keys.SECURITY_ALERTS_ENABLED] = updated.securityAlertsEnabled
            prefs[Keys.SHARE_APPROX_LOCATION] = updated.shareApproximateLocation
            prefs[Keys.LANGUAGE] = updated.language
            prefs[Keys.BACKEND_MODE] = updated.backendMode.name
            prefs[Keys.FASTAPI_BASE_URL] = updated.fastApiBaseUrl
        }
    }
}
