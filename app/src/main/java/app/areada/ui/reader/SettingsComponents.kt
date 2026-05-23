package app.areada.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.reader.ReaderFontChoice
import app.areada.data.reader.ReaderLanguageMode
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderOrientationMode
import app.areada.data.reader.ReaderThemeMode

internal val SettingsSheetHorizontalPadding = 24.dp
internal val SettingsButtonGap = 8.dp
internal const val SettingsGridColumns = 3

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun SettingsSectionSpacer() {
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
internal fun SettingsControlSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun <T> SegmentedSettingGrid(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    icon: @Composable (T) -> (@Composable (() -> Unit)?) = { null },
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SettingsButtonGap),
    ) {
        items.chunked(SettingsGridColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SettingsButtonGap),
            ) {
                rowItems.forEach { item ->
                    SettingChip(
                        label = label(item),
                        selected = item == selected,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f),
                        icon = icon(item),
                    )
                }
                repeat(SettingsGridColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun LanguageSelector(
    selected: ReaderLanguageMode,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (ReaderLanguageMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            SegmentedSettingGrid(
                items = ReaderLanguageMode.entries,
                selected = selected,
                label = { mode -> mode.displayLabel() },
                onSelect = onSelect,
            )
        }
    }
}

@Composable
internal fun SettingsSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsLabelValueRow(label = label, value = valueLabel)
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
internal fun SettingsBinaryRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsBinaryButton(
                label = stringResource(R.string.on),
                selected = checked,
                enabled = enabled,
                onClick = { onCheckedChange(true) },
            )
            SettingsBinaryButton(
                label = stringResource(R.string.off),
                selected = !checked,
                enabled = enabled,
                onClick = { onCheckedChange(false) },
            )
        }
    }
}

@Composable
internal fun SettingsBinaryButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier
            .width(52.dp)
            .height(34.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RectangleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun SettingsLabelValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun VersionLine() {
    val context = LocalContext.current
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }.getOrDefault("")
    }.ifBlank { "1.1.0" }

    Text(
        text = stringResource(R.string.version_areada, versionName),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ReaderThemeMode.displayLabel(): String =
    when (this) {
        ReaderThemeMode.LIGHT -> stringResource(R.string.theme_day)
        ReaderThemeMode.SEPIA -> stringResource(R.string.theme_sepia)
        ReaderThemeMode.DARK -> stringResource(R.string.theme_dark)
        ReaderThemeMode.SAGE -> stringResource(R.string.theme_sage)
        ReaderThemeMode.BLUSH -> stringResource(R.string.theme_blush)
        ReaderThemeMode.ANDROID -> stringResource(R.string.theme_system)
    }

@Composable
internal fun ReaderFontChoice.displayLabel(): String =
    when (this) {
        ReaderFontChoice.SERIF -> stringResource(R.string.font_serif)
        ReaderFontChoice.SANS -> stringResource(R.string.font_sans)
        ReaderFontChoice.MONO -> stringResource(R.string.font_mono)
    }

@Composable
internal fun ReaderOrientationMode.displayLabel(): String =
    when (this) {
        ReaderOrientationMode.Portrait -> stringResource(R.string.orientation_portrait)
        ReaderOrientationMode.Landscape -> stringResource(R.string.orientation_landscape)
        ReaderOrientationMode.FollowSystem -> stringResource(R.string.orientation_follow_system)
    }

@Composable
internal fun ReaderLanguageMode.displayLabel(): String =
    when (this) {
        ReaderLanguageMode.System -> stringResource(R.string.language_system)
        ReaderLanguageMode.English -> stringResource(R.string.language_english)
        ReaderLanguageMode.Nepali -> stringResource(R.string.language_nepali)
        ReaderLanguageMode.PortugueseBrazil -> stringResource(R.string.language_portuguese_brazil)
    }

@Composable
internal fun ReaderNavigationMode.displayLabel(): String =
    when (this) {
        ReaderNavigationMode.SWIPE -> stringResource(R.string.navigation_swipe)
        ReaderNavigationMode.TAP -> stringResource(R.string.navigation_tap)
        ReaderNavigationMode.BUTTONS -> stringResource(R.string.navigation_buttons)
    }




