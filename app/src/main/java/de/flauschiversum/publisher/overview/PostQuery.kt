package de.flauschiversum.publisher.overview

import java.text.Collator
import java.util.Locale

enum class SortOrder(val label: String) { NEWEST("Neueste zuerst"), OLDEST("Älteste zuerst"), TITLE("Titel A–Z") }

/** Suche, Filter und Sortierung der Übersicht. */
data class PostQuery(
    val text: String = "",
    val tags: Set<String> = emptySet(),
    val year: Int? = null,
    val sort: SortOrder = SortOrder.NEWEST,
) {
    /** Ob die Liste eingeschränkt ist (Suche oder Filter, nicht Sortierung). */
    val isFiltered: Boolean get() = text.isNotBlank() || tags.isNotEmpty() || year != null
}

object PostSearch {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    fun apply(posts: List<IndexedPost>, q: PostQuery): List<IndexedPost> {
        val terms = q.text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = posts.filter { p ->
            (terms.isEmpty() || terms.all { t -> t in p.meta || t in p.body }) &&
                q.tags.all { t -> p.summary.tags.any { it.equals(t, ignoreCase = true) } } &&
                (q.year == null || p.summary.date?.date?.year == q.year)
        }
        return when (q.sort) {
            SortOrder.NEWEST -> filtered.sortedWith(compareByDescending<IndexedPost> { it.summary.date?.dateTime }.thenBy { it.summary.title.lowercase() })
            SortOrder.OLDEST -> filtered.sortedWith(compareBy<IndexedPost, java.time.LocalDateTime?>(nullsLast()) { it.summary.date?.dateTime }.thenBy { it.summary.title.lowercase() })
            SortOrder.TITLE -> filtered.sortedWith { a, b -> collator.compare(a.summary.title, b.summary.title) }
        }
    }

    /** Jahre, in denen es Beiträge gibt (neueste zuerst). */
    fun years(posts: List<IndexedPost>): List<Int> = posts.mapNotNull { it.summary.date?.date?.year }.distinct().sortedDescending()

    /** Schlagworte, die in den Beiträgen vorkommen, alphabetisch. */
    fun tags(posts: List<IndexedPost>): List<String> = posts.flatMap { it.summary.tags }.distinct().sortedWith { a, b -> collator.compare(a, b) }
}
