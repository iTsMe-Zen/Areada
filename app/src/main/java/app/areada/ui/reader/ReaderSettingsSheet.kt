package app.areada.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.reader.ReaderFontChoice
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderOrientationMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRulerPositionMax
import app.areada.data.reader.ReaderRulerPositionMin
import app.areada.data.reader.ReaderThemeMode
import app.areada.data.reader.readingRulerPositionLabel
import app.areada.data.reader.sanitizeReadingRulerPosition
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    showReadingControls: Boolean = true,
    showLanguageSelector: Boolean = false,
    showGuideIconToggle: Boolean = false,
    showOpenPreviousChapterAtEnd: Boolean = false,
    onBookNoteClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var fontSizeDraft by rememberSaveable {
        mutableFloatStateOf(preferences.fontSizeSp.toFloat())
    }
    var lineSpacingDraft by rememberSaveable {
        mutableFloatStateOf(preferences.lineSpacing)
    }
    var pageMarginDraft by rememberSaveable {
        mutableFloatStateOf(preferences.pageMargin.toFloat())
    }
    var rulerPositionDraft by rememberSaveable {
        mutableFloatStateOf(preferences.readingRulerPosition)
    }
    var languageExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.50f).coerceAtLeast(300.dp)

    LaunchedEffect(preferences.fontSizeSp) {
        fontSizeDraft = preferences.fontSizeSp.toFloat()
    }
    LaunchedEffect(preferences.lineSpacing) {
        lineSpacingDraft = preferences.lineSpacing
    }
    LaunchedEffect(preferences.readingRulerPosition) {
        rulerPositionDraft = preferences.readingRulerPosition
    }
    LaunchedEffect(preferences.pageMargin) {
        pageMarginDraft = preferences.pageMargin.toFloat()
    }
    val localizedContext = LocalContext.current

    val consumeBottomOverScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return if (available.y > 0f) available else Offset.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .nestedScroll(consumeBottomOverScroll)
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .navigationBarsPadding()
                .padding(horizontal = SettingsSheetHorizontalPadding, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                onBookNoteClick?.let { openBookNote ->
                    Text(
                        text = stringResource(R.string.book_note),
                        modifier = Modifier
                            .clickable(onClick = openBookNote)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(title = stringResource(R.string.theme)) {
                SegmentedSettingGrid(
                    items = ReaderThemeMode.entries,
                    selected = preferences.themeMode,
                    label = { mode -> mode.displayLabel() },
                    onSelect = { mode -> onPreferencesChange(preferences.copy(themeMode = mode)) },
                )
            }
            if (showLanguageSelector) {
                SettingsSectionSpacer()
                LanguageSelector(
                    selected = preferences.languageMode,
                    expanded = languageExpanded,
                    onToggleExpanded = { languageExpanded = !languageExpanded },
                    onSelect = { mode -> onPreferencesChange(preferences.copy(languageMode = mode)) },
                )
            }
            if (showReadingControls) {
                SettingsSectionSpacer()
                SettingsSection(title = stringResource(R.string.orientation)) {
                    SegmentedSettingGrid(
                        items = ReaderOrientationMode.entries,
                        selected = preferences.orientationMode,
                        label = { mode -> mode.displayLabel() },
                        onSelect = { mode -> onPreferencesChange(preferences.copy(orientationMode = mode)) },
                    )
                }
            }
            if (showReadingControls) {
                SettingsSectionSpacer()
                SettingsSection(title = stringResource(R.string.font)) {
                    SegmentedSettingGrid(
                        items = ReaderFontChoice.entries,
                        selected = preferences.fontChoice,
                        label = { choice -> choice.displayLabel() },
                        onSelect = { choice -> onPreferencesChange(preferences.copy(fontChoice = choice)) },
                    )
                }
                SettingsSectionSpacer()
                SettingsSlider(
                    label = stringResource(R.string.font_size),
                    valueLabel = "${fontSizeDraft.roundToInt().coerceIn(12, 40)}sp",
                    value = fontSizeDraft,
                    onValueChange = { value -> fontSizeDraft = value },
                    onValueChangeFinished = {
                        onPreferencesChange(
                            preferences.copy(fontSizeSp = fontSizeDraft.roundToInt().coerceIn(12, 40)),
                        )
                    },
                    valueRange = 12f..40f,
                    steps = 27,
                )
                SettingsControlSpacer()
                SettingsSlider(
                    label = stringResource(R.string.line_spacing),
                    valueLabel = "${(lineSpacingDraft * 10f).roundToInt() / 10f}x",
                    value = lineSpacingDraft,
                    onValueChange = { value -> lineSpacingDraft = value },
                    onValueChangeFinished = {
                        onPreferencesChange(preferences.copy(lineSpacing = lineSpacingDraft.coerceIn(1.2f, 2.4f)))
                    },
                    valueRange = 1.2f..2.4f,
                    steps = 11,
                )
                SettingsControlSpacer()
                SettingsSection(title = stringResource(R.string.page_navigation)) {
                    SegmentedSettingGrid(
                        items = ReaderNavigationMode.entries,
                        selected = preferences.navigationMode,
                        label = { mode -> mode.displayLabel() },
                        onSelect = { mode ->
                            onPreferencesChange(preferences.copy(navigationMode = mode))
                        },
                    )
                }
                SettingsControlSpacer()
                SettingsSlider(
                    label = stringResource(R.string.page_margin_sides),
                    valueLabel = "${pageMarginDraft.roundToInt().coerceIn(0, 25)}",
                    value = pageMarginDraft,
                    onValueChange = { value -> pageMarginDraft = value },
                    onValueChangeFinished = {
                        onPreferencesChange(
                            preferences.copy(pageMargin = pageMarginDraft.roundToInt().coerceIn(0, 25)),
                        )
                    },
                    valueRange = 0f..25f,
                    steps = 10,
                )
                SettingsControlSpacer()
                CollapsibleSettingsSection(
                    title = stringResource(R.string.additional_settings),
                    initialExpanded = false,
                ) {
                    SettingsBinaryRow(
                        label = stringResource(R.string.reading_ruler),
                        checked = preferences.readingRuler,
                        onCheckedChange = { checked ->
                            onPreferencesChange(
                                preferences.copy(
                                    readingRuler = checked,
                                    readingRulerPosition = sanitizeReadingRulerPosition(rulerPositionDraft),
                                ),
                            )
                        },
                    )
                    if (preferences.readingRuler) {
                        SettingsControlSpacer()
                        SettingsSlider(
                            label = stringResource(R.string.ruler_position),
                            valueLabel = readingRulerPositionLabel(rulerPositionDraft),
                            value = rulerPositionDraft,
                            onValueChange = { value -> rulerPositionDraft = value },
                            onValueChangeFinished = {
                                val sanitized = sanitizeReadingRulerPosition(rulerPositionDraft)
                                rulerPositionDraft = sanitized
                                onPreferencesChange(preferences.copy(readingRulerPosition = sanitized))
                            },
                            valueRange = ReaderRulerPositionMin..ReaderRulerPositionMax,
                            steps = 13,
                        )
                    }
                    SettingsControlSpacer()
                    SettingsBinaryRow(
                        label = stringResource(R.string.keep_screen_on),
                        checked = preferences.keepScreenOn,
                        onCheckedChange = { checked -> onPreferencesChange(preferences.copy(keepScreenOn = checked)) },
                    )
                    SettingsControlSpacer()
                    SettingsBinaryRow(
                        label = stringResource(R.string.volume_buttons_turn_pages),
                        checked = preferences.volumeButtonsTurnPages,
                        onCheckedChange = { checked ->
                            onPreferencesChange(preferences.copy(volumeButtonsTurnPages = checked))
                        },
                    )
                    if (preferences.volumeButtonsTurnPages) {
                        SettingsControlSpacer()
                        SettingsBinaryRow(
                            label = stringResource(R.string.invert_volume_buttons),
                            checked = preferences.invertVolumeButtons,
                            onCheckedChange = { checked ->
                                onPreferencesChange(preferences.copy(invertVolumeButtons = checked))
                            },
                        )
                    }
                    if (showOpenPreviousChapterAtEnd) {
                        SettingsControlSpacer()
                        SettingsBinaryRow(
                            label = stringResource(R.string.open_previous_chapter_at_end),
                            checked = preferences.openPreviousChapterAtEnd,
                            onCheckedChange = { checked ->
                                onPreferencesChange(preferences.copy(openPreviousChapterAtEnd = checked))
                            },
                        )
                    }
                    SettingsControlSpacer()
                    SettingsBinaryRow(
                        label = stringResource(R.string.vibrate_on_page_turn),
                        checked = preferences.vibrateOnPageTurn,
                        onCheckedChange = { checked ->
                            onPreferencesChange(preferences.copy(vibrateOnPageTurn = checked))
                        },
                    )
                }
            }
            if (showGuideIconToggle) {
                SettingsSectionSpacer()
                SettingsBinaryRow(
                    label = stringResource(R.string.guide_icon),
                    checked = preferences.showGuideIcon,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(showGuideIcon = checked)) },
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            VersionLine()
            Spacer(modifier = Modifier.height(12.dp))
        }
        }
    }

}
