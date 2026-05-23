package app.areada.data.reader

import kotlin.math.roundToInt

enum class ReaderThemeMode(val label: String) {
    LIGHT("Day"),
    SEPIA("Sepia"),
    DARK("Dark"),
    SAGE("Sage"),
    BLUSH("Blush"),
    ANDROID("System"),
}

enum class ReaderFontChoice(
    val label: String,
    val cssFamily: String,
) {
    SERIF("Serif", "Georgia, 'Times New Roman', serif"),
    SANS("Sans", "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"),
    MONO("Mono", "ui-monospace, 'SFMono-Regular', 'Cascadia Mono', 'Courier New', monospace"),
}

enum class ReaderOrientationMode(val label: String) {
    Portrait("Portrait"),
    Landscape("Landscape"),
    FollowSystem("Follow System"),
}

fun readerOrientationModeFromName(name: String?): ReaderOrientationMode =
    ReaderOrientationMode.entries.firstOrNull { mode -> mode.name == name } ?: ReaderOrientationMode.FollowSystem

enum class ReaderNavigationMode(val label: String) {
    SWIPE("Swipe"),
    TAP("Tap"),
    BUTTONS("Buttons"),
}

fun readerNavigationModeFromName(name: String?): ReaderNavigationMode =
    ReaderNavigationMode.entries.firstOrNull { mode -> mode.name == name } ?: ReaderNavigationMode.SWIPE

enum class ReaderLanguageMode(
    val localeTag: String?,
) {
    System(null),
    English("en"),
    Nepali("ne"),
    PortugueseBrazil("pt-BR"),
}

fun readerLanguageModeFromName(name: String?): ReaderLanguageMode =
    ReaderLanguageMode.entries.firstOrNull { mode -> mode.name == name } ?: ReaderLanguageMode.System

const val ReaderRulerPositionDefault = 0.50f
const val ReaderRulerPositionMin = 0.15f
const val ReaderRulerPositionMax = 0.85f

data class ReaderPreferences(
    val themeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
    val fontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
    val languageMode: ReaderLanguageMode = ReaderLanguageMode.System,
    val orientationMode: ReaderOrientationMode = ReaderOrientationMode.FollowSystem,
    val navigationMode: ReaderNavigationMode = ReaderNavigationMode.SWIPE,
    val fontSizeSp: Int = 18,
    val lineSpacing: Float = 1.7f,
    val keepScreenOn: Boolean = false,
    val volumeButtonsTurnPages: Boolean = false,
    val invertVolumeButtons: Boolean = false,
    val readingRuler: Boolean = false,
    val readingRulerPosition: Float = ReaderRulerPositionDefault,
)

fun sanitizeReaderPreferences(preferences: ReaderPreferences): ReaderPreferences =
    preferences.copy(
        fontSizeSp = preferences.fontSizeSp.coerceIn(14, 30),
        lineSpacing = preferences.lineSpacing.coerceIn(1.2f, 2.4f),
        readingRulerPosition = sanitizeReadingRulerPosition(preferences.readingRulerPosition),
    )

fun sanitizeReadingRulerPosition(position: Float): Float =
    ((position * 20f).roundToInt() / 20f).coerceIn(ReaderRulerPositionMin, ReaderRulerPositionMax)

fun readingRulerPositionLabel(position: Float): String =
    "${(sanitizeReadingRulerPosition(position) * 100f).roundToInt()}%"

data class ReaderRenderPalette(
    val backgroundHex: String,
    val surfaceHex: String,
    val textHex: String,
    val mutedHex: String,
    val accentHex: String,
)

fun ReaderThemeMode.renderPalette(): ReaderRenderPalette = when (this) {
    ReaderThemeMode.LIGHT -> ReaderRenderPalette(
        backgroundHex = "#F6F4EE",
        surfaceHex = "#FFFCF7",
        textHex = "#111111",
        mutedHex = "#D6D1C6",
        accentHex = "#6F4BB8",
    )

    ReaderThemeMode.SEPIA -> ReaderRenderPalette(
        backgroundHex = "#F3E7CF",
        surfaceHex = "#FBF2E2",
        textHex = "#2C241B",
        mutedHex = "#D3BFA4",
        accentHex = "#744BA2",
    )

    ReaderThemeMode.DARK -> ReaderRenderPalette(
        backgroundHex = "#0F0F10",
        surfaceHex = "#171719",
        textHex = "#F5F1E6",
        mutedHex = "#34343A",
        accentHex = "#C4A7FF",
    )

    ReaderThemeMode.SAGE -> ReaderRenderPalette(
        backgroundHex = "#EEF4EA",
        surfaceHex = "#FAFFF6",
        textHex = "#172014",
        mutedHex = "#C8D7C0",
        accentHex = "#6552A8",
    )

    ReaderThemeMode.BLUSH -> ReaderRenderPalette(
        backgroundHex = "#F8EEF1",
        surfaceHex = "#FFF8FA",
        textHex = "#241719",
        mutedHex = "#E0C7CD",
        accentHex = "#8B4BB3",
    )

    ReaderThemeMode.ANDROID -> ReaderRenderPalette(
        backgroundHex = "#F4F2EB",
        surfaceHex = "#FFFCF7",
        textHex = "#111111",
        mutedHex = "#D4CEC3",
        accentHex = "#6F4BB8",
    )
}
