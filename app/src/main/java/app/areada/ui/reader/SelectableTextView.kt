package app.areada.ui.reader

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@Composable
internal fun SelectableTextView(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val textColorArgb = style.color.toArgb()
    val fontSizePx = with(LocalDensity.current) { style.fontSize.roundToPx() }
    val lineHeightPx = with(LocalDensity.current) { style.lineHeight.roundToPx() }
    val fontFamily = style.fontFamily
    val fontWeight = style.fontWeight

    val androidTypeface = remember(fontFamily, fontWeight) {
        fontFamily?.let { fam ->
            val name = fam.toString().substringAfter(".").substringBefore("@")
            android.graphics.Typeface.create(
                name.ifBlank { "sans-serif" },
                when (fontWeight) {
                    androidx.compose.ui.text.font.FontWeight.Bold -> android.graphics.Typeface.BOLD
                    androidx.compose.ui.text.font.FontWeight.Normal -> android.graphics.Typeface.NORMAL
                    else -> android.graphics.Typeface.NORMAL
                },
            )
        } ?: android.graphics.Typeface.DEFAULT
    }

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                gravity = Gravity.TOP or Gravity.START
                setLineSpacing(0f, 1f)
            }
        },
        update = { textView ->
            textView.text = text
            textView.setTextColor(textColorArgb)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx.toFloat())
            textView.setLineSpacing(0f, style.lineHeight.value)
            textView.typeface = androidTypeface
        },
        modifier = modifier,
    )
}
