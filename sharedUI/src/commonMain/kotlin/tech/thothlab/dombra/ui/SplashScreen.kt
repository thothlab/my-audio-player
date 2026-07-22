package tech.thothlab.dombra.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dombra.sharedui.generated.resources.Res
import dombra.sharedui.generated.resources.dombra_gold
import org.jetbrains.compose.resources.painterResource
import tech.thothlab.dombra.i18n.LocalStrings
import kotlin.math.sqrt

/** Золото знака и текста на сплеше. */
private val SplashGold = Color(0xFFE9C877)

/**
 * Брендовый сплеш (Ход 9): сплошная «слива» радиальным градиентом (центр светлее,
 * края темнее) + золотое гало, по центру — детальная золотая домбра, ниже —
 * «DOMBRA» и подпись, у нижнего края — спиннер.
 */
@Composable
fun SplashScreen() {
    val strings = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Слива: radial-gradient(circle at 50% 46%, #3d1036, #2a0b2b 42%, #160619).
                val center = Offset(size.width * 0.5f, size.height * 0.46f)
                val radius = sqrt(
                    (size.width * 0.5f) * (size.width * 0.5f) +
                        (size.height * 0.54f) * (size.height * 0.54f),
                )
                drawRect(
                    Brush.radialGradient(
                        0.0f to Color(0xFF3D1036),
                        0.42f to Color(0xFF2A0B2B),
                        1.0f to Color(0xFF160619),
                        center = center,
                        radius = radius,
                    ),
                )
                // Золотое гало за знаком.
                drawRect(
                    Brush.radialGradient(
                        0.0f to SplashGold.copy(alpha = 0.20f),
                        0.66f to Color.Transparent,
                        center = center,
                        radius = size.width * 0.55f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.dombra_gold),
            contentDescription = null,
            modifier = Modifier.size(186.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars) // не заезжать под системную панель
                .padding(bottom = 56.dp), // приподнять блок «DOMBRA · подпись · прогресс» повыше
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "DOMBRA",
                color = SplashGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.6.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                strings.splashSubtitle,
                color = SplashGold.copy(alpha = 0.5f),
                fontSize = 11.sp,
                letterSpacing = 3.0.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = SplashGold,
                trackColor = SplashGold.copy(alpha = 0.28f),
                strokeWidth = 2.5.dp,
            )
        }
    }
}
