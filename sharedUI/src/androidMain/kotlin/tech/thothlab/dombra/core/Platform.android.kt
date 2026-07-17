package tech.thothlab.dombra.core

import java.util.Locale

internal actual fun systemNowMs(): Long = System.currentTimeMillis()

internal actual fun systemLocaleTag(): String = Locale.getDefault().toLanguageTag()

internal actual val platformName: String = "android"
