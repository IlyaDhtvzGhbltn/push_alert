package ru.infra.alerts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

enum class ConnStatus { CONNECTING, CONNECTED, DISCONNECTED }

data class ConnState(
    val status: ConnStatus = ConnStatus.CONNECTING,

    val since: Long = System.currentTimeMillis(),
    val error: String? = null,
)

object MqttAlertClient {
    private const val TAG = "RedAlert"
    private const val RETRY_SECONDS = 30L

    private val _state = MutableStateFlow(ConnState())
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val retry = Executors.newSingleThreadScheduledExecutor()
    private var client: MqttAsyncClient? = null
    private var connecting = false

    @Synchronized
    fun ensureConnected(context: Context) {
        val app = context.applicationContext
        val c = client ?: create(app).also { client = it }
        if (c.isConnected || connecting) return
        connecting = true
        setState(app, ConnStatus.CONNECTING, null)
        try {
            c.connect(options(app), null, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) {
                    synchronized(this@MqttAlertClient) { connecting = false }
                }

                override fun onFailure(token: IMqttToken?, e: Throwable?) {
                    synchronized(this@MqttAlertClient) { connecting = false }
                    Log.e(TAG, "MQTT connect failed", e)
                    setState(app, ConnStatus.DISCONNECTED, e.describe())
                    retry.schedule(Runnable { ensureConnected(app) }, RETRY_SECONDS, TimeUnit.SECONDS)
                }
            })
        } catch (e: Exception) {
            connecting = false
            Log.e(TAG, "MQTT connect error", e)
            setState(app, ConnStatus.DISCONNECTED, e.describe())
            retry.schedule(Runnable { ensureConnected(app) }, RETRY_SECONDS, TimeUnit.SECONDS)
        }
    }

    fun reconnect(context: Context) {
        val app = context.applicationContext
        retry.execute {
            synchronized(this) {
                runCatching { client?.disconnectForcibly(1_000, 1_000) }
                runCatching { client?.close() }
                client = null
                connecting = false
            }
            ensureConnected(app)
        }
    }

    private fun create(context: Context): MqttAsyncClient {
        val uri = "ssl://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"

        val c = MqttAsyncClient(uri, AlertStore.get(context).mqttClientId, MemoryPersistence())
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i(TAG, "MQTT connected (reconnect=$reconnect)")
                setState(context, ConnStatus.CONNECTED, null)
                runCatching { c.subscribe(BuildConfig.MQTT_TOPIC, 1) }
                    .onFailure { Log.e(TAG, "MQTT subscribe failed", it) }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "MQTT connection lost", cause)
                setState(context, ConnStatus.DISCONNECTED, cause.describe())
            }

            override fun messageArrived(topic: String?, message: MqttMessage) {
                runCatching { handle(context, String(message.payload, Charsets.UTF_8)) }
                    .onFailure { Log.e(TAG, "Bad alert message", it) }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        return c
    }

    private fun options(context: Context) = MqttConnectOptions().apply {
        isCleanSession = false
        isAutomaticReconnect = true
        maxReconnectDelay = 60_000
        keepAliveInterval = 60
        connectionTimeout = 15
        userName = BuildConfig.MQTT_USERNAME
        password = BuildConfig.MQTT_PASSWORD.toCharArray()
        socketFactory = brokerSocketFactory(context)
    }

    private fun handle(context: Context, json: String) {
        val o = JSONObject(json)
        val alert = Alert(
            id = o.getString("id"),
            severity = Severity.parse(o.optString("severity")),
            title = o.getString("title"),
            body = if (o.isNull("body")) "" else o.optString("body"),
            host = if (o.isNull("host")) null else o.optString("host").ifEmpty { null },
            timestamp = if (o.has("ts")) o.getLong("ts") * 1000 else System.currentTimeMillis(),
        )
        if (AlertStore.get(context).addIfNew(alert)) Notifier.show(context, alert)
    }

    private fun brokerSocketFactory(context: Context): SSLSocketFactory {
        val cert = context.resources.openRawResource(R.raw.broker).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("broker", cert)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        return SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
    }

    private fun setState(context: Context, status: ConnStatus, error: String?) {
        val current = _state.value
        if (current.status != status || current.error != error) {
            _state.value = ConnState(status, System.currentTimeMillis(), error)
        }
        Notifier.updateServiceNotification(context, _state.value)
        ReadinessMonitor.onConnectionChanged(context)
    }

    private fun Throwable?.describe(): String? =
        this?.let { (it.cause?.message ?: it.message ?: it.javaClass.simpleName) }
}
