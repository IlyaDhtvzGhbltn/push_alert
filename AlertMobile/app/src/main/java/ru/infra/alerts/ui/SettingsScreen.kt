package ru.infra.alerts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.infra.alerts.CheckState
import ru.infra.alerts.Readiness
import ru.infra.alerts.ReadinessMonitor
import ru.infra.alerts.sendTestAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    readiness: Readiness,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val requestNotifications = rememberNotificationRequest()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Готовность к приёму аварий")
            StatusRow(
                title = "Уведомления",
                state = readiness.notifications.toState(),
                problem = "Уведомления запрещены",
                fixLabel = "Разрешить",
                onClick = requestNotifications,
            )
            StatusRow(
                title = "Громкий канал аварий",
                state = readiness.criticalChannel.toState(),
                problem = "Канал выключен или стал тихим",
                fixLabel = "Настроить",
                okSubtitle = "Мелодия, громкость, вибрация",
                onClick = { context.startActivity(ReadinessMonitor.criticalChannelSettingsIntent(context)) },
            )
            StatusRow(
                title = "Пробивает «Не беспокоить»",
                state = readiness.dnd.toState(),
                problem = "Нет доступа к режиму «Не беспокоить»",
                fixLabel = "Разрешить",
                onClick = { context.startActivity(ReadinessMonitor.dndAccessIntent()) },
            )
            StatusRow(
                title = "Батарея: без ограничений для работы в фоне",
                state = readiness.battery.toState(),
                problem = "Без этого Android может разрывать соединение во сне",
                fixLabel = "Разрешить",

                onClick = {
                    context.startActivity(
                        if (readiness.battery) ReadinessMonitor.appDetailsIntent(context)
                        else ReadinessMonitor.batteryIntent(context)
                    )
                },
            )
            StatusRow(
                title = "Не отключать при неиспользовании",
                state = readiness.keepWhenUnused.toState(),
                problem = "Выключите «Manage app if unused», иначе Android может отобрать разрешения",
                fixLabel = "Выключить",
                onClick = { context.startActivity(ReadinessMonitor.unusedAppIntent(context)) },
            )

            HorizontalDivider()
            SectionTitle("Инструменты")
            ToolRow("Проверить алерт") { sendTestAlert(context) }
            ToolRow("Переподключиться к серверу") { ReadinessMonitor.reconnect(context) }
        }
    }
}

private fun Boolean.toState() = if (this) CheckState.OK else CheckState.FAIL

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatusRow(
    title: String,
    state: CheckState,
    problem: String,
    fixLabel: String,

    onClick: () -> Unit,
    okSubtitle: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = when {
            state == CheckState.FAIL -> {
                { Text(problem) }
            }
            state == CheckState.OK && okSubtitle != null -> {
                { Text(okSubtitle) }
            }
            else -> null
        },
        leadingContent = {
            when (state) {
                CheckState.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "OK", tint = OkGreen)
                CheckState.FAIL -> Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Проблема",
                    tint = MaterialTheme.colorScheme.error,
                )
                CheckState.CHECKING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        },
        trailingContent = if (state == CheckState.FAIL) {
            { TextButton(onClick = onClick) { Text(fixLabel) } }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ToolRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
