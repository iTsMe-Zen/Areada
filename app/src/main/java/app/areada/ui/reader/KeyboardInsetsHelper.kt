package app.areada.ui.reader

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberKeyboardHeightDp(): Dp {
    val heightPx = remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val density = LocalDensity.current

    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            val screenHeight = view.rootView.height
            val visibleHeight = r.bottom - r.top
            val keyboard = if (screenHeight - visibleHeight > screenHeight * 0.15) {
                screenHeight - visibleHeight
            } else {
                0
            }
            heightPx.intValue = keyboard
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return with(density) {
        heightPx.intValue.toDp()
    }
}
