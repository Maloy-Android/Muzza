package com.maloy.muzza.ui.utils

fun <T> switchFilter(
    current: T,
    filterOrder: List<T>,
    direction: Int,
    onFilterChange: (T) -> Unit
) {
    val currentIndex = filterOrder.indexOf(current)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).coerceIn(0, filterOrder.size - 1)
    if (newIndex != currentIndex) {
        onFilterChange(filterOrder[newIndex])
    }
}