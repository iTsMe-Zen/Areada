package app.areada.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.areada.R
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySearchResultType
import app.areada.data.reader.ReaderFontChoice
import app.areada.data.reader.ReaderOrientationMode
import app.areada.data.reader.ReaderRenderPalette
import app.areada.data.reader.ReaderThemeMode
import app.areada.data.reader.renderPalette
import app.areada.reader.epub.EpubChapter
import java.net.URI

@Composable
internal fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .offset(y = 5.dp)
            .size(40.dp),
        content = content,
    )
}

@Composable
internal fun KeepReaderScreenAwake(enabled: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity()

    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (enabled) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
internal fun ReaderOrientationEffect(
    mode: ReaderOrientationMode,
    enabled: Boolean,
) {
    val view = LocalView.current
    val activity = view.context.findActivity()

    DisposableEffect(activity, mode, enabled) {
        if (activity == null) {
            return@DisposableEffect onDispose {}
        }

        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = if (enabled) {
            mode.toRequestedOrientation()
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            activity.requestedOrientation = previousOrientation
        }
    }
}

@Composable
internal fun AppWindowBackgroundEffect() {
    val view = LocalView.current
    val activity = view.context.findActivity()
    val background = MaterialTheme.colorScheme.background
    val backgroundArgb = background.toArgb()
    val useLightBars = background.luminance() > 0.5f

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val decorView = window.decorView

        window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
        decorView.setBackgroundColor(backgroundArgb)
        applyWindowBarColors(window, decorView, backgroundArgb, useLightBars)
    }
}

@Composable
internal fun VolumePageTurnEffect(
    enabled: Boolean,
    inverted: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val view = LocalView.current
    val host = view.context.findActivity() as? VolumePageTurnHost
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)

    DisposableEffect(host, enabled, inverted) {
        if (enabled && host != null) {
            host.setVolumePageTurnHandler { volumeUp ->
                val goNext = if (inverted) volumeUp else !volumeUp
                if (goNext) {
                    latestNext()
                } else {
                    latestPrevious()
                }
                true
            }
        } else {
            host?.setVolumePageTurnHandler(null)
        }

        onDispose {
            if (enabled) {
                host?.setVolumePageTurnHandler(null)
            }
        }
    }
}

@Composable
internal fun ReaderStatusBarHidden(hidden: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity()
    val background = MaterialTheme.colorScheme.background
    val backgroundArgb = background.toArgb()
    val useLightBars = background.luminance() > 0.5f

    DisposableEffect(activity, hidden, backgroundArgb, useLightBars) {
        val window = activity?.window
        val decorView = window?.decorView
        if (window == null || decorView == null) {
            return@DisposableEffect onDispose {}
        }

        @Suppress("DEPRECATION")
        val previousSystemUiVisibility = decorView.systemUiVisibility

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) {
                window.insetsController?.hide(WindowInsets.Type.systemBars())
            } else {
                window.insetsController?.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = if (hidden) {
                previousSystemUiVisibility or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            } else {
                previousSystemUiVisibility and
                    View.SYSTEM_UI_FLAG_FULLSCREEN.inv() and
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv() and
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
            }
        }

        window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
        decorView.setBackgroundColor(backgroundArgb)
        applyWindowBarColors(window, decorView, backgroundArgb, useLightBars)

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = previousSystemUiVisibility
            }

            window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
            decorView.setBackgroundColor(backgroundArgb)
            applyWindowBarColors(window, decorView, backgroundArgb, useLightBars)
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal fun ReaderOrientationMode.toRequestedOrientation(): Int =
    when (this) {
        ReaderOrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ReaderOrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ReaderOrientationMode.FollowSystem -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

@Composable
internal fun rememberReaderRenderPalette(mode: ReaderThemeMode): ReaderRenderPalette =
    if (mode == ReaderThemeMode.ANDROID) {
        val colors = MaterialTheme.colorScheme
        ReaderRenderPalette(
            backgroundHex = colors.background.toCssHex(),
            surfaceHex = colors.surface.toCssHex(),
            textHex = colors.onBackground.toCssHex(),
            mutedHex = colors.outline.toCssHex(),
            accentHex = colors.primary.toCssHex(),
            onSurfaceVariantHex = colors.onSurfaceVariant.toCssHex(),
        )
    } else {
        mode.renderPalette()
    }

internal fun Color.toCssHex(): String =
    "#%06X".format(0xFFFFFF and toArgb())

internal fun ReaderFontChoice.composeFontFamily(): FontFamily =
    when (this) {
        ReaderFontChoice.SERIF -> FontFamily.Serif
        ReaderFontChoice.SANS -> FontFamily.SansSerif
        ReaderFontChoice.MONO -> FontFamily.Monospace
    }

@Composable
internal fun LibrarySearchResult.searchSubtitle(): String {
    val parentPath = relativePath.substringBeforeLast('/', "")
    val location = if (parentPath.isBlank()) rootName else "$rootName / $parentPath"
    return when (type) {
        LibrarySearchResultType.FOLDER -> stringResource(R.string.folder_in_location, location)
        LibrarySearchResultType.BOOK -> stringResource(
            R.string.file_type_in_location,
            documentType?.name ?: "FILE",
            location,
        )
    }
}

internal fun EpubChapter.matchesLocalHref(urlString: String): Boolean {
    val target = urlString.substringBefore('#')
    if (target.isBlank()) {
        return false
    }
    val chapterUrl = file.toURI().toString().substringBefore('#')
    if (target == chapterUrl) {
        return true
    }

    return runCatching {
        java.io.File(URI(target)).canonicalFile == file.canonicalFile
    }.getOrDefault(false)
}

internal fun displayError(
    throwable: Throwable,
    fallback: String,
): String {
    val message = throwable.message.orEmpty()
    return when {
        message.contains("Invalid or unsupported EPUB", ignoreCase = true) -> "Invalid or unsupported EPUB file."
        message.contains("readable chapters", ignoreCase = true) -> "This EPUB does not contain readable chapters."
        message.contains("/data/", ignoreCase = true) ||
            message.contains("ENOENT", ignoreCase = true) ||
            message.contains("No such file", ignoreCase = true) ||
            message.contains("cache/", ignoreCase = true) -> fallback
        message.isBlank() -> fallback
        else -> message
    }
}

internal fun openExternalLinkWithChooser(
    context: Context,
    uri: Uri,
) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.open_link_with))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_app_can_open_link), Toast.LENGTH_SHORT).show()
    }
}

private fun applyWindowBarColors(
    window: android.view.Window,
    decorView: View,
    backgroundArgb: Int,
    useLightBars: Boolean,
) {
    @Suppress("DEPRECATION")
    window.statusBarColor = backgroundArgb

    @Suppress("DEPRECATION")
    window.navigationBarColor = backgroundArgb

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        window.isStatusBarContrastEnforced = false

        @Suppress("DEPRECATION")
        window.isNavigationBarContrastEnforced = false

        @Suppress("DEPRECATION")
        window.navigationBarDividerColor = backgroundArgb
    }

    WindowCompat.getInsetsController(window, decorView).apply {
        isAppearanceLightStatusBars = useLightBars
        isAppearanceLightNavigationBars = useLightBars
    }
}
