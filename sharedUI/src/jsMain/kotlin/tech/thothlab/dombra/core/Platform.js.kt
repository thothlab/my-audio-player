package tech.thothlab.dombra.core

import kotlinx.browser.window

internal actual fun systemNowMs(): Long = kotlin.js.Date.now().toLong()

internal actual fun systemLocaleTag(): String = window.navigator.language

internal actual val platformName: String = "web-js"
