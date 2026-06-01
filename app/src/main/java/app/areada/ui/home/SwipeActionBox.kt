package app.areada.ui.home

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActionBox(
    actionLabel: String,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    actionContainerColor: Color = MaterialTheme.colorScheme.primary,
    actionContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onSwipeEndToStart: (() -> Unit)? = null,
    endToStartLabel: String = "Select",
    endToStartContainerColor: Color = MaterialTheme.colorScheme.secondary,
    endToStartContentColor: Color = MaterialTheme.colorScheme.onSecondary,
    content: @Composable () -> Unit,
) {
    val endToStartEnabled = onSwipeEndToStart != null
    val currentOnSwipe = rememberUpdatedState(onSwipe)
    val currentOnSwipeEndToStart = rememberUpdatedState(onSwipeEndToStart)
    var lastEndToStartFiredAt by remember { mutableLongStateOf(0L) }
    var lastStartToEndFiredAt by remember { mutableLongStateOf(0L) }
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.45f },
        confirmValueChange = { value ->
            val now = SystemClock.uptimeMillis()
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (now - lastStartToEndFiredAt >= 400L) {
                        lastStartToEndFiredAt = now
                        currentOnSwipe.value()
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (now - lastEndToStartFiredAt >= 400L) {
                        lastEndToStartFiredAt = now
                        currentOnSwipeEndToStart.value?.invoke()
                    }
                }
                SwipeToDismissBoxValue.Settled -> {}
            }
            false
        },
    )

    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = endToStartEnabled,
        backgroundContent = {
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    shape = RectangleShape,
                    color = actionContainerColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = actionLabel,
                            color = actionContentColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (endToStartEnabled) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        shape = RectangleShape,
                        color = endToStartContainerColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = endToStartLabel,
                                color = endToStartContentColor,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
        content = {
            content()
        },
    )
}
