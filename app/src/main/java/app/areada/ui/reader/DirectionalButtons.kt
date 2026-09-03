package app.areada.ui.reader

import android.view.Choreographer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.areada.data.reader.ReaderButtonLayout
import kotlin.coroutines.resume

private const val HOLD_DURATION_MS = 500L
private const val TAP_SCROLL_FRACTION = 0.15f
private const val SCROLL_SPEED_PER_MS = 0.0025f
private const val HOLD_PAGE_INTERVAL_MS = 200L

private suspend fun awaitNextChoreographerFrame() {
    suspendCancellableCoroutine { cont ->
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        cont.invokeOnCancellation {
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }
}

private enum class ButtonSlot { PAGE_PREV, SCROLL_DOWN, SCROLL_UP, PAGE_NEXT }

private fun ReaderButtonLayout.toSlots(): List<ButtonSlot> = when (this) {
    ReaderButtonLayout.DEFAULT -> listOf(ButtonSlot.PAGE_PREV, ButtonSlot.SCROLL_DOWN, ButtonSlot.SCROLL_UP, ButtonSlot.PAGE_NEXT)
    ReaderButtonLayout.INVERTED -> listOf(ButtonSlot.PAGE_NEXT, ButtonSlot.SCROLL_UP, ButtonSlot.SCROLL_DOWN, ButtonSlot.PAGE_PREV)
    ReaderButtonLayout.SYMMETRIC -> listOf(ButtonSlot.SCROLL_DOWN, ButtonSlot.PAGE_PREV, ButtonSlot.PAGE_NEXT, ButtonSlot.SCROLL_UP)
    ReaderButtonLayout.VERTICAL -> listOf(ButtonSlot.SCROLL_UP, ButtonSlot.SCROLL_DOWN, ButtonSlot.PAGE_PREV, ButtonSlot.PAGE_NEXT)
    ReaderButtonLayout.VERTICAL_FIRST -> listOf(ButtonSlot.SCROLL_DOWN, ButtonSlot.SCROLL_UP, ButtonSlot.PAGE_NEXT, ButtonSlot.PAGE_PREV)
    ReaderButtonLayout.PAGE_FIRST -> listOf(ButtonSlot.PAGE_PREV, ButtonSlot.PAGE_NEXT, ButtonSlot.SCROLL_UP, ButtonSlot.SCROLL_DOWN)
}

@Composable
internal fun DirectionalButtons(
    onPageNext: () -> Unit,
    onPagePrevious: () -> Unit,
    onScrollChange: (deltaFraction: Float) -> Unit,
    modifier: Modifier = Modifier,
    invertScrolling: Boolean = true,
    buttonLayout: ReaderButtonLayout = ReaderButtonLayout.DEFAULT,
) {
    val currentOnPageNext = rememberUpdatedState(onPageNext)
    val currentOnPagePrevious = rememberUpdatedState(onPagePrevious)
    val currentOnScrollChange = rememberUpdatedState(onScrollChange)
    val currentInvertScrolling = rememberUpdatedState(invertScrolling)

    val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    val slots = remember(buttonLayout) { buttonLayout.toSlots() }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        slots.forEach { slot ->
            when (slot) {
                ButtonSlot.PAGE_PREV -> DirectionalButton(
                    modifier = Modifier.weight(1f),
                    onTap = { currentOnPagePrevious.value() },
                    onHoldTick = { currentOnPagePrevious.value() },
                    holdIntervalMs = HOLD_PAGE_INTERVAL_MS,
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = arrowColor,
                    bgColor = bgColor,
                )
                ButtonSlot.SCROLL_DOWN -> DirectionalButton(
                    modifier = Modifier.weight(1f),
                    onTap = {
                        currentOnScrollChange.value(
                            if (currentInvertScrolling.value) TAP_SCROLL_FRACTION else -TAP_SCROLL_FRACTION
                        )
                    },
                    onHoldTick = { dtMs ->
                        val frac = SCROLL_SPEED_PER_MS * dtMs
                        currentOnScrollChange.value(
                            if (currentInvertScrolling.value) frac else -frac
                        )
                    },
                    frameSynced = true,
                    icon = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Scroll down",
                    tint = arrowColor,
                    bgColor = bgColor,
                )
                ButtonSlot.SCROLL_UP -> DirectionalButton(
                    modifier = Modifier.weight(1f),
                    onTap = {
                        currentOnScrollChange.value(
                            if (currentInvertScrolling.value) -TAP_SCROLL_FRACTION else TAP_SCROLL_FRACTION
                        )
                    },
                    onHoldTick = { dtMs ->
                        val frac = SCROLL_SPEED_PER_MS * dtMs
                        currentOnScrollChange.value(
                            if (currentInvertScrolling.value) -frac else frac
                        )
                    },
                    frameSynced = true,
                    icon = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Scroll up",
                    tint = arrowColor,
                    bgColor = bgColor,
                )
                ButtonSlot.PAGE_NEXT -> DirectionalButton(
                    modifier = Modifier.weight(1f),
                    onTap = { currentOnPageNext.value() },
                    onHoldTick = { currentOnPageNext.value() },
                    holdIntervalMs = HOLD_PAGE_INTERVAL_MS,
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = arrowColor,
                    bgColor = bgColor,
                )
            }
        }
    }
}

@Composable
private fun DirectionalButton(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onHoldTick: ((Float) -> Unit)? = null,
    holdIntervalMs: Long = 200L,
    frameSynced: Boolean = false,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    bgColor: Color,
) {
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding && onHoldTick != null) {
            delay(HOLD_DURATION_MS)
            if (frameSynced) {
                var lastNs = System.nanoTime()
                while (isHolding) {
                    awaitNextChoreographerFrame()
                    val nowNs = System.nanoTime()
                    val dtMs = ((nowNs - lastNs) / 1_000_000.0).coerceIn(0.0, 100.0).toFloat()
                    lastNs = nowNs
                    onHoldTick(dtMs)
                }
            } else {
                var lastNs = System.nanoTime()
                while (isHolding) {
                    delay(holdIntervalMs)
                    val nowNs = System.nanoTime()
                    val dtMs = ((nowNs - lastNs) / 1_000_000.0).coerceIn(0.0, 100.0).toFloat()
                    lastNs = nowNs
                    onHoldTick(dtMs)
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
            .height(48.dp)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onTap()
                    isHolding = true

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val c = event.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        c.consume()
                    }

                    isHolding = false
                }
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (isHolding) 1f else 0.65f),
            modifier = Modifier
                .size(28.dp)
                .padding(10.dp),
        )
    }
}
