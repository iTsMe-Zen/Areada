package app.areada.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatBookInfoDateTime(timestamp: Long): String =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("Unknown")

internal fun formatNoteTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd | hh:mm a :", Locale.US).format(Date())
