package app.areada.data

import app.areada.data.library.moveListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ListOrderHelpersTest {
    @Test
    fun movesItemUpAndDownOnePosition() {
        val items = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), moveListItem(items, index = 1, offset = -1))
        assertEquals(listOf("a", "c", "b"), moveListItem(items, index = 1, offset = 1))
    }

    @Test
    fun moveClampsAtListEdges() {
        val items = listOf("a", "b", "c")

        assertEquals(items, moveListItem(items, index = 0, offset = -1))
        assertEquals(items, moveListItem(items, index = 2, offset = 1))
        assertEquals(items, moveListItem(items, index = 9, offset = 1))
    }
}
