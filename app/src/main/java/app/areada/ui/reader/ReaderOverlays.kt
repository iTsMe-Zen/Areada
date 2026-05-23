package app.areada.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
internal fun EpubSectionScrollThumb(
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "epubSectionScrollThumb",
    )
    val backgroundIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val thumbColor = if (backgroundIsDark) {
        Color.White.copy(alpha = 0.48f)
    } else {
        Color.Black.copy(alpha = 0.40f)
    }
    val thumbHeight = 56.dp

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        if (maxHeight <= thumbHeight) {
            return@BoxWithConstraints
        }
        val travel = maxHeight - thumbHeight
        Box(
            modifier = Modifier
                .offset(y = travel * animatedProgress)
                .width(4.dp)
                .height(thumbHeight)
                .background(thumbColor, CircleShape),
        )
    }
}
