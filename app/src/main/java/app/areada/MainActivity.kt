package app.areada

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import app.areada.data.reader.ReaderStateStore
import app.areada.data.reader.ReaderThemeMode
import androidx.compose.runtime.mutableStateOf
import app.areada.ui.AreadaApp
import app.areada.ui.reader.VolumePageTurnHost

class MainActivity : ComponentActivity(), VolumePageTurnHost {
    private val externalOpenUri = mutableStateOf<Uri?>(null)
    private var volumePageTurnHandler: ((volumeUp: Boolean) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalOpenUri.value = viewUriFrom(intent)
        enableEdgeToEdge()
        applyLaunchWindowColors()
        setContent {
            AreadaApp(
                externalOpenUri = externalOpenUri.value,
                onExternalOpenHandled = { externalOpenUri.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalOpenUri.value = viewUriFrom(intent)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = volumePageTurnHandler
        if (
            handler != null &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> if (handler(true)) return true
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (handler(false)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun setVolumePageTurnHandler(handler: ((volumeUp: Boolean) -> Boolean)?) {
        volumePageTurnHandler = handler
    }

    private fun viewUriFrom(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        val uri = intent.data ?: return null
        return when (uri.scheme?.lowercase()) {
            "content", "file" -> {
                persistIncomingReadPermission(intent, uri)
                uri
            }
            else -> null
        }
    }

    private fun persistIncomingReadPermission(
        intent: Intent,
        uri: Uri,
    ) {
        if (uri.scheme?.lowercase() != "content") {
            return
        }
        if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLaunchWindowColors() {
        val themeMode = ReaderStateStore.loadPreferences(this).themeMode
        val dark = themeMode == ReaderThemeMode.DARK ||
            (themeMode == ReaderThemeMode.ANDROID && isSystemInDarkMode())
        val background = when (themeMode) {
            ReaderThemeMode.DARK -> Color.rgb(0x0F, 0x0F, 0x10)
            ReaderThemeMode.SEPIA -> Color.rgb(0xF3, 0xE7, 0xCF)
            ReaderThemeMode.SAGE -> Color.rgb(0xEE, 0xF4, 0xEA)
            ReaderThemeMode.BLUSH -> Color.rgb(0xF8, 0xEE, 0xF1)
            ReaderThemeMode.ANDROID -> if (dark) {
                Color.rgb(0x0F, 0x0F, 0x10)
            } else {
                Color.rgb(0xF4, 0xF2, 0xEB)
            }
            ReaderThemeMode.LIGHT -> Color.rgb(0xF4, 0xF2, 0xEB)
        }
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun isSystemInDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
