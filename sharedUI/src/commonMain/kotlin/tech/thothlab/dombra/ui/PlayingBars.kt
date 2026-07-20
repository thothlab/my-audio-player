package tech.thothlab.dombra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Индикатор «звучания» напротив играющего трека (из макета «Все песни»): три
 * accent-полосы, анимация scaleY .3↔1 со сдвигом фаз (0 / .3s / .6s). Пока играет —
 * анимируется; на паузе полосы застывают (это про «звук», а не про «выбран»).
 */
@Composable
fun PlayingBars(
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val fractions: List<Float> = if (playing) {
        val transition = rememberInfiniteTransition(label = "eq")
        listOf(0, 300, 600).map { offsetMs ->
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMs),
                ),
                label = "bar$offsetMs",
            ).value
        }
    } else {
        listOf(0.5f, 0.85f, 0.65f) // застывшие полосы на паузе
    }
    Row(
        modifier = modifier.height(14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        fractions.forEach { f ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}
