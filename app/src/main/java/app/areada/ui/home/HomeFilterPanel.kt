package app.areada.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderPickerEntry
import app.areada.data.library.LibrarySortMode

@Composable
internal fun LibraryFileFilter.displayLabel(): String =
    when (this) {
        LibraryFileFilter.ALL -> stringResource(R.string.all)
        LibraryFileFilter.ARCHIVE -> stringResource(R.string.filter_archives)
        LibraryFileFilter.EPUB -> stringResource(R.string.filter_epub)
        LibraryFileFilter.FB2 -> stringResource(R.string.filter_fb2)
        LibraryFileFilter.MARKDOWN -> stringResource(R.string.filter_markdown)
        LibraryFileFilter.PDF -> stringResource(R.string.filter_pdf)
        LibraryFileFilter.TXT -> stringResource(R.string.filter_txt)
    }

@Composable
internal fun LibrarySortMode.displayLabel(): String {
    val arrow = if (isAscending) "\u2191" else "\u2193"
    return "$arrow ${sortBaseLabel()}"
}

    @Composable
    internal fun LibraryFileFilter.emptyLibraryMessage(): String =
    when (this) {
        LibraryFileFilter.ALL -> stringResource(R.string.empty_library_all)
        LibraryFileFilter.EPUB -> stringResource(R.string.empty_library_epub)
        LibraryFileFilter.PDF -> stringResource(R.string.empty_library_pdf)
        LibraryFileFilter.TXT -> stringResource(R.string.empty_library_txt)
        LibraryFileFilter.MARKDOWN -> stringResource(R.string.empty_library_md)
        LibraryFileFilter.FB2 -> stringResource(R.string.empty_library_fb2)
        LibraryFileFilter.ARCHIVE -> stringResource(R.string.empty_library_archive)
    }

@Composable
internal fun LibrarySortMode.sortBaseLabel(): String =
    when (this) {
        LibrarySortMode.NAME_ASC, LibrarySortMode.NAME_DESC -> stringResource(R.string.sort_name_base)
        LibrarySortMode.DATE_ADDED_ASC, LibrarySortMode.DATE_ADDED_DESC -> stringResource(R.string.sort_date_added_base)
        LibrarySortMode.RECENTLY_OPENED, LibrarySortMode.RECENTLY_OPENED_ASC -> stringResource(R.string.sort_recently_opened)
        LibrarySortMode.READING_PROGRESS, LibrarySortMode.READING_PROGRESS_ASC -> stringResource(R.string.sort_reading_progress)
        LibrarySortMode.FILE_TYPE, LibrarySortMode.FILE_TYPE_DESC -> stringResource(R.string.sort_file_type)
    }

@Composable
internal fun LibrarySortButton(
    sortMode: LibrarySortMode,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = sortMode.displayLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun LibrarySortInlinePanel(
    selectedSortMode: LibrarySortMode,
    onSelectSortMode: (LibrarySortMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            LibrarySortMode.baseOptions.forEach { baseOption ->
                val isBaseOfActive = baseOption == selectedSortMode.baseOption
                val selected = isBaseOfActive
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isBaseOfActive && selectedSortMode.isDirectional) {
                                onSelectSortMode(selectedSortMode.toggled())
                            } else {
                                onSelectSortMode(baseOption)
                            }
                        }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                            RectangleShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = baseOption.sortBaseLabel(),
                        modifier = Modifier.weight(1f),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (selected && selectedSortMode.isDirectional) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = if (selectedSortMode.isAscending) 180f else 0f
                                },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryFilterButton(
    filter: LibraryFileFilter,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FilterList,
            contentDescription = stringResource(R.string.filter_files),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = filter.displayLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun LibraryFilterInlinePanel(
    selectedFilter: LibraryFileFilter,
    onSelectFilter: (LibraryFileFilter) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            LibraryFileFilter.entries.forEach { filter ->
                val selected = filter == selectedFilter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFilter(filter) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                            RectangleShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = filter.icon(),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = filter.displayLabel(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FolderPickerDropdown(
    entries: List<LibraryFolderPickerEntry>,
    selectedRootUriString: String?,
    currentRelativePath: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedEntry = entries.firstOrNull { entry ->
        entry.rootUriString == selectedRootUriString
    } ?: entries.firstOrNull()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable {
                onToggleExpanded()
            },
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedEntry?.name ?: stringResource(R.string.folders),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = if (expanded) {
                    stringResource(R.string.close_folder_menu)
                } else {
                    stringResource(R.string.open_folder_menu)
                },
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                },
            )
        }
    }
}

@Composable
internal fun FolderPickerInlinePanel(
    entries: List<LibraryFolderPickerEntry>,
    selectedRootUriString: String?,
    currentRelativePath: String,
    onSelectEntry: (LibraryFolderPickerEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .padding(vertical = 6.dp),
        ) {
            items(
                items = entries,
                key = { entry -> "${entry.rootUriString}::${entry.relativePath}" },
            ) { entry ->
                val selected = entry.rootUriString == selectedRootUriString
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectEntry(entry)
                        }
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                            RectangleShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = entry.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
