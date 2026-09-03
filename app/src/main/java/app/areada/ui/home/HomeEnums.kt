package app.areada.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderEntry

internal sealed interface LibraryActionTarget {
    val displayName: String
    val pinned: Boolean

    data class Folder(
        val folder: LibraryFolderEntry,
    ) : LibraryActionTarget {
        override val displayName: String = folder.name
        override val pinned: Boolean = folder.pinned
    }

    data class Book(
        val book: LibraryBookEntry,
    ) : LibraryActionTarget {
        override val displayName: String = book.title
        override val pinned: Boolean = book.pinned
    }
}

internal enum class HomeTab(val label: String) {
    Collection("Books"),
    Reading("Reading"),
    Bookmarks("Bookmarks"),
}

internal fun homeTabFromName(name: String): HomeTab =
    HomeTab.entries.firstOrNull { tab -> tab.name == name } ?: HomeTab.Collection

internal fun LibraryFileFilter.icon(): ImageVector =
    when (this) {
        LibraryFileFilter.ALL -> Icons.AutoMirrored.Outlined.LibraryBooks
        LibraryFileFilter.EPUB -> Icons.Outlined.ImportContacts
        LibraryFileFilter.PDF -> Icons.Outlined.PictureAsPdf
        LibraryFileFilter.TXT -> Icons.Outlined.Description
        LibraryFileFilter.MARKDOWN -> Icons.Outlined.Description
        LibraryFileFilter.FB2 -> Icons.AutoMirrored.Outlined.LibraryBooks
        LibraryFileFilter.ARCHIVE -> Icons.AutoMirrored.Outlined.LibraryBooks
    }
