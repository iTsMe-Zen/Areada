package app.areada.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.reader.DocumentType
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySearchResultType
import app.areada.data.reader.ReadingBookmark
import app.areada.ui.reader.BookRow
import app.areada.ui.reader.archiveFormatLabel
import app.areada.ui.reader.EmptyStateCard
import app.areada.ui.reader.InfoCard
import app.areada.ui.reader.SectionHeader
import app.areada.ui.reader.searchSubtitle

@Composable
internal fun BookmarksSection(
    bookmarks: List<ReadingBookmark>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenBookmark: (ReadingBookmark) -> Unit,
    onRemoveBookmark: (ReadingBookmark) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = stringResource(R.string.bookmarks))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bookmarks.size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (expanded) 180f else 0f
                    },
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            if (bookmarks.isEmpty()) {
                EmptyStateCard(
                    title = stringResource(R.string.no_bookmarks_title),
                    body = stringResource(R.string.no_bookmarks_body),
                )
            } else {
                bookmarks.take(20).forEach { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        pinned = false,
                        hasNote = false,
                        onClick = { onOpenBookmark(bookmark) },
                        onActions = { onRemoveBookmark(bookmark) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
internal fun ReadingSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title = stringResource(R.string.reading))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                },
            )
        }
    }
}

@Composable
internal fun BookmarkRow(
    bookmark: ReadingBookmark,
    pinned: Boolean,
    hasNote: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    onActions: () -> Unit,
    onSelect: (() -> Unit)? = null,
) {
    SwipeActionBox(
        actionLabel = stringResource(R.string.actions),
        onSwipe = onActions,
        onSwipeEndToStart = onSelect,
        endToStartLabel = stringResource(R.string.select),
    ) {
        BookRow(
            title = bookmark.title,
            type = bookmark.type,
            typeLabel = if (bookmark.type == DocumentType.ARCHIVE) {
                archiveFormatLabel(bookmark.uriString.substringAfterLast('/'))
            } else null,
            progressLabel = bookmark.customName ?: bookmark.positionLabel,
            pinned = pinned,
            hasNote = hasNote,
            selected = selected,
            onClick = onClick,
        )
    }
}

@Composable
internal fun SearchResults(
    results: List<LibrarySearchResult>,
    isSearching: Boolean,
    onOpenResult: (LibrarySearchResult) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        when {
            isSearching -> InfoCard(message = stringResource(R.string.searching_selected_folders))
            results.isEmpty() -> EmptyStateCard(
                title = stringResource(R.string.no_matching_books_title),
                body = stringResource(R.string.no_matching_books_body),
            )
            else -> {
                results.take(40).forEach { result ->
                    SearchResultRow(
                        result = result,
                        onClick = { onOpenResult(result) },
                    )
                }
                if (results.size > 40) {
                    Text(
                        text = stringResource(R.string.showing_first_40_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchResultRow(
    result: LibrarySearchResult,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (result.type) {
                    LibrarySearchResultType.FOLDER -> Icons.Outlined.Folder
                    LibrarySearchResultType.BOOK -> when (result.documentType) {
                        DocumentType.EPUB -> Icons.Outlined.ImportContacts
                        DocumentType.PDF -> Icons.Outlined.PictureAsPdf
                        DocumentType.TXT -> Icons.Outlined.Description
                        DocumentType.MARKDOWN -> Icons.Outlined.Description
                        DocumentType.FB2 -> Icons.AutoMirrored.Outlined.LibraryBooks
                        DocumentType.ZIP -> Icons.AutoMirrored.Outlined.LibraryBooks
                        DocumentType.ARCHIVE -> Icons.AutoMirrored.Outlined.LibraryBooks
                        null -> Icons.AutoMirrored.Outlined.LibraryBooks
                    }
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = result.searchSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageFoldersSheet(
    roots: List<LibraryRoot>,
    selectedRootUriString: String?,
    onDismiss: () -> Unit,
    onRemoveRoot: (LibraryRoot) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.manage_folders),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (roots.isEmpty()) {
                InfoCard(message = stringResource(R.string.no_folders_selected))
            } else {
                roots.forEachIndexed { index, root ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = root.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            if (root.treeUriString == selectedRootUriString) {
                                Text(
                                    text = stringResource(R.string.current),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onRemoveRoot(root) }) {
                            Text(text = stringResource(R.string.remove))
                        }
                    }
                    if (index < roots.lastIndex) {
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
