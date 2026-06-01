package app.areada.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.areada.R
import java.util.Locale

@Composable
internal fun NoteEditorSearchBar(
    query: String,
    matchLabel: String,
    hasMatches: Boolean,
    backgroundColor: Color,
    textColor: Color,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                placeholder = {
                    Text(text = stringResource(R.string.search_note))
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
            if (matchLabel.isNotBlank()) {
                Text(
                    text = matchLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.68f),
                )
            }
            IconButton(
                enabled = hasMatches,
                onClick = onPrevious,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.previous_match),
                    tint = if (hasMatches) textColor else textColor.copy(alpha = 0.28f),
                )
            }
            IconButton(
                enabled = hasMatches,
                onClick = onNext,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.next_match),
                    tint = if (hasMatches) textColor else textColor.copy(alpha = 0.28f),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close_search),
                    tint = textColor,
                )
            }
        }
    }
}

@Composable
internal fun NoteSectionBar(
    sections: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    backgroundColor: Color,
    onToggleExpanded: () -> Unit,
    onSelectSection: (Int) -> Unit,
    onAddSection: () -> Unit,
    onRenameSection: () -> Unit,
) {
    val defaultNoteTitle = stringResource(R.string.note)
    val selectedTitle = sections.getOrNull(selectedIndex).orEmpty().ifBlank { defaultNoteTitle }
    val dropdownMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.45f)
        .coerceAtMost(320.dp)
        .coerceAtLeast(120.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onToggleExpanded),
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedTitle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Outlined.KeyboardArrowUp
                            } else {
                                Icons.Outlined.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .width(44.dp)
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onAddSection),
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onRenameSection),
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.rename),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dropdownMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(
                        items = sections,
                        key = { index, title -> "$index:$title" },
                    ) { index, title ->
                        val selected = index == selectedIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSection(index) },
                            shape = RectangleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Text(
                                text = title.ifBlank { defaultNoteTitle },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoteEditorHelperBar(
    backgroundColor: Color,
    iconColor: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertTimestamp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = canUndo,
                    onClick = onUndo,
                ) {
                    Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = stringResource(R.string.undo),
                        tint = if (canUndo) iconColor else iconColor.copy(alpha = 0.28f),
                    )
                }
                IconButton(
                    enabled = canRedo,
                    onClick = onRedo,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                        contentDescription = stringResource(R.string.redo),
                        tint = if (canRedo) iconColor else iconColor.copy(alpha = 0.28f),
                    )
                }
            }
            IconButton(onClick = onInsertTimestamp) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = stringResource(R.string.insert_timestamp),
                    tint = iconColor,
                )
            }
        }
    }
}

internal const val NoteHistoryMaxDepth = 20
internal const val NoteHistoryMemoryLimitBytes = 512 * 1024
internal const val NoteHistorySnapshotMaxBytes = 128 * 1024
internal const val NoteUndoTypingCoalesceMs = 1_200L
internal const val NoteUndoCharacterBatch = 512
internal const val NoteAutosaveMinIntervalMs = 1_200L
internal const val NoteAutosaveDebounceMs = 700L
internal const val NoteAutosaveCharacterBatch = 256
internal const val NoteTimestampMaxPrefixLength = 48
internal const val NoteInlineStyleMaxChars = 50_000

internal class NoteTextLayoutRef {
    var value: TextLayoutResult? = null
}

internal data class NoteStyledRange(
    val start: Int,
    val end: Int,
)

internal data class NoteHistoryEntry(
    val value: TextFieldValue,
    val timestampRanges: List<NoteStyledRange>,
    val order: Long,
)

internal fun estimateNoteHistoryBytes(value: TextFieldValue): Int {
    return value.text.length * 2 + 64
}

internal fun trimNoteHistories(
    undoHistory: MutableList<NoteHistoryEntry>,
    redoHistory: MutableList<NoteHistoryEntry>,
) {
    while (undoHistory.size > NoteHistoryMaxDepth) {
        undoHistory.removeAt(0)
    }
    while (redoHistory.size > NoteHistoryMaxDepth) {
        redoHistory.removeAt(0)
    }

    while (noteHistoryBytes(undoHistory) + noteHistoryBytes(redoHistory) > NoteHistoryMemoryLimitBytes) {
        val oldestUndo = undoHistory.firstOrNull()
        val oldestRedo = redoHistory.firstOrNull()
        when {
            oldestUndo == null && oldestRedo == null -> return
            oldestRedo == null -> undoHistory.removeAt(0)
            oldestUndo == null -> redoHistory.removeAt(0)
            oldestUndo.order <= oldestRedo.order -> undoHistory.removeAt(0)
            else -> redoHistory.removeAt(0)
        }
    }
}

internal fun noteHistoryBytes(history: List<NoteHistoryEntry>): Int {
    return history.sumOf { entry -> estimateNoteHistoryBytes(entry.value) }
}

internal class NoteTextVisualTransformation(
    private val timestampChipColor: Color,
    private val searchMatchColor: Color,
    private val currentSearchMatchColor: Color,
    private val timestampRanges: List<NoteStyledRange>,
    private val searchMatches: List<IntRange>,
    private val currentSearchRange: IntRange?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        builder.append(text.text)
        text.spanStyles.forEach { style ->
            builder.addStyle(style.item, style.start, style.end)
        }
        text.paragraphStyles.forEach { style ->
            builder.addStyle(style.item, style.start, style.end)
        }
        timestampRanges.forEach { range ->
            val start = range.start.coerceIn(0, text.text.length)
            val end = range.end.coerceIn(start, text.text.length)
            if (start >= end) {
                return@forEach
            }
            builder.addStyle(
                style = SpanStyle(background = timestampChipColor),
                start = start,
                end = end,
            )
        }
        searchMatches
            .filter { range -> range.first >= 0 && range.last < text.text.length }
            .forEach { range ->
                builder.addStyle(
                    style = SpanStyle(background = searchMatchColor),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        currentSearchRange
            ?.takeIf { range -> range.first >= 0 && range.last < text.text.length }
            ?.let { range ->
                builder.addStyle(
                    style = SpanStyle(background = currentSearchMatchColor),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

internal data class NoteTextEditDelta(
    val oldStart: Int,
    val oldEnd: Int,
    val newEnd: Int,
) {
    val insertedLength: Int get() = (newEnd - oldStart).coerceAtLeast(0)
    val netDelta: Int get() = insertedLength - (oldEnd - oldStart).coerceAtLeast(0)
}

internal fun updateTimestampRangesAfterEdit(
    activeRanges: MutableList<NoteStyledRange>,
    oldText: String,
    newText: String,
) {
    if (activeRanges.isEmpty() || oldText == newText) {
        return
    }
    val delta = noteTextEditDelta(oldText, newText)
    val updatedRanges = activeRanges.mapNotNull { range ->
        updateStyledRangeForEdit(range, delta, newText.length)
    }
    replaceTimestampRanges(activeRanges, updatedRanges, newText.length)
}

internal fun noteTextEditDelta(
    oldText: String,
    newText: String,
): NoteTextEditDelta {
    val maxPrefix = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) {
        prefix += 1
    }

    var oldSuffix = oldText.length
    var newSuffix = newText.length
    while (
        oldSuffix > prefix &&
        newSuffix > prefix &&
        oldText[oldSuffix - 1] == newText[newSuffix - 1]
    ) {
        oldSuffix -= 1
        newSuffix -= 1
    }

    return NoteTextEditDelta(
        oldStart = prefix,
        oldEnd = oldSuffix,
        newEnd = newSuffix,
    )
}

internal fun updateStyledRangeForEdit(
    range: NoteStyledRange,
    delta: NoteTextEditDelta,
    newTextLength: Int,
): NoteStyledRange? {
    val start = range.start.coerceAtLeast(0)
    val end = range.end.coerceAtLeast(start)
    if (start >= end) {
        return null
    }

    val isInsertionOnly = delta.oldStart == delta.oldEnd
    val updated = if (isInsertionOnly) {
        when {
            delta.oldStart < start -> {
                range.copy(start = start + delta.netDelta, end = end + delta.netDelta)
            }
            delta.oldStart >= end -> {
                range
            }
            else -> {
                range.copy(end = end + delta.insertedLength)
            }
        }
    } else {
        when {
            delta.oldEnd <= start -> {
                range.copy(start = start + delta.netDelta, end = end + delta.netDelta)
            }
            delta.oldStart >= end -> {
                range
            }
            else -> {
                val newStart = if (delta.oldStart <= start) delta.oldStart else start
                val newEnd = if (delta.oldEnd >= end) delta.newEnd else end + delta.netDelta
                NoteStyledRange(newStart, newEnd)
            }
        }
    }

    val safeStart = updated.start.coerceIn(0, newTextLength)
    val safeEnd = updated.end.coerceIn(safeStart, newTextLength)
    return if (safeStart < safeEnd) {
        NoteStyledRange(safeStart, safeEnd)
    } else {
        null
    }
}

internal fun replaceTimestampRanges(
    activeRanges: MutableList<NoteStyledRange>,
    ranges: List<NoteStyledRange>,
    textLength: Int,
) {
    activeRanges.clear()
    activeRanges.addAll(normalizeNoteStyledRanges(ranges, textLength))
}

internal fun normalizeNoteStyledRanges(
    ranges: List<NoteStyledRange>,
    textLength: Int,
): List<NoteStyledRange> {
    if (textLength <= 0 || ranges.isEmpty()) {
        return emptyList()
    }

    val sortedRanges = ranges
        .mapNotNull { range ->
            val start = range.start.coerceIn(0, textLength)
            val end = range.end.coerceIn(start, textLength)
            if (start < end) NoteStyledRange(start, end) else null
        }
        .sortedWith(compareBy<NoteStyledRange> { it.start }.thenBy { it.end })

    if (sortedRanges.isEmpty()) {
        return emptyList()
    }

    val merged = mutableListOf<NoteStyledRange>()
    sortedRanges.forEach { range ->
        val previous = merged.lastOrNull()
        if (previous != null && range.start <= previous.end) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
        } else {
            merged += range
        }
    }
    return merged
}

internal fun findRecognizableTimestampRanges(text: String): List<NoteStyledRange> {
    if ('|' !in text || text.length < 20) {
        return emptyList()
    }

    val ranges = mutableListOf<NoteStyledRange>()
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { index ->
            if (index < 0) text.length else index
        }
        recognizableTimestampPrefixLength(text, lineStart, lineEnd)?.let { length ->
            if (length > 0) {
                ranges += NoteStyledRange(lineStart, lineStart + length)
            }
        }
        if (lineEnd >= text.length) {
            break
        }
        lineStart = lineEnd + 1
    }
    return ranges
}

internal fun recognizableTimestampPrefixLength(
    text: String,
    lineStart: Int,
    lineEnd: Int,
): Int? {
    if (lineEnd - lineStart < 20) {
        return null
    }

    fun hasDigit(offset: Int): Boolean =
        lineStart + offset < lineEnd && text[lineStart + offset].isDigit()

    fun hasChar(offset: Int, expected: Char): Boolean =
        lineStart + offset < lineEnd && text[lineStart + offset] == expected

    val dateLooksRight = hasDigit(0) &&
        hasDigit(1) &&
        hasDigit(2) &&
        hasDigit(3) &&
        hasChar(4, '-') &&
        hasDigit(5) &&
        hasDigit(6) &&
        hasChar(7, '-') &&
        hasDigit(8) &&
        hasDigit(9)
    if (!dateLooksRight) {
        return null
    }

    var cursor = lineStart + 10
    while (cursor < lineEnd && text[cursor].isWhitespace() && text[cursor] != '\n') {
        cursor += 1
    }
    if (cursor >= lineEnd || text[cursor] != '|') {
        return null
    }
    cursor += 1
    while (cursor < lineEnd && text[cursor].isWhitespace() && text[cursor] != '\n') {
        cursor += 1
    }
    repeat(2) {
        if (cursor >= lineEnd || !text[cursor].isDigit()) {
            return null
        }
        cursor += 1
    }
    if (cursor >= lineEnd || text[cursor] != ':') {
        return null
    }
    cursor += 1
    repeat(2) {
        if (cursor >= lineEnd || !text[cursor].isDigit()) {
            return null
        }
        cursor += 1
    }
    while (cursor < lineEnd && text[cursor].isWhitespace() && text[cursor] != '\n') {
        cursor += 1
    }
    val period = text.substring(cursor, minOf(cursor + 2, lineEnd)).uppercase(Locale.ROOT)
    if (period != "AM" && period != "PM") {
        return null
    }
    cursor += 2
    while (cursor < lineEnd && text[cursor].isWhitespace() && text[cursor] != '\n') {
        cursor += 1
    }
    if (cursor >= lineEnd || text[cursor] != ':') {
        return null
    }
    cursor += 1
    if (cursor < lineEnd && text[cursor] == ' ') {
        cursor += 1
    }
    return (cursor - lineStart).coerceAtMost(NoteTimestampMaxPrefixLength)
}

internal fun findTextMatches(
    text: String,
    query: String,
): List<IntRange> {
    if (query.isBlank() || text.isEmpty()) {
        return emptyList()
    }
    val matches = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex <= text.length - query.length) {
        val nextIndex = text.indexOf(query, startIndex, ignoreCase = true)
        if (nextIndex < 0) {
            break
        }
        matches += nextIndex..(nextIndex + query.length - 1)
        startIndex = nextIndex + query.length.coerceAtLeast(1)
    }
    return matches
}

