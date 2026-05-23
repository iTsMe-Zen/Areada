package app.areada.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.areada.R

@Composable
internal fun HomeTab.displayLabel(): String =
    when (this) {
        HomeTab.Collection -> stringResource(R.string.books)
        HomeTab.Reading -> stringResource(R.string.reading)
        HomeTab.Bookmarks -> stringResource(R.string.bookmarks)
    }

@Composable
internal fun HomeTabRow(
    selectedTab: HomeTab,
    readingCount: Int,
    bookmarkCount: Int,
    collectionLabel: String? = null,
    onSelectTab: (HomeTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            val count = when (tab) {
                HomeTab.Collection -> null
                HomeTab.Reading -> readingCount
                HomeTab.Bookmarks -> bookmarkCount
            }
            HomeTabChip(
                label = if (tab == HomeTab.Collection && collectionLabel != null) collectionLabel else tab.displayLabel(),
                count = count,
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeTabChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val labelText = buildAnnotatedString {
        append(label)
        count?.let { value ->
            append(" ")
            pushStyle(
                SpanStyle(
                    color = textColor.copy(alpha = if (selected) 0.58f else 0.52f),
                    fontWeight = FontWeight.Normal,
                ),
            )
            append("($value)")
            pop()
        }
    }
    Column(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(2.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RectangleShape,
                ),
        )
    }
}
