package ru.infra.alerts

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CheckState { OK, FAIL, CHECKING }

data class Readiness(
    val connection: CheckState = CheckState.CHECKING,

    val connectionSince: Long = 0L,
    val connectionError: String? = null,
    val notifications: Boolean = false,
    val criticalChannel: Boolean = false,
    val dnd: Boolean = false,
    val battery: Boolean = false,

    val keepWhenUnused: Boolean = false,
) {
    val allOk: Boolean
        get() = connection == CheckState.OK && settingsOk

    val settingsOk: Boolean
        get() = notifications && criticalChannel && dnd && battery && keepWhenUnused

    val hasProblems: Boolean
        get() = connection != CheckState.CHECKING && !allOk

    val connectionProblem: String
        get() = connectionError?.let { "Ошибка: $it" } ?: "Нет связи с сервером"
}

object ReadinessMonitor {
    private val _state = MutableStateFlow(Readiness())
    val state: StateFlow<Readiness> = _state.asStateFlow()

    fun refresh(context: Context) {
        val app = context.applicationContext
        Notifier.ensureCriticalBypassesDnd(app)
        _state.value = localChecks(app)

        AlertService.start(app)
    }

    fun onConnectionChanged(context: Context) {
        _state.value = localChecks(context.applicationContext)
    }

    fun reconnect(context: Context) {
        MqttAlertClient.reconnect(context)
    }

    private fun localChecks(context: Context): Readiness {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val critical = nm.getNotificationChannel(Notifier.criticalChannelId(context))
        val conn = MqttAlertClient.state.value
        return Readiness(
            connection = when (conn.status) {
                ConnStatus.CONNECTED -> CheckState.OK
                ConnStatus.CONNECTING -> CheckState.CHECKING
                ConnStatus.DISCONNECTED -> CheckState.FAIL
            },
            connectionSince = conn.since,
            connectionError = conn.error,
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            criticalChannel = critical != null && critical.importance >= NotificationManager.IMPORTANCE_HIGH,

            dnd = nm.isNotificationPolicyAccessGranted && critical?.canBypassDnd() == true,
            battery = pm.isIgnoringBatteryOptimizations(context.packageName),

            keepWhenUnused = Build.VERSION.SDK_INT < 30 || context.packageManager.isAutoRevokeWhitelisted,
        )
    }

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun criticalChannelSettingsIntent(context: Context) =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.criticalChannelId(context))

    fun dndAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun unusedAppIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT == 30) {
            Intent("android.intent.action.AUTO_REVOKE_PERMISSIONS", Uri.parse("package:${context.packageName}"))
        } else {
            appDetailsIntent(context)
        }

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
