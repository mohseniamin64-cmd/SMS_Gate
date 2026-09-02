package com.example

/** A small explicit back stack for the single-activity Compose application. */
data class AppNavigationState(
    val stack: List<String> = listOf("dashboard")
) {
    val currentKey: String
        get() = stack.lastOrNull() ?: "dashboard"

    fun navigate(destinationKey: String): AppNavigationState {
        if (destinationKey == currentKey) return this
        return copy(stack = stack + destinationKey)
    }

    fun navigateToDashboard(): AppNavigationState = AppNavigationState()

    fun back(): AppNavigationState =
        if (stack.size > 1) copy(stack = stack.dropLast(1)) else this
}
