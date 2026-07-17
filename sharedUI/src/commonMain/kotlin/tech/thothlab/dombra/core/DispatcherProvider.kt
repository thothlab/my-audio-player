package tech.thothlab.dombra.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Абстракция диспетчеров для тестируемости (§7.2 ТЗ).
 * `io` на web-таргетах отображается в Default: браузер не имеет блокирующего IO.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

internal class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = ioDispatcher()
}

internal expect fun ioDispatcher(): CoroutineDispatcher
