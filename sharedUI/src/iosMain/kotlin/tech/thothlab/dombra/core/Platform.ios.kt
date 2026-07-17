package tech.thothlab.dombra.core

import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.timeIntervalSince1970

internal actual fun systemNowMs(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

internal actual fun systemLocaleTag(): String =
    NSLocale.currentLocale.localeIdentifier.replace('_', '-')

internal actual val platformName: String = "ios"
