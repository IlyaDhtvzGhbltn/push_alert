package ru.infra.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    const val CHANNEL_WARNING = "alerts_warning"
    const val CHANNEL_INFO = "alerts_info"
    const val CHANNEL_RESOLVED = "alerts_resolved"
    const val CHANNEL_SERVICE = "alerts_service"
    const val SERVICE_NOTIFICATION_ID = 1

    private const val LEGACY_CHANNEL_CRITICAL = "alerts_critical"
    private const val PREFS = "notifier"
    private const val KEY_CRITICAL_VERSION = "critical_version"
    private const val KEY_RECREATED_WITH_DND = "critical_recreated_with_dnd"

    fun criticalChannelId(context: Context): String =
        "alerts_critical_v" + prefs(context).getInt(KEY_CRITICAL_VERSION, 1)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_CRITICAL)

        val warning = NotificationChannel(
            CHANNEL_WARNING, "Предупреждения", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val info = NotificationChannel(
            CHANNEL_INFO, "Информация", NotificationManager.IMPORTANCE_DEFAULT
        )

        val resolved = NotificationChannel(
            CHANNEL_RESOLVED, "Восстановления", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE, "Соединение с сервером", NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        nm.createNotificationChannels(
            listOf(criticalChannel(criticalChannelId(context)), warning, info, resolved, service)
        )
        ensureCriticalBypassesDnd(context)
    }

    fun ensureCriticalBypassesDnd(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val prefs = prefs(context)
        val channel = nm.getNotificationChannel(criticalChannelId(context)) ?: return
        if (channel.canBypassDnd() || !nm.isNotificationPolicyAccessGranted) return
        if (prefs.getBoolean(KEY_RECREATED_WITH_DND, false)) return

        nm.deleteNotificationChannel(channel.id)
        val version = prefs.getInt(KEY_CRITICAL_VERSION, 1) + 1
        prefs.edit()
            .putInt(KEY_CRITICAL_VERSION, version)
            .putBoolean(KEY_RECREATED_WITH_DND, true)
            .apply()
        nm.createNotificationChannel(criticalChannel("alerts_critical_v$version"))
    }

    private fun criticalChannel(id: String) = NotificationChannel(
        id, "Аварии (критичные)", NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Громкий сигнал, пробивает режим «Не беспокоить»"
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
        enableLights(true)
        setBypassDnd(true)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun serviceNotification(context: Context, state: ConnState): Notification {
        val text = when (state.status) {
            ConnStatus.CONNECTED -> "На связи с сервером"
            ConnStatus.CONNECTING -> "Подключение к серверу…"
            ConnStatus.DISCONNECTED -> "Нет связи с сервером"
        }
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    fun updateServiceNotification(context: Context, state: ConnState) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return
        try {
            nmc.notify(SERVICE_NOTIFICATION_ID, serviceNotification(context, state))
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun show(context: Context, alert: Alert) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return

        val channel = when (alert.severity) {
            Severity.CRITICAL -> criticalChannelId(context)
            Severity.WARNING -> CHANNEL_WARNING
            Severity.INFO -> CHANNEL_INFO
            Severity.RESOLVED -> CHANNEL_RESOLVED
        }
        val prefix = when (alert.severity) {
            Severity.CRITICAL -> "🔴 "
            Severity.WARNING -> "🟠 "
            Severity.RESOLVED -> "✅ "
            Severity.INFO -> ""
        }
        val text = listOfNotNull(alert.host, alert.body.takeIf { it.isNotBlank() }).joinToString(": ")

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(prefix + alert.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(alert.timestamp)
            .setShowWhen(true)
            .setCategory(
                if (alert.severity == Severity.CRITICAL) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_STATUS
            )
            .setPriority(
                if (alert.severity == Severity.CRITICAL) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        if (alert.severity == Severity.CRITICAL) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        try {
            nmc.notify(alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
