package app.areada.reader.fb2

data class Fb2Book(
    val title: String?,
    val author: String?,
    val chapters: List<Fb2Chapter>,
    val images: Map<String, String> = emptyMap(),
)

data class Fb2Chapter(
    val title: String,
    val html: String,
)
