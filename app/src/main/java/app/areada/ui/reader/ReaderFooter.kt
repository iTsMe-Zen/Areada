package app.areada.ui.reader

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ReaderFooter(
    leftLabel: String,
    rightLabel: String,
    centerLabel: String,
    leftEnabled: Boolean,
    rightEnabled: Boolean,
    onLeft: () -> Unit,
    onCenter: (() -> Unit)? = null,
    onRight: () -> Unit,
    progressFraction: Float? = null,
    progressPercentFraction: Float? = null,
    progressKey: Any? = null,
    onProgressScrubbed: ((Float) -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (progressFraction != null && onProgressScrubbed != null) {
                ReaderFooterProgressTrack(
                    progressFraction = progressFraction,
                    progressKey = progressKey,
                    onProgressScrubbed = onProgressScrubbed,
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TextButton(onClick = onLeft, enabled = leftEnabled) {
                        Text(text = leftLabel)
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onCenter == null) {
                        ReaderFooterCenterLabel(
                            centerLabel = centerLabel,
                            progressPercentFraction = progressPercentFraction,
                        )
                    } else {
                        TextButton(onClick = onCenter) {
                            ReaderFooterCenterLabel(
                                centerLabel = centerLabel,
                                progressPercentFraction = progressPercentFraction,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(onClick = onRight, enabled = rightEnabled) {
                        Text(text = rightLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderFooterCenterLabel(
    centerLabel: String,
    progressPercentFraction: Float?,
) {
    val cleanProgressPercent = progressPercentFraction
        ?.takeIf { value -> value.isFinite() }
        ?.coerceIn(0f, 1f)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = centerLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (cleanProgressPercent != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${(cleanProgressPercent * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ReaderFooterProgressTrack(
    progressFraction: Float,
    progressKey: Any?,
    onProgressScrubbed: (Float) -> Unit,
) {
    val latestOnProgressScrubbed by rememberUpdatedState(onProgressScrubbed)
    val releaseHandler = remember(progressKey) {
        Handler(Looper.getMainLooper())
    }
    var dragProgress by remember(progressKey) {
        mutableStateOf<Float?>(null)
    }

    DisposableEffect(progressKey) {
        onDispose {
            releaseHandler.removeCallbacksAndMessages(null)
        }
    }

    val shownProgress = (dragProgress ?: progressFraction).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    val dotColor = MaterialTheme.colorScheme.primary
    val handleSize = 10.dp

    fun updateProgress(progress: Float) {
        val cleanProgress = progress.coerceIn(0f, 1f)
        releaseHandler.removeCallbacksAndMessages(null)
        dragProgress = cleanProgress
        latestOnProgressScrubbed(cleanProgress)
    }

    fun finishScrub() {
        val finalProgress = dragProgress
        if (finalProgress != null) {
            latestOnProgressScrubbed(finalProgress)
        }

        releaseHandler.removeCallbacksAndMessages(null)
        releaseHandler.postDelayed(
            {
                dragProgress = null
            },
            220L,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(progressKey) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0) {
                            updateProgress(offset.x / size.width.toFloat())
                        }
                    },
                    onDrag = { change, _ ->
                        if (size.width > 0) {
                            updateProgress(change.position.x / size.width.toFloat())
                        }
                        change.consume()
                    },
                    onDragCancel = {
                        releaseHandler.removeCallbacksAndMessages(null)
                        dragProgress = null
                    },
                    onDragEnd = {
                        finishScrub()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = if (maxWidth > handleSize) maxWidth - handleSize else 0.dp
        val labelWidth = 40.dp
        val labelTravel = if (maxWidth > labelWidth) maxWidth - labelWidth else 0.dp
        Text(
            text = "${(shownProgress * 100f).roundToInt()}%",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = labelTravel * shownProgress)
                .width(labelWidth),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 5.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = travel * shownProgress)
                .size(handleSize)
                .background(dotColor, CircleShape),
        )
    }
}
