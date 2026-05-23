package app.areada.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.areada.data.reader.ReaderThemeMode

private val LightReaderColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFF4F2EB),
    primaryContainer = Color(0xFFDDD9D2),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF59564E),
    onSecondary = Color(0xFFF7F4EC),
    background = Color(0xFFF4F2EB),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE8E4DA),
    onSurfaceVariant = Color(0xFF666157),
    outline = Color(0xFFD4CEC3),
)

private val SepiaReaderColors = lightColorScheme(
    primary = Color(0xFF2C241B),
    onPrimary = Color(0xFFFBF2E2),
    primaryContainer = Color(0xFFE7D8BD),
    onPrimaryContainer = Color(0xFF2C241B),
    secondary = Color(0xFF6B5A46),
    onSecondary = Color(0xFFFBF2E2),
    background = Color(0xFFF3E7CF),
    onBackground = Color(0xFF2C241B),
    surface = Color(0xFFFBF2E2),
    onSurface = Color(0xFF2C241B),
    surfaceVariant = Color(0xFFE7D8BD),
    onSurfaceVariant = Color(0xFF6E5B46),
    outline = Color(0xFFD3BFA4),
)

private val DarkReaderColors = darkColorScheme(
    primary = Color(0xFFF5F1E6),
    onPrimary = Color(0xFF0F0F10),
    primaryContainer = Color(0xFF34343A),
    onPrimaryContainer = Color(0xFFF5F1E6),
    secondary = Color(0xFFCFC8BA),
    onSecondary = Color(0xFF111111),
    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFF5F1E6),
    surface = Color(0xFF171719),
    onSurface = Color(0xFFF5F1E6),
    surfaceVariant = Color(0xFF242428),
    onSurfaceVariant = Color(0xFFAAA79D),
    outline = Color(0xFF34343A),
)

private val SageReaderColors = lightColorScheme(
    primary = Color(0xFF315E3B),
    onPrimary = Color(0xFFFAFFF6),
    primaryContainer = Color(0xFFDDE9D6),
    onPrimaryContainer = Color(0xFF172014),
    secondary = Color(0xFF52614B),
    onSecondary = Color(0xFFFAFFF6),
    background = Color(0xFFEEF4EA),
    onBackground = Color(0xFF172014),
    surface = Color(0xFFFAFFF6),
    onSurface = Color(0xFF172014),
    surfaceVariant = Color(0xFFDDE9D6),
    onSurfaceVariant = Color(0xFF5A6654),
    outline = Color(0xFFC8D7C0),
)

private val BlushReaderColors = lightColorScheme(
    primary = Color(0xFF8A3E4D),
    onPrimary = Color(0xFFFFF8FA),
    primaryContainer = Color(0xFFF0DDE2),
    onPrimaryContainer = Color(0xFF241719),
    secondary = Color(0xFF72565C),
    onSecondary = Color(0xFFFFF8FA),
    background = Color(0xFFF8EEF1),
    onBackground = Color(0xFF241719),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF241719),
    surfaceVariant = Color(0xFFF0DDE2),
    onSurfaceVariant = Color(0xFF6E5960),
    outline = Color(0xFFE0C7CD),
)

private val FlatShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun ReaderTheme(
    mode: ReaderThemeMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val useDarkSystem = isSystemInDarkTheme()

    val colors = when (mode) {
        ReaderThemeMode.LIGHT -> LightReaderColors
        ReaderThemeMode.SEPIA -> SepiaReaderColors
        ReaderThemeMode.DARK -> DarkReaderColors
        ReaderThemeMode.SAGE -> SageReaderColors
        ReaderThemeMode.BLUSH -> BlushReaderColors
        ReaderThemeMode.ANDROID -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDarkSystem -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            useDarkSystem -> DarkReaderColors
            else -> LightReaderColors
        }
    }

    val backgroundArgb = colors.background.toArgb()
    val useDarkSystemBarIcons = colors.background.luminance() > 0.5f

    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val window = activity.window

            window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
            window.statusBarColor = backgroundArgb
            window.navigationBarColor = backgroundArgb

            var flags = view.systemUiVisibility

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = if (useDarkSystemBarIcons) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = if (useDarkSystemBarIcons) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = backgroundArgb
            }

            view.systemUiVisibility = flags
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = FlatShapes,
        content = content,
    )
}
