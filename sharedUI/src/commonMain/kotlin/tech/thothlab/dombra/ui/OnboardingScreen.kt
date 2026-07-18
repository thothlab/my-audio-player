package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.thothlab.dombra.theme.LocalAccentColor

/**
 * Простой онбординг первого запуска (PRD-03 T01): приветствие → подключение источника.
 * Кросс-платформенный (commonMain); шаг подключения предлагает выбрать локальную папку
 * или пропустить (удалённый источник Subsonic добавится в T06). iCloud-шаг — только iOS.
 */
@Composable
fun OnboardingScreen(
    onPickFolder: (() -> Unit)?,
    onFinish: () -> Unit,
) {
    val accent = LocalAccentColor.current
    var step by remember { mutableIntStateOf(0) }
    val steps = 2

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Прогресс-точки.
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(steps) { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Иконка.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = accent,
                shadowElevation = 10.dp,
                modifier = Modifier.size(100.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (step == 0) Icons.Filled.MusicNote else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = if (step == 0) "Добро пожаловать в Dombra" else "Добавьте свою музыку",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (step == 0) {
                    "Плеер для музыки без потери качества — ALAC, FLAC, MP3, Opus и другое. Локально и с вашего сервера."
                } else {
                    "Выберите папку с музыкой на устройстве. Подключить свой сервер можно позже в настройках."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (step == 1 && onPickFolder != null) {
                Spacer(Modifier.height(28.dp))
                OutlinedButton(onClick = onPickFolder) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать папку")
                }
            }

            Spacer(Modifier.weight(2f))

            // Нижние кнопки навигации.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step == 0) {
                    TextButton(onClick = onFinish) { Text("Пропустить") }
                    Button(onClick = { step = 1 }) { Text("Продолжить") }
                } else {
                    TextButton(onClick = { step = 0 }) { Text("Назад") }
                    Button(onClick = onFinish) { Text("Начать") }
                }
            }
        }
    }
}
