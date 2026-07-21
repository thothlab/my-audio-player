package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import tech.thothlab.dombra.i18n.LocalStrings
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.auroraColors

private val FREQS = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private val PRESETS = mapOf(
    "Плоский" to List(10) { 0.5f },
    "Рок" to listOf(0.72f, 0.66f, 0.58f, 0.5f, 0.46f, 0.5f, 0.62f, 0.7f, 0.72f, 0.68f),
    "Джаз" to listOf(0.6f, 0.56f, 0.5f, 0.56f, 0.6f, 0.6f, 0.54f, 0.5f, 0.56f, 0.6f),
)

/**
 * Экран эквалайзера (Ход 2 · доп. экраны): переключатель вкл/выкл, пресеты,
 * 10 полос (32 Гц … 16 кГц) и предусиление. Представление; аудио-DSP (android.media.audiofx
 * / ExoPlayer audio session) — отдельная задача, пока не подключён к звуку.
 */
@Composable
fun EqualizerScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val strings = LocalStrings.current
    // PRESETS keys and the custom-preset key are stable internal IDs; localize only what is displayed.
    fun label(key: String) = when (key) {
        "Плоский" -> strings.eqFlat
        "Рок" -> strings.eqRock
        "Джаз" -> strings.eqJazz
        else -> strings.eqCustom
    }
    val bands: SnapshotStateList<Float> = remember { List(10) { 0.5f }.toMutableStateList() }
    var preamp by remember { mutableStateOf(0.62f) }
    var preset by remember { mutableStateOf("Свой") }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            // Toolbar (как на остальных экранах) + переключатель.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    tech.thothlab.dombra.theme.Symbol(tech.thothlab.dombra.theme.Sym.ChevronLeft, size = 28.dp, tint = c.textPrimary)
                }
                Text(strings.equalizer, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
                )
            }

            // Пресеты.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (PRESETS.keys + "Свой").forEach { name ->
                    val sel = name == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .then(
                                if (sel) Modifier.background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                                else Modifier.background(c.glassFillStrong).border(1.dp, c.glassBorder, RoundedCornerShape(999.dp)),
                            )
                            .clickable {
                                preset = name
                                PRESETS[name]?.let { p -> p.forEachIndexed { i, v -> bands[i] = v } }
                            }
                            .padding(horizontal = 15.dp, vertical = 7.dp),
                    ) {
                        Text(label(name), fontSize = 12.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Color.White else c.textSecondary)
                    }
                }
            }

            // Полосы.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                FREQS.forEachIndexed { i, freq ->
                    BandSlider(
                        value = bands[i],
                        freq = freq,
                        accent = accent,
                        trackColor = c.glassBorderStrong,
                        labelColor = c.textTertiary,
                        modifier = Modifier.weight(1f),
                        onValueChange = { bands[i] = it; preset = "Свой" },
                    )
                }
            }

            // Предусиление.
            Column(Modifier.padding(top = 26.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.preamp, fontSize = 13.sp, color = c.textSecondary)
                    Text("${(preamp * 24 - 12).roundToInt().let { if (it > 0) "+$it" else "$it" }} dB", fontSize = 12.sp, color = accent, fontFamily = FontFamily.Monospace)
                }
                HSlider(value = preamp, accent = accent, trackColor = c.glassBorderStrong, onValueChange = { preamp = it })
            }
        }
    }
}

/** Вертикальный слайдер одной полосы: трек 5dp, заливка снизу, белый кноб + подпись частоты. */
@Composable
private fun BandSlider(
    value: Float,
    freq: String,
    accent: Color,
    trackColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        var h by remember { mutableStateOf(1f) }
        Box(
            modifier = Modifier
                .height(150.dp)
                .width(24.dp)
                .onSizeChanged { h = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { onValueChange((1f - it.y / h).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> onValueChange((1f - change.position.y / h).coerceIn(0f, 1f)) }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(trackColor))
            Box(Modifier.width(5.dp).fillMaxHeight(value).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(accent, AuroraPurple))))
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -((value * h) - 6.5.dp.toPx()).roundToInt()) }
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Text(freq, fontSize = 8.sp, color = labelColor, fontFamily = FontFamily.Monospace)
    }
}

/** Горизонтальный слайдер (предусиление): трек 5dp + градиентная заливка + белый кноб. */
@Composable
private fun HSlider(value: Float, accent: Color, trackColor: Color, onValueChange: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onValueChange((it.x / w).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> onValueChange((change.position.x / w).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(trackColor))
        Box(Modifier.fillMaxWidth(value).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(accent, AuroraPurple))))
        Box(
            Modifier
                .offset { IntOffset((value * w - 7.dp.toPx()).roundToInt(), 0) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
