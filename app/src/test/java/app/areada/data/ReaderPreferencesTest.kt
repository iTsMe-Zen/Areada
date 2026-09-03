package app.areada.data

import app.areada.data.reader.ReaderLanguageMode
import app.areada.data.reader.ReaderOrientationMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRulerPositionDefault
import app.areada.data.reader.ReaderRulerPositionMax
import app.areada.data.reader.ReaderRulerPositionMin
import app.areada.data.reader.readerLanguageModeFromName
import app.areada.data.reader.readerOrientationModeFromName
import app.areada.data.reader.readingRulerPositionLabel
import app.areada.data.reader.sanitizeReaderPreferences
import app.areada.data.reader.sanitizeReadingRulerPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreferencesTest {
    @Test
    fun orientationModeDefaultsToFollowSystem() {
        assertEquals(ReaderOrientationMode.FollowSystem, ReaderPreferences().orientationMode)
    }

    @Test
    fun orientationModeRestoresFromPersistedName() {
        assertEquals(ReaderOrientationMode.Portrait, readerOrientationModeFromName("Portrait"))
        assertEquals(ReaderOrientationMode.Landscape, readerOrientationModeFromName("Landscape"))
        assertEquals(ReaderOrientationMode.FollowSystem, readerOrientationModeFromName("FollowSystem"))
    }

    @Test
    fun orientationModeFallsBackForUnknownName() {
        assertEquals(ReaderOrientationMode.FollowSystem, readerOrientationModeFromName(""))
        assertEquals(ReaderOrientationMode.FollowSystem, readerOrientationModeFromName("SomethingElse"))
    }

    @Test
    fun languageModeDefaultsToSystem() {
        assertEquals(ReaderLanguageMode.System, ReaderPreferences().languageMode)
    }

    @Test
    fun languageModeRestoresFromPersistedName() {
        assertEquals(ReaderLanguageMode.System, readerLanguageModeFromName("System"))
        assertEquals(ReaderLanguageMode.English, readerLanguageModeFromName("English"))
        assertEquals(ReaderLanguageMode.Russian, readerLanguageModeFromName("Russian"))
        assertEquals(ReaderLanguageMode.Spanish, readerLanguageModeFromName("Spanish"))
        assertEquals(ReaderLanguageMode.Nepali, readerLanguageModeFromName("Nepali"))
        assertEquals(ReaderLanguageMode.PortugueseBrazil, readerLanguageModeFromName("PortugueseBrazil"))
        assertEquals(ReaderLanguageMode.ChineseSimplified, readerLanguageModeFromName("ChineseSimplified"))
    }

    @Test
    fun languageModeFallsBackForUnknownName() {
        assertEquals(ReaderLanguageMode.System, readerLanguageModeFromName(""))
        assertEquals(ReaderLanguageMode.System, readerLanguageModeFromName("UnknownLanguage"))
    }

    @Test
    fun comfortPreferencesDefaultOff() {
        assertEquals(false, ReaderPreferences().readingRuler)
        assertEquals(ReaderRulerPositionDefault, ReaderPreferences().readingRulerPosition, 0.0001f)
    }

    @Test
    fun sanitizePreferencesClampsDisplayValuesAndKeepsRulerValues() {
        val sanitized = sanitizeReaderPreferences(
            ReaderPreferences(
                fontSizeSp = 99,
                lineSpacing = 9f,
                readingRuler = true,
                readingRulerPosition = 2f,
            ),
        )

        assertEquals(40, sanitized.fontSizeSp)
        assertEquals(2.4f, sanitized.lineSpacing, 0.0001f)
        assertEquals(true, sanitized.readingRuler)
        assertEquals(ReaderRulerPositionMax, sanitized.readingRulerPosition, 0.0001f)
    }

    @Test
    fun readingRulerPositionSnapsToFivePercentSteps() {
        assertEquals(ReaderRulerPositionMin, sanitizeReadingRulerPosition(-1f), 0.0001f)
        assertEquals(0.35f, sanitizeReadingRulerPosition(0.33f), 0.0001f)
        assertEquals(ReaderRulerPositionMax, sanitizeReadingRulerPosition(1f), 0.0001f)
    }

    @Test
    fun readingRulerPositionLabelUsesPercentValues() {
        assertEquals("50%", readingRulerPositionLabel(0.52f))
    }
}
