package app.areada.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.areada.R
import app.areada.data.BookStatus
import app.areada.data.library.LibraryBookEntry
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.readingProgressPercent
import app.areada.ui.formatBookInfoDateTime
import app.areada.data.reader.DocumentType
import app.areada.ui.reader.PromptChoiceButton
import app.areada.ui.reader.archiveFormatLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentListActionSheet(
    title: String,
    pinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    bookStatus: BookStatus? = null,
    onMarkBookStatus: ((BookStatus) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val localizedContext = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (onMarkBookStatus != null) {
                    val nextStatus = if (bookStatus == BookStatus.Finished) {
                        BookStatus.Reading
                    } else {
                        BookStatus.Finished
                    }
                    ActionSheetItem(
                        label = if (nextStatus == BookStatus.Finished) {
                            stringResource(R.string.mark_as_finished)
                        } else {
                            stringResource(R.string.mark_as_reading)
                        },
                        onClick = { onMarkBookStatus(nextStatus) },
                    )
                }
                ActionSheetItem(
                    label = if (pinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                    onClick = onTogglePin,
                )
                if (canMoveUp) {
                    ActionSheetItem(label = stringResource(R.string.move_up), onClick = onMoveUp)
                }
                if (canMoveDown) {
                    ActionSheetItem(label = stringResource(R.string.move_down), onClick = onMoveDown)
                }
                ActionSheetItem(label = stringResource(R.string.remove), onClick = onRemove)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryActionSheet(
    target: LibraryActionTarget,
    bookStatus: BookStatus? = null,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onShowInfo: (() -> Unit)? = null,
    onMarkBookStatus: ((BookStatus) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val localizedContext = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (target is LibraryActionTarget.Book && onShowInfo != null) {
                    ActionSheetItem(
                        label = stringResource(R.string.info),
                        onClick = onShowInfo,
                    )
                }
                if (target is LibraryActionTarget.Book && onMarkBookStatus != null) {
                    val nextStatus = if (bookStatus == BookStatus.Finished) {
                        BookStatus.Reading
                    } else {
                        BookStatus.Finished
                    }
                    ActionSheetItem(
                        label = if (nextStatus == BookStatus.Finished) {
                            stringResource(R.string.mark_as_finished)
                        } else {
                            stringResource(R.string.mark_as_reading)
                        },
                        onClick = { onMarkBookStatus(nextStatus) },
                    )
                }
                ActionSheetItem(
                    label = if (target.pinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                    onClick = onTogglePin,
                )
                ActionSheetItem(
                    label = stringResource(R.string.rename),
                    onClick = onRename,
                )
                ActionSheetItem(
                    label = stringResource(R.string.delete),
                    onClick = onDelete,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookInfoSheet(
    book: LibraryBookEntry,
    progress: ReadingProgress?,
    status: BookStatus,
    recent: RecentDocument?,
    onDismiss: () -> Unit,
    onMarkStatus: (BookStatus) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            BookInfoRow(
                label = stringResource(R.string.format),
                value = if (book.type == DocumentType.ARCHIVE) {
                    archiveFormatLabel(book.fileName) ?: book.type.name
                } else {
                    book.type.name
                },
            )
            BookInfoRow(label = stringResource(R.string.status), value = status.displayLabel())
            BookInfoRow(label = stringResource(R.string.progress), value = bookInfoProgressLabel(progress))
            recent?.lastOpenedAt
                ?.takeIf { timestamp -> timestamp > 0L }
                ?.let { timestamp ->
                    BookInfoRow(label = stringResource(R.string.last_opened), value = formatBookInfoTimestamp(timestamp))
                }
            book.addedAt
                .takeIf { timestamp -> timestamp > 0L }
                ?.let { timestamp ->
                    BookInfoRow(label = stringResource(R.string.added), value = formatBookInfoTimestamp(timestamp))
                }
            BookInfoRow(label = stringResource(R.string.file), value = book.fileName)
            if (book.pinned) {
                BookInfoRow(label = stringResource(R.string.pinned), value = stringResource(R.string.prompt_yes))
            }
            Spacer(modifier = Modifier.height(10.dp))
            ActionSheetItem(
                label = if (status == BookStatus.Finished) {
                    stringResource(R.string.mark_as_reading)
                } else {
                    stringResource(R.string.mark_as_finished)
                },
                onClick = {
                    onMarkStatus(
                        if (status == BookStatus.Finished) {
                            BookStatus.Reading
                        } else {
                            BookStatus.Finished
                        },
                    )
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BookInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BookStatus.displayLabel(): String =
    when (this) {
        BookStatus.Unread -> stringResource(R.string.not_started)
        BookStatus.Reading -> stringResource(R.string.reading_status)
        BookStatus.Finished -> stringResource(R.string.finished)
    }

@Composable
internal fun bookInfoProgressLabel(progress: ReadingProgress?): String {
    val percent = readingProgressPercent(progress) ?: return stringResource(R.string.not_started)
    return "$percent%"
}

internal fun formatBookInfoTimestamp(timestamp: Long): String =
    formatBookInfoDateTime(timestamp)

@Composable
internal fun ActionSheetItem(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
internal fun BatchActionBar(
    selectedCount: Int,
    isCollectionTab: Boolean,
    onClearSelection: () -> Unit,
    onBatchMarkFinished: (() -> Unit)? = null,
    onBatchMarkReading: (() -> Unit)? = null,
    onBatchPin: () -> Unit,
    onBatchRemove: (() -> Unit)? = null,
    onBatchDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.clear),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onClearSelection),
                )
            }
            androidx.compose.material3.HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (onBatchRemove != null) {
                    BatchActionIcon(
                        icon = { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.primary) },
                        label = stringResource(R.string.remove),
                        onClick = onBatchRemove,
                    )
                }
                if (onBatchDelete != null) {
                    BatchActionIcon(
                        icon = { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.primary) },
                        label = stringResource(R.string.delete),
                        onClick = onBatchDelete,
                    )
                }
                if (onBatchMarkFinished != null) {
                    BatchActionIcon(
                        icon = {
                            Text(
                                text = "\u2713",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label = stringResource(R.string.mark_as_finished),
                        onClick = onBatchMarkFinished,
                    )
                }
                if (onBatchMarkReading != null) {
                    BatchActionIcon(
                        icon = {
                            Text(
                                text = "\u2715",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label = stringResource(R.string.mark_as_reading),
                        onClick = onBatchMarkReading,
                    )
                }
                BatchActionIcon(
                    icon = { Icon(Icons.Outlined.Bookmark, contentDescription = stringResource(R.string.pin), tint = MaterialTheme.colorScheme.primary) },
                    label = stringResource(R.string.pin),
                    onClick = onBatchPin,
                )
            }
        }
    }
}

@Composable
private fun BatchActionIcon(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            icon()
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RenameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.rename),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PromptChoiceButton(
                        label = stringResource(R.string.save),
                        highlighted = true,
                        onClick = onConfirm,
                        enabled = name.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    PromptChoiceButton(
                        label = stringResource(R.string.cancel),
                        highlighted = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
