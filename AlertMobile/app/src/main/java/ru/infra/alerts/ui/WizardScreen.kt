package ru.infra.alerts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.infra.alerts.CheckState
import ru.infra.alerts.Readiness
import ru.infra.alerts.ReadinessMonitor
import ru.infra.alerts.sendTestAlert

private enum class Step { NOTIFICATIONS, BATTERY, DND, UNUSED, DONE }

private data class Fix(val title: String, val reason: String, val label: String, val action: () -> Unit)

@Composable
fun WizardScreen(readiness: Readiness, onFinish: () -> Unit) {
    val context = LocalContext.current
    val requestNotifications = rememberNotificationRequest()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[index]
    val next: () -> Unit = { if (index < Step.entries.lastIndex) index++ else onFinish() }

    val fixes = buildList {
        if (readiness.connection == CheckState.FAIL) {
            add(Fix("Связь с сервером", readiness.connectionProblem, "Повторить") {
                ReadinessMonitor.reconnect(context)
            })
        }
        if (!readiness.notifications) {
            add(Fix("Уведомления", "Уведомления запрещены", "Разрешить", requestNotifications))
        }
        if (!readiness.criticalChannel) {
            add(Fix("Громкий канал аварий", "Канал выключен или стал тихим", "Настроить") {
                context.startActivity(ReadinessMonitor.criticalChannelSettingsIntent(context))
            })
        }
        if (!readiness.dnd) {
            add(Fix("Пробивает «Не беспокоить»", "Нет доступа к режиму «Не беспокоить»", "Разрешить") {
                context.startActivity(ReadinessMonitor.dndAccessIntent())
            })
        }
        if (!readiness.battery) {
            add(Fix("Батарея: без ограничений для работы в фоне", "Без этого Android может разрывать соединение во сне", "Разрешить") {
                context.startActivity(ReadinessMonitor.batteryIntent(context))
            })
        }
        if (!readiness.keepWhenUnused) {
            add(Fix("Не отключать при неиспользовании", "Android может отобрать разрешения", "Выключить") {
                context.startActivity(ReadinessMonitor.unusedAppIntent(context))
            })
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            LinearProgressIndicator(
                progress = { (index + 1f) / Step.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Шаг ${index + 1} из ${Step.entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            when (step) {
                Step.NOTIFICATIONS -> StepContent(
                    icon = Icons.Filled.Notifications,
                    title = "Уведомления",
                    text = "Разрешите приложению показывать уведомления — иначе об аварии вы не узнаете.",
                    done = readiness.notifications,
                    doneText = "Уведомления разрешены",
                    actionLabel = "Разрешить",
                    onAction = requestNotifications,
                    onNext = next,
                )
                Step.BATTERY -> StepContent(
                    icon = Icons.Filled.Refresh,
                    title = "Батарея: без ограничений для работы в фоне",
                    text = "Приложение держит постоянную связь с сервером. Чтобы телефон не разрывал её во сне, " +
                        "разрешите работу в фоне.",
                    hint = "На Xiaomi, Huawei, Honor, Samsung также включите «Автозапуск» и режим батареи " +
                        "«Без ограничений» для этого приложения.",
                    done = readiness.battery,
                    doneText = "Работа в фоне разрешена",
                    actionLabel = "Разрешить",
                    onAction = { context.startActivity(ReadinessMonitor.batteryIntent(context)) },
                    secondaryLabel = "Настройки приложения",
                    onSecondary = { context.startActivity(ReadinessMonitor.appDetailsIntent(context)) },
                    onNext = next,
                )
                Step.DND -> StepContent(
                    icon = Icons.Filled.Warning,
                    title = "Режим «Не беспокоить»",
                    text = "Аварии должны звучать и ночью. Найдите в списке это приложение и включите доступ.",
                    done = readiness.dnd,
                    doneText = "Аварии пробивают «Не беспокоить»",
                    actionLabel = "Открыть настройки",
                    onAction = { context.startActivity(ReadinessMonitor.dndAccessIntent()) },
                    onNext = next,
                )
                Step.UNUSED -> StepContent(
                    icon = Icons.Filled.Lock,
                    title = "Не отключать при неиспользовании",
                    text = "Аварийное приложение открывают редко. Чтобы Android не отобрал у него разрешения, " +
                        "выключите «Manage app if unused» (внизу карточки приложения).",
                    done = readiness.keepWhenUnused,
                    doneText = "Разрешения не будут отобраны",
                    actionLabel = "Открыть настройки",
                    onAction = { context.startActivity(ReadinessMonitor.unusedAppIntent(context)) },
                    onNext = next,
                )
                Step.DONE -> StepContent(
                    icon = if (readiness.allOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    iconTint = if (readiness.allOk) OkGreen else MaterialTheme.colorScheme.error,
                    title = if (readiness.allOk) "Всё готово" else "Не всё готово",
                    text = if (readiness.allOk) {
                        "Приложение можно закрыть — алерты придут сами. Проверьте, как звучит авария."
                    } else {
                        "Пока это не исправлено, алерты могут не приходить."
                    },
                    fixes = fixes,
                    done = false,
                    doneText = "",
                    actionLabel = "Проверить алерт",
                    onAction = { sendTestAlert(context) },
                    nextLabel = "Начать",
                    onNext = next,
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    icon: ImageVector,
    title: String,
    text: String,
    done: Boolean,
    doneText: String,
    actionLabel: String,
    onAction: () -> Unit,
    onNext: () -> Unit,
    hint: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    nextLabel: String? = null,
    fixes: List<Fix> = emptyList(),
    iconTint: Color? = null,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint ?: if (done) OkGreen else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        hint?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fixes.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    fixes.forEach { fix ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fix.title, fontWeight = FontWeight.Bold)
                                Text(fix.reason, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = fix.action) { Text(fix.label) }
                        }
                    }
                }
            }
        }
        if (done) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OkGreen)
                Spacer(Modifier.width(8.dp))
                Text(doneText, color = OkGreen, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nextLabel != null) {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(nextLabel) }
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            } else if (done) {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Далее") }
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                        Text(secondaryLabel)
                    }
                }
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Пропустить") }
            }
        }
    }
}
