package ru.infra.alerts

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.infra.alerts.ui.AlertsScreen
import ru.infra.alerts.ui.RedAlertTheme
import ru.infra.alerts.ui.SettingsScreen
import ru.infra.alerts.ui.WizardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = AlertStore.get(this)
        setContent {
            RedAlertTheme { AppRoot(store) }
        }
    }

    override fun onResume() {
        super.onResume()

        ReadinessMonitor.refresh(this)
    }
}

private enum class Screen { WIZARD, ALERTS, SETTINGS }

@Composable
private fun AppRoot(store: AlertStore) {
    var screen by rememberSaveable { mutableStateOf(if (store.wizardDone) Screen.ALERTS else Screen.WIZARD) }
    val readiness by ReadinessMonitor.state.collectAsStateWithLifecycle()
    val alerts by store.alerts.collectAsStateWithLifecycle()

    when (screen) {
        Screen.WIZARD -> WizardScreen(
            readiness = readiness,
            onFinish = {
                store.wizardDone = true
                screen = Screen.ALERTS
            },
        )
        Screen.ALERTS -> AlertsScreen(
            alerts = alerts,
            readiness = readiness,
            onOpenSettings = { screen = Screen.SETTINGS },
            onClearHistory = store::clear,
        )
        Screen.SETTINGS -> SettingsScreen(
            readiness = readiness,
            onBack = { screen = Screen.ALERTS },
        )
    }

    BackHandler(enabled = screen == Screen.SETTINGS) { screen = Screen.ALERTS }
}

fun sendTestAlert(context: Context) {
    val alert = Alert(
        id = "local-test",
        severity = Severity.CRITICAL,
        title = "Тестовый алерт",
        body = "Так выглядит авария. Смахните уведомление, чтобы остановить звук.",
        host = "test-host",
        timestamp = System.currentTimeMillis(),
    )
    AlertStore.get(context).add(alert)
    Notifier.show(context, alert)
}
