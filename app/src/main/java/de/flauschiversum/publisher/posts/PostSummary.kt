package de.flauschiversum.publisher.posts

import de.flauschiversum.publisher.git.GitStore

/** Kurzangaben zu einem Beitrag für Listen. */
data class PostSummary(
    val dir: String,
    val title: String,
    val date: PostDate?,
    val tags: List<String>,
    val author: String?,
    val image: String?,
    val excerpt: String?,
    val permalink: String?,
    /** Gesetzt, wenn die Datei nicht gelesen werden konnte (ungültiges Frontmatter o. ä.). */
    val problem: String? = null,
) {
    companion object {
        fun from(dir: String, text: String): PostSummary {
            val doc = PostDocument.parse(text)
            return try {
                PostSummary(
                    dir = dir,
                    title = doc.string(Fields.TITLE) ?: dir,
                    date = PostDate.parse(doc.string(Fields.DATE)),
                    tags = doc.tags(),
                    author = doc.string(Fields.AUTHOR),
                    image = doc.string(Fields.IMAGE),
                    excerpt = doc.string(Fields.EXCERPT),
                    permalink = doc.string(Fields.PERMALINK),
                    problem = if (!doc.hasFrontmatter) "Kein gültiges Frontmatter" else null,
                )
            } catch (e: Exception) {
                PostSummary(dir, dir, null, emptyList(), null, null, null, null, problem = "Frontmatter nicht lesbar: ${e.message?.lineSequence()?.firstOrNull()}")
            }
        }
    }
}

/** Liest Beiträge aus einem Stand des Repositorys. */
class PostReader(private val store: GitStore) {
    fun dirNames(rev: String): List<String> =
        store.list(rev, POSTS_DIR).filter { it.isDirectory }.map { it.name }

    fun text(rev: String, dir: String): String? = store.readText(rev, "$POSTS_DIR/$dir/index.md")

    fun document(rev: String, dir: String): PostDocument? = text(rev, dir)?.let { PostDocument.parse(it) }

    fun summary(rev: String, dir: String): PostSummary? = text(rev, dir)?.let { PostSummary.from(dir, it) }

    /** Alle Beiträge (Verzeichnisse mit `index.md`) des Standes, neueste zuerst. */
    fun summaries(rev: String): List<PostSummary> =
        dirNames(rev).mapNotNull { summary(rev, it) }.sortedWith(NEWEST_FIRST)

    /** Dateien eines Beitragsverzeichnisses (ohne Unterverzeichnisse). */
    fun files(rev: String, dir: String): List<String> =
        store.list(rev, "$POSTS_DIR/$dir").filter { !it.isDirectory }.map { it.name }

    companion object {
        const val POSTS_DIR = "content/posts"
        val NEWEST_FIRST: Comparator<PostSummary> =
            compareByDescending<PostSummary> { it.date?.dateTime }.thenBy { it.title.lowercase() }
    }
}
