package tech.thothlab.dombra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.AccentColor
import tech.thothlab.dombra.domain.model.AppLanguage
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.ReplayGainMode
import tech.thothlab.dombra.domain.model.ThemeMode
import tech.thothlab.dombra.i18n.LocalStrings
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.theme.toColor

/** Настройки: выбор accent-цвета (палитра Cosmos) и темы. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    graph: AppGraph,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val remoteConfig by graph.remote.config.collectAsState()
    val c = auroraColors()
    val strings = LocalStrings.current
    var showLangPicker by remember { mutableStateOf(false) }
    fun update(block: (AppSettings) -> AppSettings) { scope.launch { graph.settings.update(block) } }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Toolbar как на остальных экранах: back-IconButton + заголовок.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(strings.settings, style = MaterialTheme.typography.headlineSmall)
            }

            Text(strings.theme, style = MaterialTheme.typography.titleMedium)
            ThemeSegmented(
                selected = settings.theme,
                onSelect = { mode -> scope.launch { graph.settings.update { it.copy(theme = mode) } } },
            )

            Text(strings.accentColor, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentColor.entries.forEach { ac ->
                    val selected = settings.accentColor == ac
                    val swatch = @Composable {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(ac.toColor())
                                .clickable { scope.launch { graph.settings.update { it.copy(accentColor = ac) } } },
                        )
                    }
                    if (selected) {
                        // Двойное кольцо по макету: зазор цвета фона + обводка onSurface.
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) { swatch() }
                    } else {
                        swatch()
                    }
                }
            }

            Text(strings.sound, style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.glassFill)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp)),
            ) {
                SoundRow(strings.replayGain, checked = settings.replayGainMode != ReplayGainMode.OFF) { on ->
                    update { it.copy(replayGainMode = if (on) ReplayGainMode.TRACK else ReplayGainMode.OFF) }
                }
                HorizontalDivider(color = c.glassBorder)
                SoundRow(
                    strings.equalizer,
                    checked = settings.equalizerEnabled,
                    onRowClick = onOpenEqualizer,
                ) { on -> update { it.copy(equalizerEnabled = on) } }
                HorizontalDivider(color = c.glassBorder)
                SoundRow(strings.sleepTimer, checked = settings.showSleepTimerButton) { on ->
                    update { it.copy(showSleepTimerButton = on) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showLangPicker = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.language, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (settings.language == AppLanguage.ENGLISH) strings.english else strings.russian,
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Symbol(Sym.ChevronRight, size = 19.dp, tint = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            }

            Text(strings.sources, style = MaterialTheme.typography.titleMedium)
            Surface(
                onClick = onOpenServer,
                shape = RoundedCornerShape(16.dp),
                color = c.glassFill,
                border = BorderStroke(1.dp, c.glassBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(Sym.Dns, Color(0xFF5B9BE8), size = 48.dp, iconSize = 24.dp)
                    Column(Modifier.weight(1f)) {
                        Text(strings.serverCard, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            remoteConfig?.let { strings.connectedTo(it.label) } ?: strings.notConnected,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Symbol(Sym.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showLangPicker) {
        LanguagePicker(
            current = settings.language,
            onSelect = { lang -> update { it.copy(language = lang) }; showLangPicker = false },
            onDismiss = { showLangPicker = false },
        )
    }
}

/** Строка секции «Звук»: подпись + переключатель; опц. тап по строке (для эквалайзера). */
@Composable
private fun SoundRow(
    title: String,
    checked: Boolean,
    onRowClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
        )
    }
}

/** Выбор языка: Русский / Английский. */
@Composable
private fun LanguagePicker(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popoverSurface,
        title = { Text(strings.language, color = c.textPrimary) },
        text = {
            Column {
                listOf(AppLanguage.RUSSIAN to strings.russian, AppLanguage.ENGLISH to strings.english).forEach { (lang, label) ->
                    val sel = lang == current || (current == AppLanguage.SYSTEM && lang == AppLanguage.RUSSIAN)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = if (sel) accent else c.textPrimary, modifier = Modifier.weight(1f))
                        if (sel) Symbol(Sym.Check, size = 20.dp, tint = accent)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

/** Сегментированный переключатель темы (Aurora Glass): выбранный сегмент — accent→фиолетовый градиент. */
@Composable
private fun ThemeSegmented(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(c.glassFill)
            .border(1.dp, c.glassBorder, RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSel = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .then(
                        if (isSel) Modifier.background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                        else Modifier,
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.themeName(mode),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSel) Color.White else c.textSecondary,
                )
            }
        }
    }
}
