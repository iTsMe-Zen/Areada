package app.areada.data

import java.util.Locale

data class NoteSection(
    val title: String,
    val content: String,
)

data class NoteSectionDocument(
    val sections: List<NoteSection>,
    val hasExplicitSections: Boolean,
)

private val NoteHeadingRegex = Regex("(?m)^#[ \\t]*(.*?)[ \\t]*$")

fun parseNoteSections(text: String): NoteSectionDocument {
    val normalizedText = text.normalizeNoteLineEndings()
    val headings = NoteHeadingRegex.findAll(normalizedText).toList()
    if (headings.isEmpty()) {
        return NoteSectionDocument(
            sections = listOf(NoteSection(title = DefaultNoteSectionTitle, content = normalizedText)),
            hasExplicitSections = false,
        )
    }

    val sections = mutableListOf<NoteSection>()
    val leadingText = normalizedText.substring(0, headings.first().range.first).trim('\n')
    if (leadingText.isNotBlank()) {
        sections += NoteSection(
            title = uniqueNoteSectionTitle(DefaultNoteSectionTitle, sections),
            content = leadingText,
        )
    }

    headings.forEachIndexed { index, heading ->
        val nextHeadingStart = headings.getOrNull(index + 1)?.range?.first ?: normalizedText.length
        val contentStart = (heading.range.last + 1).coerceAtMost(normalizedText.length)
        val content = normalizedText
            .substring(contentStart, nextHeadingStart)
            .removePrefix("\n")
            .trim('\n')
        val rawTitle = heading.groupValues.getOrNull(1).orEmpty()
        sections += NoteSection(
            title = uniqueNoteSectionTitle(rawTitle, sections, fallback = DefaultRecoveredSectionTitle),
            content = content,
        )
    }

    return NoteSectionDocument(
        sections = sections.ifEmpty {
            listOf(NoteSection(title = DefaultNoteSectionTitle, content = ""))
        },
        hasExplicitSections = true,
    )
}

fun serializeNoteSections(sections: List<NoteSection>): String {
    val normalizedSections = sanitizeNoteSections(sections)
    return normalizedSections.joinToString(separator = "\n\n") { section ->
        val content = section.content.normalizeNoteLineEndings().trim('\n')
        if (content.isBlank()) {
            "# ${section.title}"
        } else {
            "# ${section.title}\n\n$content"
        }
    }
}

fun addNoteSection(
    sections: List<NoteSection>,
    requestedTitle: String = NewNoteSectionTitle,
): List<NoteSection> =
    sanitizeNoteSections(sections) + NoteSection(
        title = uniqueNoteSectionTitle(requestedTitle, sections, fallback = NewNoteSectionTitle),
        content = "",
    )

fun renameNoteSection(
    sections: List<NoteSection>,
    index: Int,
    requestedTitle: String,
): List<NoteSection> {
    if (index !in sections.indices) {
        return sanitizeNoteSections(sections)
    }
    val sanitizedSections = sanitizeNoteSections(sections)
    val newTitle = uniqueNoteSectionTitle(
        requestedTitle = requestedTitle,
        sections = sanitizedSections,
        excludingIndex = index,
        fallback = sanitizedSections[index].title,
    )
    return sanitizedSections.mapIndexed { sectionIndex, section ->
        if (sectionIndex == index) {
            section.copy(title = newTitle)
        } else {
            section
        }
    }
}

fun resolveNoteSectionIndex(
    sections: List<NoteSection>,
    savedTitle: String?,
): Int {
    if (sections.isEmpty()) {
        return 0
    }
    val cleanTitle = savedTitle?.trim().orEmpty()
    if (cleanTitle.isBlank()) {
        return 0
    }
    return sections.indexOfFirst { section -> section.title == cleanTitle }
        .takeIf { index -> index >= 0 }
        ?: 0
}

fun sanitizeNoteSections(sections: List<NoteSection>): List<NoteSection> {
    val sanitized = mutableListOf<NoteSection>()
    sections.forEach { section ->
        sanitized += NoteSection(
            title = uniqueNoteSectionTitle(section.title, sanitized),
            content = section.content.normalizeNoteLineEndings(),
        )
    }
    return sanitized.ifEmpty {
        listOf(NoteSection(title = DefaultNoteSectionTitle, content = ""))
    }
}

fun uniqueNoteSectionTitle(
    requestedTitle: String,
    sections: List<NoteSection>,
    excludingIndex: Int? = null,
    fallback: String = DefaultNoteSectionTitle,
): String {
    val baseTitle = requestedTitle
        .replace(Regex("\\s+"), " ")
        .trim()
        .removePrefix("#")
        .trim()
        .ifBlank { fallback }
        .ifBlank { DefaultNoteSectionTitle }
    val existingTitles = sections
        .filterIndexed { index, _ -> excludingIndex == null || index != excludingIndex }
        .map { section -> section.title.lowercase(Locale.ROOT) }
        .toSet()
    if (baseTitle.lowercase(Locale.ROOT) !in existingTitles) {
        return baseTitle
    }

    var suffix = 2
    while (true) {
        val candidate = "$baseTitle $suffix"
        if (candidate.lowercase(Locale.ROOT) !in existingTitles) {
            return candidate
        }
        suffix += 1
    }
}

private fun String.normalizeNoteLineEndings(): String =
    replace("\r\n", "\n").replace('\r', '\n')

const val DefaultNoteSectionTitle = "Note"
const val DefaultRecoveredSectionTitle = "Section"
const val NewNoteSectionTitle = "New section"
