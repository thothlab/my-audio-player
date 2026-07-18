package tech.thothlab.dombra.ui

import androidx.compose.runtime.mutableStateListOf

/** Экраны приложения (D-10 — собственная лёгкая навигация вместо Nav3 alpha). */
sealed interface Screen {
    data object Library : Screen
    data object Player : Screen
    data object Settings : Screen
}

/** Лёгкий стек навигации: push/pop, root не выталкивается. */
class Navigator(root: Screen) {
    private val stack = mutableStateListOf(root)

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
