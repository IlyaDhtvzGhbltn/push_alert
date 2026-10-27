package ru.infra.alerts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Severity(val label: String) {
    CRITICAL("Авария"),
    WARNING("Предупреждение"),
    INFO("Инфо"),
    RESOLVED("Восстановлено");

    companion object {
        fun parse(value: String?): Severity = when (value?.lowercase()) {
            "critical", "disaster", "high", "crit" -> CRITICAL
            "warning", "warn", "average" -> WARNING
            "resolved", "ok", "recovery" -> RESOLVED
            else -> INFO
        }
    }
}

data class Alert(
    val id: String,
    val severity: Severity,
    val title: String,
    val body: String,
    val host: String?,
    val timestamp: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("severity", severity.name)
        .put("title", title)
        .put("body", body)
        .put("host", host)
        .put("ts", timestamp)

    companion object {
        fun fromJson(o: JSONObject) = Alert(
            id = o.getString("id"),
            severity = Severity.valueOf(o.getString("severity")),
            title = o.getString("title"),
            body = o.optString("body"),
            host = if (o.isNull("host")) null else o.optString("host"),
            timestamp = o.getLong("ts"),
        )
    }
}

class AlertStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    private val _alerts = MutableStateFlow(load())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    val mqttClientId: String =
        prefs.getString(KEY_CLIENT_ID, null) ?: ("redalert-" + java.util.UUID.randomUUID()).also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }

    var wizardDone: Boolean
        get() = prefs.getBoolean(KEY_WIZARD_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_WIZARD_DONE, value).apply()

    private fun load(): List<Alert> {
        val arr = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        return (0 until arr.length()).map { Alert.fromJson(arr.getJSONObject(it)) }
    }

    @Synchronized
    fun add(alert: Alert) {
        val items = (listOf(alert) + _alerts.value).take(MAX_ITEMS)
        val arr = JSONArray().apply { items.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
        _alerts.value = items
    }

    @Synchronized
    fun addIfNew(alert: Alert): Boolean {
        val exists = _alerts.value.any {
            it.id == alert.id && it.severity == alert.severity && it.timestamp == alert.timestamp
        }
        if (!exists) add(alert)
        return !exists
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ITEMS).apply()
        _alerts.value = emptyList()
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_CLIENT_ID = "mqtt_client_id"
        private const val KEY_WIZARD_DONE = "wizard_done"
        private const val MAX_ITEMS = 200

        @Volatile
        private var instance: AlertStore? = null

        fun get(context: Context): AlertStore =
            instance ?: synchronized(this) {
                instance ?: AlertStore(context.applicationContext).also { instance = it }
            }
    }
}
