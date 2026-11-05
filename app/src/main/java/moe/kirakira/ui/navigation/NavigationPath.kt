package moe.kirakira.ui.navigation

/** Keeps shared entries and their saved UI state while replacing only the changed tail. */
internal fun <T> synchronizeNavigationPath(backStack: MutableList<T>, path: List<T>) {
    val commonSize = backStack.zip(path).takeWhile { (current, next) -> current == next }.size
    while (backStack.size > commonSize) backStack.removeAt(backStack.lastIndex)
    backStack.addAll(path.drop(commonSize))
}
