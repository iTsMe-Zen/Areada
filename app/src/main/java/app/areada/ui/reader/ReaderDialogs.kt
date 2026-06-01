package app.areada.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.areada.R
import app.areada.data.ZipBookEntry
import app.areada.data.reader.DocumentType

@Composable
internal fun OpenLinkDialog(
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    CompactChoiceDialog(
        question = stringResource(R.string.open_link_question),
        onDismiss = onDismiss,
        onYes = onOpen,
    )
}

@Composable
internal fun ExitPromptDialog(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) {
    CompactChoiceDialog(
        question = stringResource(R.string.quit_question),
        onDismiss = onDismiss,
        onYes = onExit,
    )
}

@Composable
internal fun ConfirmDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CompactChoiceDialog(
        question = stringResource(R.string.delete_permanently_question),
        onDismiss = onDismiss,
        onYes = onConfirm,
    )
}

@Composable
internal fun ZipEntryPickerDialog(
    entries: List<ZipBookEntry>,
    onDismiss: () -> Unit,
    onOpenEntry: (ZipBookEntry) -> Unit,
) {
    val maxListHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.48f)
        .coerceAtMost(360.dp)
        .coerceAtLeast(160.dp)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.choose_file),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                ) {
                    items(
                        items = entries,
                        key = { entry -> entry.uriString },
                    ) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEntry(entry) }
                                .padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.type.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                PromptChoiceButton(
                    label = stringResource(R.string.cancel),
                    highlighted = false,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun SaveChangesDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    CompactTwoButtonDialog(
        title = stringResource(R.string.save_changes_title),
        body = stringResource(R.string.save_changes_body),
        primaryLabel = stringResource(R.string.prompt_yes),
        secondaryLabel = stringResource(R.string.prompt_no),
        onPrimary = onSave,
        onSecondary = onDiscard,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun CompactTwoButtonDialog(
    title: String,
    body: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 420.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PromptChoiceButton(
                        label = primaryLabel,
                        highlighted = true,
                        onClick = onPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    PromptChoiceButton(
                        label = secondaryLabel,
                        highlighted = false,
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun GoToPositionDialog(
    label: String,
    currentIndex: Int,
    total: Int,
    title: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val dialogTitle = title ?: stringResource(R.string.go_to)
    val safeTotal = total.coerceAtLeast(1)
    var input by rememberSaveable(label, currentIndex, safeTotal) {
        mutableStateOf((currentIndex + 1).coerceIn(1, safeTotal).toString())
    }
    val targetIndex = input.toIntOrNull()?.minus(1)
    val targetIsValid = targetIndex != null && targetIndex in 0 until safeTotal

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 420.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "$label ${currentIndex + 1} / $safeTotal",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(6)
                    },
                    singleLine = true,
                    isError = input.isNotBlank() && !targetIsValid,
                    placeholder = {
                        Text(text = "1-$safeTotal")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PromptChoiceButton(
                        label = stringResource(R.string.ok),
                        highlighted = true,
                        enabled = targetIsValid,
                        onClick = {
                            onConfirm(targetIndex ?: return@PromptChoiceButton)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PromptChoiceButton(
                        label = stringResource(R.string.cancel),
                        highlighted = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactChoiceDialog(
    question: String,
    onDismiss: () -> Unit,
    onYes: () -> Unit,
) {
    val yesLabel = stringResource(R.string.prompt_yes)
    val noLabel = stringResource(R.string.prompt_no)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 420.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = question,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PromptChoiceButton(
                        label = yesLabel,
                        highlighted = true,
                        onClick = onYes,
                        modifier = Modifier.weight(1f),
                    )
                    PromptChoiceButton(
                        label = noLabel,
                        highlighted = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PromptChoiceButton(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    }
    val effectiveBackground = if (enabled) {
        backgroundColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RectangleShape,
        color = effectiveBackground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
        }
    }
}

