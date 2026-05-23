package app.areada.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.areada.data.reader.sanitizeReadingRulerPosition

@Composable
internal fun ReaderComfortOverlay(
    readingRuler: Boolean,
    readingRulerPosition: Float,
    modifier: Modifier = Modifier,
) {
    if (!readingRuler) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        val topGuard = 88.dp
        val bottomGuard = 88.dp
        val availableHeight = (maxHeight - topGuard - bottomGuard).coerceAtLeast(1.dp)
        val yOffset = topGuard + availableHeight * sanitizeReadingRulerPosition(readingRulerPosition)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = yOffset)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    RectangleShape,
                ),
        )
    }
}
