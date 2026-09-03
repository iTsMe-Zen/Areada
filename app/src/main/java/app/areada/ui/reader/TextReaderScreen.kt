package app.areada.ui.reader

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.zIndex
import app.areada.R
import app.areada.data.reader.DocumentType
import app.areada.data.NoteSection
import app.areada.data.addNoteSection
import app.areada.data.moveNoteSectionDown
import app.areada.data.moveNoteSectionUp
import app.areada.data.parseNoteSections
import app.areada.ui.NoteSectionActionSheet
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReaderThemeMode
import app.areada.data.renameNoteSection
import app.areada.data.resolveNoteSectionIndex
import app.areada.data.serializeNoteSections
import app.areada.data.reader.txtBookmarkId
import app.areada.ui.formatNoteTimestamp
import app.areada.ui.home.RenameDialog
import app.areada.ui.NoteEditorHelperBar
import app.areada.ui.NoteEditorSearchBar
import app.areada.ui.NoteSectionBar
import app.areada.ui.NoteAutosaveCharacterBatch
import app.areada.ui.NoteAutosaveDebounceMs
import app.areada.ui.NoteAutosaveMinIntervalMs
import app.areada.ui.NoteHistoryEntry
import app.areada.ui.NoteHistorySnapshotMaxBytes
import app.areada.ui.NoteInlineStyleMaxChars
import app.areada.ui.NoteStyledRange
import app.areada.ui.NoteTextLayoutRef
import app.areada.ui.NoteTextVisualTransformation
import app.areada.ui.NoteUndoCharacterBatch
import app.areada.ui.NoteUndoTypingCoalesceMs
import app.areada.ui.estimateNoteHistoryBytes
import app.areada.ui.findRecognizableTimestampRanges
import app.areada.ui.findTextMatches
import app.areada.ui.normalizeNoteStyledRanges
import app.areada.ui.replaceTimestampRanges
import app.areada.ui.trimNoteHistories
import app.areada.ui.updateTimestampRangesAfterEdit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextReaderScreen(
    screen: ReaderScreen.Text,
    preferences: ReaderPreferences,
    bookmarks: List<ReadingBookmark>,
    onBack: () -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onSaveText: (String) -> Unit,
    onSaveProgress: (Float) -> Unit,
    onToggleBookmark: (Float) -> Unit,
    onDiscardText: () -> Unit,
    onRenameText: (String, String) -> Unit,
    onNoteSectionSelected: (String) -> Unit,
    onOpenBookNote: (() -> Unit)?,
) {
    var showSettings by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showRename by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showDiscardPrompt by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showSaveChangesPrompt by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var sectionDeletionTarget by remember { mutableStateOf<Int?>(null) }
    var renameText by rememberSaveable(screen.document.uriString) {
        mutableStateOf(screen.document.title)
    }
    val isEditable = screen.editable && screen.document.type == DocumentType.TXT
    val sectionedNotesEnabled = isEditable && screen.sectionedNote
    val initialNoteSections = remember(screen.document.uriString, screen.initialText, sectionedNotesEnabled) {
        if (sectionedNotesEnabled) {
            parseNoteSections(screen.initialText).sections
        } else {
            emptyList()
        }
    }
    val noteSections = remember(screen.document.uriString, sectionedNotesEnabled) {
        mutableStateListOf<NoteSection>().apply {
            addAll(initialNoteSections)
        }
    }
    val initialNoteSectionIndex = remember(initialNoteSections, screen.initialNoteSectionTitle) {
        resolveNoteSectionIndex(initialNoteSections, screen.initialNoteSectionTitle)
    }
    var selectedNoteSectionIndex by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableIntStateOf(initialNoteSectionIndex)
    }
    var showNoteSectionPicker by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableStateOf(false)
    }
    var showRenameSection by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableStateOf(false)
    }
    var renameSectionText by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableStateOf("")
    }
    var showSectionActionSheet by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableStateOf(false)
    }
    var actionSheetSectionIndex by rememberSaveable(screen.document.uriString, sectionedNotesEnabled) {
        mutableIntStateOf(-1)
    }
    val pinnedNoteSectionTitles = remember(screen.document.uriString) {
        mutableStateOf(setOf<String>())
    }
    val initialEditorText = if (sectionedNotesEnabled) {
        initialNoteSections.getOrNull(initialNoteSectionIndex)?.content
            ?: initialNoteSections.firstOrNull()?.content.orEmpty()
    } else {
        screen.initialText
    }
    var noteValue by remember(screen.document.uriString) {
        mutableStateOf(
            TextFieldValue(
                text = initialEditorText,
                selection = TextRange(initialEditorText.length),
            ),
        )
    }
    val undoHistory = remember(screen.document.uriString) {
        mutableStateListOf<NoteHistoryEntry>()
    }
    val redoHistory = remember(screen.document.uriString) {
        mutableStateListOf<NoteHistoryEntry>()
    }
    val activeTimestampRanges = remember(screen.document.uriString) {
        mutableStateListOf<NoteStyledRange>().apply {
            addAll(findRecognizableTimestampRanges(initialEditorText))
        }
    }
    var noteHistoryOrder by remember(screen.document.uriString) {
        mutableStateOf(0L)
    }
    var lastUndoCheckpointAt by remember(screen.document.uriString) {
        mutableStateOf(0L)
    }
    var lastUndoCheckpointLength by remember(screen.document.uriString) {
        mutableIntStateOf(initialEditorText.length)
    }
    var showNoteSearch by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var lastTimestampInsertMs by remember(screen.document.uriString) {
        mutableStateOf(0L)
    }
    var noteSearchQuery by rememberSaveable(screen.document.uriString) {
        mutableStateOf("")
    }
    var noteSearchIndex by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    val text = noteValue.text
    var saveOnDispose by remember(screen.document.uriString) {
        mutableStateOf(true)
    }
    var noteEditedSinceOpen by remember(screen.document.uriString) {
        mutableStateOf(false)
    }
    var noteStructureEditedSinceOpen by remember(screen.document.uriString) {
        mutableStateOf(false)
    }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var editorHeightPx by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    val noteTextLayoutRef = remember(screen.document.uriString) {
        NoteTextLayoutRef()
    }
    var lastAutosaveAt by remember(screen.document.uriString) {
        mutableStateOf(0L)
    }
    var lastAutosavedText by remember(screen.document.uriString) {
        mutableStateOf(screen.initialText)
    }
    val cursorKeepVisiblePaddingPx = with(LocalDensity.current) {
        72.dp.roundToPx()
    }
    val currentScrollFraction = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    fun noteSectionsWithCurrentText(sectionText: String = text): List<NoteSection> {
        if (!sectionedNotesEnabled) {
            return emptyList()
        }
        val safeSections = noteSections.ifEmpty {
            listOf(NoteSection(title = "Note", content = ""))
        }
        val safeIndex = selectedNoteSectionIndex.coerceIn(0, safeSections.lastIndex)
        return safeSections.mapIndexed { index, section ->
            if (index == safeIndex) {
                section.copy(content = sectionText)
            } else {
                section
            }
        }
    }

    fun persistedTextFor(sectionText: String = text): String =
        if (sectionedNotesEnabled) {
            serializeNoteSections(noteSectionsWithCurrentText(sectionText))
        } else {
            sectionText
        }

    val currentPersistedText = persistedTextFor(text)
    val latestPersistedText by rememberUpdatedState(currentPersistedText)
    val latestScrollFraction by rememberUpdatedState(currentScrollFraction)
    val shouldSaveOnDispose by rememberUpdatedState(saveOnDispose)
    val currentBookmarked = bookmarks.any {
        it.id == txtBookmarkId(screen.document.uriString, currentScrollFraction)
    }
    val renderPalette = rememberReaderRenderPalette(preferences.themeMode)
    val backgroundColor = Color(AndroidColor.parseColor(renderPalette.backgroundHex))
    val textColor = Color(AndroidColor.parseColor(renderPalette.textHex))
    val timestampBackgroundColor = Color(AndroidColor.parseColor(renderPalette.mutedHex)).copy(
        alpha = if (preferences.themeMode == ReaderThemeMode.DARK) 0.55f else 0.35f,
    )
    val searchMatchBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val currentSearchMatchBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val noteSearchMatches = remember(text, noteSearchQuery) {
        findTextMatches(text, noteSearchQuery)
    }
    val currentSearchIndex = if (noteSearchMatches.isEmpty()) {
        -1
    } else {
        noteSearchIndex.coerceIn(0, noteSearchMatches.lastIndex)
    }
    val currentSearchRange = noteSearchMatches.getOrNull(currentSearchIndex)
    val activeTimestampStyleRanges = remember(text.length, activeTimestampRanges.toList()) {
        normalizeNoteStyledRanges(activeTimestampRanges, text.length)
    }
    val enableInlineNoteStyling = text.length <= NoteInlineStyleMaxChars &&
        (activeTimestampStyleRanges.isNotEmpty() || noteSearchMatches.isNotEmpty())
    val noteVisualTransformation: VisualTransformation = remember(
        timestampBackgroundColor,
        searchMatchBackgroundColor,
        currentSearchMatchBackgroundColor,
        activeTimestampStyleRanges,
        noteSearchMatches,
        currentSearchRange,
        enableInlineNoteStyling,
    ) {
        if (enableInlineNoteStyling) {
            NoteTextVisualTransformation(
                timestampChipColor = timestampBackgroundColor,
                searchMatchColor = searchMatchBackgroundColor,
                currentSearchMatchColor = currentSearchMatchBackgroundColor,
                timestampRanges = activeTimestampStyleRanges,
                searchMatches = noteSearchMatches,
                currentSearchRange = currentSearchRange,
            )
        } else {
            VisualTransformation.None
        }
    }

    fun addNoteHistoryState(
        history: MutableList<NoteHistoryEntry>,
        value: TextFieldValue,
        timestampRanges: List<NoteStyledRange> = activeTimestampRanges.toList(),
    ) {
        if (history.lastOrNull()?.value?.text == value.text) {
            return
        }
        if (estimateNoteHistoryBytes(value) > NoteHistorySnapshotMaxBytes) {
            return
        }
        noteHistoryOrder += 1L
        history += NoteHistoryEntry(
            value = value,
            timestampRanges = normalizeNoteStyledRanges(timestampRanges, value.text.length),
            order = noteHistoryOrder,
        )
        trimNoteHistories(undoHistory, redoHistory)
    }

    fun shouldRecordUndoCheckpoint(
        previousValue: TextFieldValue,
        nextValue: TextFieldValue,
        forceCheckpoint: Boolean,
    ): Boolean {
        if (forceCheckpoint || undoHistory.isEmpty()) {
            return true
        }
        if (estimateNoteHistoryBytes(nextValue) > NoteHistorySnapshotMaxBytes) {
            return false
        }
        val now = SystemClock.uptimeMillis()
        val lengthDelta = abs(nextValue.text.length - lastUndoCheckpointLength)
        val insertedText = when {
            nextValue.text.length > previousValue.text.length -> {
                val start = minOf(previousValue.selection.start, previousValue.selection.end)
                    .coerceIn(0, nextValue.text.length)
                val insertedLength = nextValue.text.length - previousValue.text.length
                nextValue.text.substring(start, (start + insertedLength).coerceAtMost(nextValue.text.length))
            }
            else -> ""
        }
        return now - lastUndoCheckpointAt >= NoteUndoTypingCoalesceMs ||
            lengthDelta >= NoteUndoCharacterBatch ||
            '\n' in insertedText
    }

    fun updateNoteValue(
        nextValue: TextFieldValue,
        recordUndo: Boolean = true,
        forceUndoCheckpoint: Boolean = false,
    ) {
        if (!isEditable && nextValue.text != noteValue.text) {
            return
        }
        if (recordUndo && nextValue.text != noteValue.text) {
            if (shouldRecordUndoCheckpoint(noteValue, nextValue, forceUndoCheckpoint)) {
                addNoteHistoryState(undoHistory, noteValue, activeTimestampRanges.toList())
                lastUndoCheckpointAt = SystemClock.uptimeMillis()
                lastUndoCheckpointLength = noteValue.text.length
            }
            redoHistory.clear()
        }
        if (nextValue.text != noteValue.text) {
            updateTimestampRangesAfterEdit(
                activeRanges = activeTimestampRanges,
                oldText = noteValue.text,
                newText = nextValue.text,
            )
            noteEditedSinceOpen = true
        }
        noteValue = nextValue
    }

    fun undoNoteChange() {
        val previousEntry = undoHistory.lastOrNull() ?: return
        undoHistory.removeAt(undoHistory.lastIndex)
        addNoteHistoryState(redoHistory, noteValue, activeTimestampRanges.toList())
        replaceTimestampRanges(activeTimestampRanges, previousEntry.timestampRanges, previousEntry.value.text.length)
        noteValue = previousEntry.value
    }

    fun redoNoteChange() {
        val nextEntry = redoHistory.lastOrNull() ?: return
        redoHistory.removeAt(redoHistory.lastIndex)
        addNoteHistoryState(undoHistory, noteValue, activeTimestampRanges.toList())
        replaceTimestampRanges(activeTimestampRanges, nextEntry.timestampRanges, nextEntry.value.text.length)
        noteValue = nextEntry.value
    }

    fun saveDraftIfChanged(textToSave: String = latestPersistedText) {
        if (!isEditable) {
            return
        }
        if (sectionedNotesEnabled && !noteEditedSinceOpen && !noteStructureEditedSinceOpen) {
            return
        }
        if (textToSave == lastAutosavedText) {
            return
        }
        lastAutosaveAt = SystemClock.uptimeMillis()
        lastAutosavedText = textToSave
        onSaveText(textToSave)
    }

    fun resetNoteEditHistory(nextText: String) {
        undoHistory.clear()
        redoHistory.clear()
        noteHistoryOrder = 0L
        lastUndoCheckpointAt = 0L
        lastUndoCheckpointLength = nextText.length
        replaceTimestampRanges(activeTimestampRanges, findRecognizableTimestampRanges(nextText), nextText.length)
    }

    fun commitCurrentNoteSection() {
        if (!sectionedNotesEnabled || noteSections.isEmpty()) {
            return
        }
        val safeIndex = selectedNoteSectionIndex.coerceIn(0, noteSections.lastIndex)
        noteSections[safeIndex] = noteSections[safeIndex].copy(content = noteValue.text)
        selectedNoteSectionIndex = safeIndex
    }

    fun switchNoteSection(index: Int) {
        if (!sectionedNotesEnabled || index !in noteSections.indices) {
            return
        }
        commitCurrentNoteSection()
        saveDraftIfChanged(serializeNoteSections(noteSections))
        val nextText = noteSections[index].content
        selectedNoteSectionIndex = index
        onNoteSectionSelected(noteSections[index].title)
        noteValue = TextFieldValue(
            text = nextText,
            selection = TextRange(nextText.length),
        )
        resetNoteEditHistory(nextText)
        noteEditedSinceOpen = false
        noteStructureEditedSinceOpen = false
        showNoteSectionPicker = false
    }

    fun addSectionAndSwitch() {
        if (!sectionedNotesEnabled) {
            return
        }
        commitCurrentNoteSection()
        saveDraftIfChanged(serializeNoteSections(noteSections))
        val nextSections = addNoteSection(noteSections)
        noteSections.clear()
        noteSections.addAll(nextSections)
        val nextIndex = noteSections.lastIndex.coerceAtLeast(0)
        selectedNoteSectionIndex = nextIndex
        onNoteSectionSelected(noteSections.getOrNull(nextIndex)?.title.orEmpty())
        noteValue = TextFieldValue("")
        resetNoteEditHistory("")
        noteEditedSinceOpen = false
        noteStructureEditedSinceOpen = true
        saveDraftIfChanged(serializeNoteSections(noteSections))
        noteStructureEditedSinceOpen = false
        showNoteSectionPicker = false
    }

    fun renameCurrentSection() {
        if (!sectionedNotesEnabled || noteSections.isEmpty()) {
            return
        }
        if (renameSectionText.trim().isBlank()) {
            return
        }
        commitCurrentNoteSection()
        val renamedSections = renameNoteSection(
            sections = noteSections,
            index = selectedNoteSectionIndex.coerceIn(0, noteSections.lastIndex),
            requestedTitle = renameSectionText,
        )
        noteSections.clear()
        noteSections.addAll(renamedSections)
        onNoteSectionSelected(noteSections.getOrNull(selectedNoteSectionIndex)?.title.orEmpty())
        noteStructureEditedSinceOpen = true
        saveDraftIfChanged(serializeNoteSections(noteSections))
        noteStructureEditedSinceOpen = false
        showRenameSection = false
    }

    fun selectNoteSearchMatch(index: Int) {
        if (noteSearchMatches.isEmpty()) {
            return
        }
        val safeIndex = ((index % noteSearchMatches.size) + noteSearchMatches.size) % noteSearchMatches.size
        val range = noteSearchMatches[safeIndex]
        noteSearchIndex = safeIndex
        updateNoteValue(
            nextValue = noteValue.copy(selection = TextRange(range.first, range.last + 1)),
            recordUndo = false,
        )
    }

    fun closeNoteSearch() {
        showNoteSearch = false
        noteSearchQuery = ""
        noteSearchIndex = 0
    }

    fun saveAndLeave() {
        saveOnDispose = false
        if (isEditable) {
            saveDraftIfChanged(latestPersistedText)
        }
        onSaveProgress(currentScrollFraction)
        onBack()
    }

    fun confirmDiscardAndLeave() {
        saveOnDispose = false
        showDiscardPrompt = false
        onDiscardText()
    }

    fun discardNoteChangesAndLeave() {
        saveOnDispose = false
        showSaveChangesPrompt = false
        if (screen.deleteOnDiscard) {
            onDiscardText()
            return
        }
        if (isEditable && latestPersistedText != screen.initialText) {
            onSaveText(screen.initialText)
        }
        onSaveProgress(currentScrollFraction)
        onBack()
    }

    fun requestBackFromNote() {
        if (isEditable && latestPersistedText != screen.initialText) {
            showSaveChangesPrompt = true
            return
        }
        saveOnDispose = false
        onSaveProgress(currentScrollFraction)
        onBack()
    }

    fun discardAndLeave() {
        if (isEditable) {
            showDiscardPrompt = true
        } else {
            saveOnDispose = false
            onSaveProgress(currentScrollFraction)
            onBack()
        }
    }

    fun insertTimestamp() {
        if (!isEditable) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastTimestampInsertMs < 200L) {
            return
        }
        lastTimestampInsertMs = now
        val timestamp = formatNoteTimestamp()
        val selectionStart = minOf(noteValue.selection.start, noteValue.selection.end)
            .coerceIn(0, noteValue.text.length)
        val selectionEnd = maxOf(noteValue.selection.start, noteValue.selection.end)
            .coerceIn(0, noteValue.text.length)
        val prefix = if (selectionStart > 0 && noteValue.text.getOrNull(selectionStart - 1) != '\n') "\n" else ""
        val suffix = if (selectionEnd < noteValue.text.length && noteValue.text.getOrNull(selectionEnd) != '\n') "\n" else ""
        val insertion = "$prefix$timestamp $suffix"
        val timestampStart = selectionStart + prefix.length
        val timestampEnd = timestampStart + timestamp.length + 1
        val nextText = buildString {
            append(noteValue.text.substring(0, selectionStart))
            append(insertion)
            append(noteValue.text.substring(selectionEnd))
        }
        val cursor = selectionStart + insertion.length
        updateNoteValue(
            nextValue = TextFieldValue(
                text = nextText,
                selection = TextRange(cursor),
            ),
            forceUndoCheckpoint = true,
        )
        activeTimestampRanges.add(NoteStyledRange(timestampStart, timestampEnd))
    }

    BackHandler {
        requestBackFromNote()
    }

    DisposableEffect(screen.document.uriString) {
        onDispose {
            if (shouldSaveOnDispose) {
                if (isEditable) {
                    saveDraftIfChanged(latestPersistedText)
                }
                onSaveProgress(latestScrollFraction)
            }
        }
    }

    LaunchedEffect(screen.document.uriString, screen.initialScrollFraction, scrollState.maxValue) {
        if (screen.initialScrollFraction > 0f && scrollState.maxValue > 0) {
            scrollState.scrollTo((scrollState.maxValue * screen.initialScrollFraction.coerceIn(0f, 1f)).roundToInt())
        }
    }
    LaunchedEffect(showNoteSearch, noteSearchQuery, noteSearchMatches.size) {
        noteSearchIndex = if (showNoteSearch && noteSearchQuery.isNotBlank() && noteSearchMatches.isNotEmpty()) {
            noteSearchIndex.coerceIn(0, noteSearchMatches.lastIndex)
        } else {
            0
        }
    }
    LaunchedEffect(currentPersistedText, noteEditedSinceOpen) {
        if (isEditable && noteEditedSinceOpen && currentPersistedText != lastAutosavedText) {
            delay(NoteAutosaveDebounceMs)
            saveDraftIfChanged(currentPersistedText)
        }
    }
    LaunchedEffect(
        noteValue.text.length,
        noteValue.selection.start,
        noteValue.selection.end,
        editorHeightPx,
        scrollState.maxValue,
        noteEditedSinceOpen,
    ) {
        val layout = noteTextLayoutRef.value ?: return@LaunchedEffect
        if (!noteEditedSinceOpen || editorHeightPx <= 0 || scrollState.maxValue <= 0) {
            return@LaunchedEffect
        }

        val cursor = maxOf(noteValue.selection.start, noteValue.selection.end)
            .coerceIn(0, noteValue.text.length)
        val cursorRect = layout.getCursorRect(cursor)
        val visibleTop = scrollState.value + cursorKeepVisiblePaddingPx
        val visibleBottom = scrollState.value + editorHeightPx - cursorKeepVisiblePaddingPx
        val nextScroll = when {
            cursorRect.bottom > visibleBottom -> {
                (cursorRect.bottom - editorHeightPx + cursorKeepVisiblePaddingPx)
                    .roundToInt()
                    .coerceIn(0, scrollState.maxValue)
            }
            cursorRect.top < visibleTop -> {
                (cursorRect.top - cursorKeepVisiblePaddingPx)
                    .roundToInt()
                    .coerceIn(0, scrollState.maxValue)
            }
            else -> null
        }

        if (nextScroll != null && nextScroll != scrollState.value) {
            scrollState.scrollTo(nextScroll)
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            preferences = preferences,
            onBookNoteClick = onOpenBookNote,
            onDismiss = { showSettings = false },
            onPreferencesChange = onPreferencesChange,
        )
    }

    if (showRename) {
        RenameDialog(
            name = renameText,
            onNameChange = { renameText = it },
            onDismiss = { showRename = false },
            onConfirm = {
                onRenameText(renameText, latestPersistedText)
                showRename = false
            },
        )
    }
    if (showRenameSection) {
        RenameDialog(
            name = renameSectionText,
            onNameChange = { renameSectionText = it },
            onDismiss = { showRenameSection = false },
            onConfirm = ::renameCurrentSection,
        )
    }
    if (showSectionActionSheet && actionSheetSectionIndex in noteSections.indices) {
        val section = noteSections[actionSheetSectionIndex]
        NoteSectionActionSheet(
            sectionTitle = section.title,
            pinned = section.title in pinnedNoteSectionTitles.value,
            canMoveUp = actionSheetSectionIndex > 0,
            canMoveDown = actionSheetSectionIndex < noteSections.lastIndex,
            onDismiss = {
                showSectionActionSheet = false
                actionSheetSectionIndex = -1
            },
            onRename = {
                renameSectionText = section.title
                showRenameSection = true
                showSectionActionSheet = false
            },
            onMoveUp = {
                val updated = moveNoteSectionUp(noteSections, actionSheetSectionIndex)
                noteSections.clear()
                noteSections.addAll(updated)
                if (selectedNoteSectionIndex == actionSheetSectionIndex) {
                    selectedNoteSectionIndex -= 1
                } else if (selectedNoteSectionIndex == actionSheetSectionIndex - 1) {
                    selectedNoteSectionIndex += 1
                }
                showSectionActionSheet = false
            },
            onMoveDown = {
                val updated = moveNoteSectionDown(noteSections, actionSheetSectionIndex)
                noteSections.clear()
                noteSections.addAll(updated)
                if (selectedNoteSectionIndex == actionSheetSectionIndex) {
                    selectedNoteSectionIndex += 1
                } else if (selectedNoteSectionIndex == actionSheetSectionIndex + 1) {
                    selectedNoteSectionIndex -= 1
                }
                showSectionActionSheet = false
            },
            onTogglePin = {
                val title = section.title
                val updated = if (title in pinnedNoteSectionTitles.value) {
                    pinnedNoteSectionTitles.value - title
                } else {
                    pinnedNoteSectionTitles.value + title
                }
                pinnedNoteSectionTitles.value = updated
                showSectionActionSheet = false
            },
            onDelete = { sectionDeletionTarget = actionSheetSectionIndex },
        )
    }
    sectionDeletionTarget?.let { idx ->
        CompactChoiceDialog(
            question = stringResource(R.string.delete_section_question, noteSections[idx].title),
            onDismiss = { sectionDeletionTarget = null },
            onYes = {
                noteSections.removeAt(idx)
                if (selectedNoteSectionIndex >= noteSections.size) {
                    selectedNoteSectionIndex = noteSections.lastIndex.coerceAtLeast(0)
                }
                showSectionActionSheet = false
                actionSheetSectionIndex = -1
                sectionDeletionTarget = null
            },
        )
    }
    if (showDiscardPrompt) {
        CompactChoiceDialog(
            question = stringResource(R.string.discard_question),
            onDismiss = { showDiscardPrompt = false },
            onYes = ::confirmDiscardAndLeave,
        )
    }
    if (showSaveChangesPrompt) {
        SaveChangesDialog(
            onDismiss = { showSaveChangesPrompt = false },
            onSave = ::saveAndLeave,
            onDiscard = ::discardNoteChangesAndLeave,
        )
    }
    KeepReaderScreenAwake(enabled = preferences.keepScreenOn)
    VolumePageTurnEffect(
        enabled = preferences.volumeButtonsTurnPages && !showSettings && !showNoteSearch,
        inverted = preferences.invertVolumeButtons,
        onPrevious = {
            coroutineScope.launch {
                scrollState.scrollTo((scrollState.value - editorHeightPx).coerceAtLeast(0))
            }
        },
        onNext = {
            coroutineScope.launch {
                scrollState.scrollTo((scrollState.value + editorHeightPx).coerceAtMost(scrollState.maxValue))
            }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .imePadding(),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = backgroundColor,
                titleContentColor = textColor,
            ),
            navigationIcon = {
                TextButton(onClick = ::discardAndLeave) {
                    Text(text = if (isEditable) stringResource(R.string.discard) else stringResource(R.string.library))
                }
            },
            title = {
                Column {
                    Text(
                        text = screen.document.title,
                        modifier = if (isEditable) {
                            Modifier.clickable {
                                renameText = screen.document.title
                                showRename = true
                            }
                        } else {
                            Modifier
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = screen.document.type.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showNoteSearch = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = if (isEditable) {
                            stringResource(R.string.search_note)
                        } else {
                            stringResource(R.string.search_document)
                        },
                        tint = textColor,
                    )
                }
                if (isEditable) {
                    TextButton(onClick = ::saveAndLeave) {
                        Text(text = stringResource(R.string.save))
                    }
                }
                IconButton(onClick = { onToggleBookmark(currentScrollFraction) }) {
                    Icon(
                        imageVector = if (currentBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (currentBookmarked) {
                            stringResource(R.string.remove_bookmark)
                        } else {
                            stringResource(R.string.add_bookmark)
                        },
                        tint = if (currentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            },
        )
        if (showNoteSearch) {
            NoteEditorSearchBar(
                query = noteSearchQuery,
                matchLabel = when {
                    noteSearchQuery.isBlank() -> ""
                    noteSearchMatches.isEmpty() -> "0 / 0"
                    else -> "${currentSearchIndex + 1} / ${noteSearchMatches.size}"
                },
                hasMatches = noteSearchMatches.isNotEmpty(),
                backgroundColor = backgroundColor,
                textColor = textColor,
                onQueryChange = { query ->
                    noteSearchQuery = query
                    noteSearchIndex = 0
                },
                onPrevious = {
                    selectNoteSearchMatch(if (currentSearchIndex < 0) 0 else currentSearchIndex - 1)
                },
                onNext = {
                    selectNoteSearchMatch(if (currentSearchIndex < 0) 0 else currentSearchIndex + 1)
                },
                onClose = ::closeNoteSearch,
            )
        }
        if (sectionedNotesEnabled) {
            NoteSectionBar(
                sections = noteSections.toList(),
                selectedIndex = selectedNoteSectionIndex.coerceIn(0, noteSections.lastIndex.coerceAtLeast(0)),
                expanded = showNoteSectionPicker,
                backgroundColor = backgroundColor,
                pinnedSectionTitles = pinnedNoteSectionTitles.value,
                onToggleExpanded = { showNoteSectionPicker = !showNoteSectionPicker },
                onSelectSection = ::switchNoteSection,
                onAddSection = ::addSectionAndSwitch,
                onSectionActions = { index ->
                    actionSheetSectionIndex = index
                    showSectionActionSheet = true
                },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .widthIn(max = 700.dp),
        ) {
            BasicTextField(
                value = noteValue,
                onValueChange = { nextValue ->
                    updateNoteValue(nextValue)
                },
                onTextLayout = { layoutResult ->
                    noteTextLayoutRef.value = layoutResult
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size -> editorHeightPx = size.height }
                    .verticalScroll(scrollState),
                readOnly = !isEditable,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontFamily = preferences.fontChoice.composeFontFamily(),
                    fontSize = preferences.fontSizeSp.sp,
                    lineHeight = (preferences.fontSizeSp * preferences.lineSpacing.coerceIn(1.2f, 2.4f)).sp,
                ),
                cursorBrush = SolidColor(textColor),
                visualTransformation = noteVisualTransformation,
            )
            if (isEditable && text.isBlank()) {
                Text(
                    text = stringResource(R.string.write_note_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor.copy(alpha = 0.45f),
                        fontFamily = preferences.fontChoice.composeFontFamily(),
                        fontSize = preferences.fontSizeSp.sp,
                        lineHeight = (preferences.fontSizeSp * preferences.lineSpacing.coerceIn(1.2f, 2.4f)).sp,
                    ),
                )
            }
            if (scrollState.maxValue > 0) {
                EpubSectionScrollThumb(
                    progressFraction = currentScrollFraction,
                    thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(2f)
                        .fillMaxHeight()
                        .width(18.dp)
                        .padding(end = 4.dp),
                )
            }
        }
        if (isEditable) {
            NoteEditorHelperBar(
                backgroundColor = backgroundColor,
                iconColor = textColor,
                canUndo = undoHistory.isNotEmpty(),
                canRedo = redoHistory.isNotEmpty(),
                onUndo = ::undoNoteChange,
                onRedo = ::redoNoteChange,
                onInsertTimestamp = ::insertTimestamp,
            )
        }
    }
}

