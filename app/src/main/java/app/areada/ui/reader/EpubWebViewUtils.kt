package app.areada.ui.reader

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
internal fun restoreWebViewScroll(
    webView: WebView,
    fraction: Float,
) {
    if (fraction <= 0f) {
        return
    }

    val restore = {
        val fullHeight = (webView.contentHeight * webView.scale).toInt()
        val maxScroll = (fullHeight - webView.height).coerceAtLeast(0)
        if (maxScroll > 0) {
            scrollWebViewToProgress(webView, fraction)
        }
    }

    webView.post(restore)
    webView.postDelayed(restore, 150L)
}

@Suppress("DEPRECATION")
internal fun scrollWebViewToProgress(
    webView: WebView,
    progress: Float,
) {
    val fullHeight = (webView.contentHeight * webView.scale).toInt()
    val maxScroll = (fullHeight - webView.height).coerceAtLeast(0)
    val targetY = (maxScroll * progress.coerceIn(0f, 1f)).roundToInt()
    webView.scrollTo(webView.scrollX, targetY)
}

internal fun applyEpubScrollRequest(
    webView: WebView,
    progress: Float,
) {
    val scroll = {
        scrollWebViewToProgress(webView, progress)
    }
    scroll()
    webView.post(scroll)
    webView.postDelayed(scroll, 80L)
}

@Suppress("DEPRECATION")
internal fun epubWebViewScrollState(webView: WebView): Pair<Float, Boolean> {
    val fullHeight = webView.contentHeight * webView.scale
    val maxScroll = (fullHeight - webView.height).coerceAtLeast(0f)
    if (maxScroll <= 1f) {
        return 0f to false
    }
    return (webView.scrollY / maxScroll).coerceIn(0f, 1f) to true
}

@Suppress("DEPRECATION")
internal fun resetWebViewZoom(webView: WebView) {
    val currentScale = webView.scale.takeIf { it > 0f } ?: 1f
    val zoomFactor = (1f / currentScale).coerceIn(0.2f, 5f)
    if (abs(currentScale - 1f) > 0.02f) {
        webView.zoomBy(zoomFactor)
    }
}

@Suppress("DEPRECATION")
internal fun scrollByWebView(webView: WebView, deltaX: Int, deltaY: Int) {
    webView.scrollBy(deltaX, deltaY)
}

internal class NoteBridge(
    private val onNoteOpen: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun open(note: String?) {
        val cleanNote = note?.trim().orEmpty()
        if (cleanNote.isBlank()) {
            return
        }

        mainHandler.post {
            onNoteOpen(cleanNote)
        }
    }
}

internal fun injectNoteHandler(webView: WebView) {
    webView.evaluateJavascript(
        """
        (function() {
          if (window.__areadaNoteHandlerInstalled) return;
          window.__areadaNoteHandlerInstalled = true;

          function findAnchor(start) {
            var node = start;
            while (node && node.tagName !== 'A') {
              node = node.parentElement;
            }
            return node;
          }

          function noteFor(targetId, fallback) {
            if (!targetId) return fallback || '';
            var node = document.getElementById(targetId) ||
              document.querySelector('[name="' + targetId.replace(/"/g, '\\"') + '"]');
            if (!node) return fallback || '';
            return (node.innerText || node.textContent || fallback || '').trim();
          }

          document.addEventListener('click', function(event) {
            var anchor = findAnchor(event.target);
            if (!anchor) return;

            var href = anchor.getAttribute('href') || '';
            var hash = '';
            try {
              hash = new URL(href, window.location.href).hash || '';
            } catch (error) {
              var hashIndex = href.indexOf('#');
              if (hashIndex >= 0) hash = href.substring(hashIndex);
            }
            if (!hash || hash.length < 2) return;

            var targetId = decodeURIComponent(hash.substring(1));
            var fallback = (anchor.getAttribute('title') || anchor.innerText || anchor.textContent || '').trim();
            var note = noteFor(targetId, fallback);
            if (note && window.AreadaNote) {
              event.preventDefault();
              window.AreadaNote.open(note);
            }
          }, true);
        })();
        """.trimIndent(),
        null,
    )
}

internal fun openChapterNote(
    webView: WebView,
    fragment: String,
    onNoteOpen: (String) -> Unit,
) {
    val quotedFragment = JSONObject.quote(fragment)
    val script = """
        (function() {
          var id = $quotedFragment;
          var node = document.getElementById(id) || document.querySelector('[name="' + id.replace(/"/g, '\\"') + '"]');
          return node ? node.innerText : '';
        })();
    """.trimIndent()

    webView.evaluateJavascript(script) { result ->
        val note = decodeJavascriptString(result).trim()
        if (note.isNotBlank()) {
            onNoteOpen(note)
        }
    }
}

internal fun decodeJavascriptString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") {
        return ""
    }

    return runCatching {
        JSONArray("[$value]").optString(0)
    }.getOrDefault("")
}
