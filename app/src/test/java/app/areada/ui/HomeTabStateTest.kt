package app.areada.ui

import app.areada.ui.home.HomeTab
import app.areada.ui.home.homeTabFromName
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabStateTest {
    @Test
    fun homeTabFromNameRestoresValidPersistedTab() {
        assertEquals(HomeTab.Reading, homeTabFromName("Reading"))
        assertEquals(HomeTab.Bookmarks, homeTabFromName("Bookmarks"))
    }

    @Test
    fun homeTabFromNameFallsBackToBooksForInvalidValue() {
        assertEquals(HomeTab.Collection, homeTabFromName(""))
        assertEquals(HomeTab.Collection, homeTabFromName("Missing"))
    }
}
