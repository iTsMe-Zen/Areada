package app.areada.data

import app.areada.data.library.NaturalSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {
    @Test
    fun sortsNumberRunsByNumericValue() {
        val names = listOf("Chapter 10", "Chapter 2", "Chapter 1")

        val sorted = names.sortedWith(NaturalSort.comparator { it })

        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10"), sorted)
    }

    @Test
    fun comparesCaseInsensitively() {
        assertEquals(0, NaturalSort.compare("File 7", "file 7"))
    }

    @Test
    fun shorterEquivalentNumberRunSortsFirst() {
        assertTrue(NaturalSort.compare("File 2", "File 02") < 0)
    }
}
