package app.areada.ui.reader

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.reader.DocumentType
import app.areada.data.library.LibraryPathSegment
import app.areada.data.reader.ReadingProgress
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderRow(
    name: String,
    pinned: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val nameCopied = stringResource(R.string.name_copied)

    val surfaceColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(150),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboard.setText(AnnotatedString(name))
                    Toast.makeText(context, nameCopied, Toast.LENGTH_SHORT).show()
                },
            ),
        shape = RectangleShape,
        color = surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = stringResource(R.string.folder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(14.dp))
            if (pinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookRow(
    title: String,
    type: DocumentType,
    progressLabel: String?,
    pinned: Boolean,
    hasNote: Boolean = false,
    typeLabel: String? = null,
    extraMetadata: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val nameCopied = stringResource(R.string.name_copied)
    val noteLabel = stringResource(R.string.note)
    val metadata = buildList {
        add(typeLabel ?: type.name)
        extraMetadata?.takeIf { it.isNotBlank() }?.let(::add)
        progressLabel?.takeIf { it.isNotBlank() }?.let(::add)
        if (hasNote) {
            add(noteLabel)
        }
    }.joinToString(" · ")

    val surfaceColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(150),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboard.setText(AnnotatedString(title))
                    Toast.makeText(context, nameCopied, Toast.LENGTH_SHORT).show()
                },
            ),
        shape = RectangleShape,
        color = surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NotePopup(
    title: String,
    note: AnnotatedString,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable { },
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = onClose,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(text = stringResource(R.string.close))
                    }
                }
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                )
            }
        }
    }
}

@Composable
internal fun InfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyStateCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun SettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    icon()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun SettingToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
        )
        Text(
            text = if (checked) stringResource(R.string.on) else stringResource(R.string.off),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (checked && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            },
        )
    }
}

@Composable
internal fun ReaderMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (onRetry != null) 8.dp else 20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (onRetry != null) {
                    PromptChoiceButton(
                        label = stringResource(R.string.retry),
                        highlighted = true,
                        onClick = onRetry,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = {
                        onAction()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(text = actionLabel)
                }
            }
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(text = stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
internal fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun LoadingState(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun buildResumeLabel(
    progress: ReadingProgress?,
    type: DocumentType,
): String? {
    if (progress == null) {
        return null
    }

    return when (type) {
        DocumentType.EPUB -> {
            val total = progress.epubChapterCount.takeIf { it > 0 }
                ?: return stringResource(R.string.resume_chapter, progress.epubChapterIndex + 1)
            stringResource(R.string.resume_chapter_of, progress.epubChapterIndex + 1, total)
        }

        DocumentType.PDF -> {
            val total = progress.pdfPageCount.takeIf { it > 0 }
                ?: return stringResource(R.string.resume_page, progress.pdfPageIndex + 1)
            stringResource(R.string.resume_page_of, progress.pdfPageIndex + 1, total)
        }

        DocumentType.TXT,
        DocumentType.ZIP,
        DocumentType.ARCHIVE -> null

        DocumentType.FB2 -> {
            val percent = (progress.txtScrollFraction.coerceIn(0f, 1f) * 100f).roundToInt()
            if (percent <= 0) null else stringResource(R.string.resume_percent, percent)
        }
    }
}

internal fun archiveFormatLabel(fileName: String): String? {
    val lower = fileName.lowercase(Locale.ROOT)
    return when {
        lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "TAR.GZ"
        lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> "TAR.BZ2"
        lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> "TAR.XZ"
        lower.endsWith(".tar") -> "TAR"
        lower.endsWith(".7z") -> "7z"
        lower.endsWith(".bz2") -> "BZ2"
        lower.endsWith(".gz") -> "GZ"
        lower.endsWith(".xz") -> "XZ"
        lower.endsWith(".rar") -> "RAR"
        else -> null
    }
}