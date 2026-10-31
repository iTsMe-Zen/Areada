package app.areada.reader

import app.areada.data.reader.ReaderPreferences

private val NoHyphenationSelector = listOf("pre", "code", "h1", "h2", "h3", "h4", "h5", "h6")
    .joinToString(", ") { tag -> "$tag, $tag *" }

// Single-line, so it can be embedded into trimIndent() templates.
internal fun readerHyphenationCss(preferences: ReaderPreferences): String =
    if (preferences.hyphenation) {
        "body, body * { -webkit-hyphens: auto !important; hyphens: auto !important; }" +
            " $NoHyphenationSelector { -webkit-hyphens: manual !important; hyphens: manual !important; }"
    } else {
        ""
    }

// Plain CSS can't override book styles without breaking centered/right-aligned blocks,
// so the script changes only the justified ones.
internal fun readerTextAlignScript(preferences: ReaderPreferences): String {
    if (!preferences.disableJustification) {
        return ""
    }
    return """
        (function () {
          var nodes = [document.body].concat(Array.prototype.slice.call(document.body.getElementsByTagName('*')));
          nodes
            .filter(function (node) { return node.style && getComputedStyle(node).textAlign === 'justify'; })
            .forEach(function (node) { node.style.setProperty('text-align', 'start', 'important'); });
        })();
    """.trimIndent()
}
