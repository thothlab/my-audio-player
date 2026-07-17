package tech.thothlab.dombra.core

private fun jsDateNow(): Double = js("Date.now()")

private fun jsNavigatorLanguage(): String = js("navigator.language")

internal actual fun systemNowMs(): Long = jsDateNow().toLong()

internal actual fun systemLocaleTag(): String = jsNavigatorLanguage()

internal actual val platformName: String = "web-wasm"
