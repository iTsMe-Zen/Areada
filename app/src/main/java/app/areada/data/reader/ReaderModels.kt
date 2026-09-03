package app.areada.data.reader

import android.net.Uri

enum class DocumentType {
    EPUB,
    PDF,
    TXT,
    FB2,
    ZIP,
    ARCHIVE,
    MARKDOWN,
}

data class ReaderDocument(
    val uri: Uri,
    val uriString: String,
    val title: String,
    val type: DocumentType,
)

data class RecentDocument(
    val uriString: String,
    val title: String,
    val type: DocumentType,
    val lastOpenedAt: Long,
)

data class TextDocumentContent(
    val text: String,
    val title: String? = null,
)
