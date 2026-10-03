package com.kitconn.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitconn.shared.core.CountryCode
import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.presentation.MainAction
import com.kitconn.shared.presentation.MainUiState
import com.kitconn.shared.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────── Сплэш: кот увеличивается и подмигивает ─────────────

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    val wink = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(350, easing = LinearEasing)) }
        launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        delay(750)
        // Экран живёт, пока грузятся серверы, поэтому подмигиваем по кругу
        while (true) {
            wink.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
            delay(140)
            wink.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            delay(1900)
        }
    }

    Box(Modifier.fillMaxSize().background(KitColors.Background), contentAlignment = Alignment.Center) {
        CatMark(
            modifier = Modifier.size(200.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
            wink = wink.value,
        )
    }
}

// ───────────── Требуется обновление ─────────────

@Composable
fun UpdateRequiredScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(KitColors.Background).safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CatMark(Modifier.size(110.dp))
        Spacer(Modifier.height(20.dp))
        Text("Нужно обновление", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "Версия приложения устарела и больше не поддерживается сервером. Обновите KitConn VPN.",
            color = KitColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Проверить снова") }
    }
}

// ───────────── Главный экран ─────────────

@Composable
fun MainScreen(state: MainUiState, onAction: (MainAction) -> Unit) {
    val connected = state.vpnState == VpnState.CONNECTED
    val isOn = state.vpnState != VpnState.DISCONNECTED

    Column(
        Modifier.fillMaxSize().background(KitColors.Background).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Header(state)

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Connecting Time", style = MaterialTheme.typography.titleMedium, color = KitColors.Muted)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (connected) formatDuration(state.durationSeconds) else "00:00:00",
                fontSize = 56.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White,
            )
            Spacer(Modifier.height(28.dp))
            Row(Modifier.width(260.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isOn) KitColors.Off else Color.White)
                Text("On", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isOn) Color.White else KitColors.Off)
            }
            VpnKnob(state.vpnState, onToggle = { onAction(MainAction.ToggleVpn) })
            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = KitColors.Danger, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.clickable { onAction(MainAction.DismissError) })
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SpeedColumn("Download", "↓", if (connected) speedText(state.downBytesPerSec) else "0 KB/s", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(48.dp).background(Color(0xFF262A3E)))
                SpeedColumn("Upload", "↑", if (connected) speedText(state.upBytesPerSec) else "0 KB/s", Modifier.weight(1f))
            }
            ServerButton(state) {
                if (state.configs.isNotEmpty()) onAction(MainAction.SetServersVisible(true)) else onAction(MainAction.Refresh)
            }
        }
    }

    if (state.showServers) {
        ServersSheet(state, onAction)
    }
}

@Composable
private fun Header(state: MainUiState) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("KitConn VPN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Версия: v${state.versionName}", style = MaterialTheme.typography.bodySmall, color = KitColors.Muted)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF161824), border = BorderStroke(1.dp, Color(0xFF262A3E))) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(8.dp).background(
                        when (state.vpnState) {
                            VpnState.CONNECTED -> KitColors.Green
                            VpnState.CONNECTING -> KitColors.Cyan
                            VpnState.DISCONNECTED -> KitColors.Off
                        },
                        CircleShape,
                    ),
                )
                Text(
                    when (state.vpnState) {
                        VpnState.CONNECTED -> "Подключено"
                        VpnState.CONNECTING -> "Подключение"
                        VpnState.DISCONNECTED -> "Не подключено"
                    },
                    style = MaterialTheme.typography.labelSmall, color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SpeedColumn(title: String, arrow: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(arrow, color = KitColors.Muted, fontSize = 14.sp)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = KitColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun ServerButton(state: MainUiState, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp), color = KitColors.Card,
        border = BorderStroke(1.dp, KitColors.CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val s = state.selected
                if (s != null) FlagView(s.country, size = 36.dp) else Text("🌐", fontSize = 26.sp)
                Column {
                    Text(s?.displayName ?: "Выберите локацию", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(s?.subtitle?.ifEmpty { null } ?: "Нажмите для выбора страны", style = MaterialTheme.typography.bodySmall, color = KitColors.Muted)
                }
            }
            Text("▼", color = KitColors.Muted, fontSize = 12.sp)
        }
    }
}

// ───────────── Список серверов: стеклянные карточки ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServersSheet(state: MainUiState, onAction: (MainAction) -> Unit) {
    // Раскрываем сразу на всю высоту и не залезаем под статус-бар
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(MainAction.SetServersVisible(false)) },
        sheetState = sheetState,
        modifier = Modifier.statusBarsPadding(),
        containerColor = KitColors.Background,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Список серверов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Surface(
                    onClick = { onAction(MainAction.PingAll) }, shape = CircleShape, color = Color(0xFF222638),
                    border = BorderStroke(1.dp, Color(0xFF323850)),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.isPinging) CircularProgressIndicator(Modifier.size(14.dp), color = KitColors.Cyan, strokeWidth = 2.dp) else Text("⏱️", fontSize = 14.sp)
                        Text(if (state.isPinging) "Замер..." else "Тест пинга", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.configs, key = { it.config }) { item ->
                    ServerCard(item, item == state.selected, state.pings[item.config], state.isPinging) { onAction(MainAction.SelectConfig(item)) }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(item: VpnConfig, selected: Boolean, ping: Int?, pinging: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF181B28), Color(0xFF10121B))))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) KitColors.Green else Color(0xFF232739), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                FlagView(item.country, size = 44.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ping == null && pinging) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = KitColors.Cyan, strokeWidth = 2.dp)
                    } else if (ping != null) {
                        Text(
                            if (ping > 0) "$ping мс" else "n/a",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = when (ping) {
                                in 1..250 -> Color(0xFF00E676)
                                in 251..500 -> Color(0xFFFFB300)
                                in 501..Int.MAX_VALUE -> KitColors.Danger
                                else -> KitColors.Muted
                            },
                        )
                    }
                    Text(
                        if (selected) "✓" else "•••", color = if (selected) KitColors.Green else KitColors.Muted,
                        fontWeight = FontWeight.Bold, fontSize = if (selected) 20.sp else 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(item.subtitle.ifEmpty { "Location" }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB4BBCB))
            Text(item.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

// ───────────── Форматирование ─────────────

internal fun formatDuration(totalSeconds: Long): String {
    fun two(v: Long) = v.toString().padStart(2, '0')
    return "${two(totalSeconds / 3600)}:${two(totalSeconds % 3600 / 60)}:${two(totalSeconds % 60)}"
}

internal fun speedText(bytesPerSec: Double): String {
    val kb = bytesPerSec / 1024
    fun one(v: Double) = ((v * 10).toLong() / 10.0).toString()
    return if (kb >= 1024) "${one(kb / 1024)} MB/s" else "${one(kb)} KB/s"
}
