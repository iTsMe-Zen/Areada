package app.areada.data.library

fun <T> moveListItem(
    items: List<T>,
    index: Int,
    offset: Int,
): List<T> {
    if (items.size < 2 || offset == 0 || index !in items.indices) {
        return items
    }
    val targetIndex = (index + offset).coerceIn(items.indices)
    if (targetIndex == index) {
        return items
    }

    return items.toMutableList().apply {
        val item = removeAt(index)
        add(targetIndex, item)
    }
}
