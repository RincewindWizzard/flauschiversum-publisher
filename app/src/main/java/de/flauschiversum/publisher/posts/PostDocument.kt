package de.flauschiversum.publisher.posts

/** Die Felder des Frontmatters, die die App kennt. */
object Fields {
    const val TITLE = "title"
    const val DATE = "date"
    const val PERMALINK = "permalink"
    const val TAGS = "tags"
    const val AUTHOR = "author"
    const val IMAGE = "image"
    const val EXCERPT = "excerpt"

    /** Reihenfolge, in der der Blog die Felder schreibt; neue Felder werden entsprechend eingefügt. */
    val ORDER = listOf(TITLE, PERMALINK, TAGS, AUTHOR, DATE, IMAGE, EXCERPT)
}

/**
 * Eine `index.md` eines Beitrags, getrennt in Kopf (Frontmatter einschließlich der Trennzeilen `---`) und Text.
 * [text] ergibt immer wieder die Originaldatei Zeichen für Zeichen, solange nichts geändert wurde.
 */
class PostDocument(val head: String, val body: String) {
    val text: String get() = head + body
    val hasFrontmatter: Boolean get() = head.isNotEmpty()

    /** Zeilenende der Datei ("\n" oder "\r\n"). */
    val eol: String get() = if (head.contains("\r\n")) "\r\n" else "\n"

    private fun headLines(): List<String> = splitKeepingEol(head).map { it.removeSuffix("\n").removeSuffix("\r") }
    private fun innerLines(): List<String> = headLines().let { if (it.size >= 2) it.subList(1, it.size - 1) else emptyList() }

    /** Das Frontmatter als Zuordnung (Zeichenketten oder Listen); wirft bei ungültigem YAML. */
    fun fields(): Map<String, Any?> = if (!hasFrontmatter) emptyMap() else Yaml.parse(innerLines().joinToString("\n"))

    fun string(key: String): String? = (fields()[key] as? String)?.trim()?.ifEmpty { null }

    fun tags(): List<String> = when (val v = fields()[Fields.TAGS]) {
        is List<*> -> v.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } }
        is String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    fun withBody(newBody: String) = PostDocument(head, newBody)

    /**
     * Setzt (oder entfernt bei null) ein Feld. Nur die Zeilen dieses Feldes ändern sich; bleibt der Wert gleich,
     * ändert sich nichts. Neue Felder werden in der Reihenfolge des Blogs eingefügt.
     */
    fun withField(key: String, renderedValue: String?): PostDocument {
        if (!hasFrontmatter) {
            if (renderedValue == null) return this
            return PostDocument("---$eol$key: $renderedValue$eol---$eol", body)
        }
        val lines = headLines().toMutableList()
        val blocks = blocks(lines)
        val block = blocks.firstOrNull { it.key == key }
        if (block != null) {
            if (renderedValue == null) {
                repeat(block.end - block.start) { lines.removeAt(block.start) }
            } else {
                val old = lines.subList(block.start, block.end)
                val keepCurrent = old.size == 1 && old[0].substringAfter(':', "").trim() == renderedValue.trim()
                if (keepCurrent) return this
                repeat(block.end - block.start) { lines.removeAt(block.start) }
                lines.add(block.start, "$key: $renderedValue")
            }
        } else {
            if (renderedValue == null) return this
            val at = insertionIndex(key, lines, blocks)
            lines.add(at, "$key: $renderedValue")
        }
        return PostDocument(joinWithEol(lines, endsWithNewline = head.endsWith("\n")), body)
    }

    private data class Block(val key: String, val start: Int, val end: Int)

    private fun blocks(lines: List<String>): List<Block> {
        val result = mutableListOf<Block>()
        var i = 1 // nach der öffnenden Trennzeile
        val last = lines.size - 1 // schließende Trennzeile
        while (i < last) {
            val m = KEY_LINE.matchEntire(lines[i])
            if (m == null) { i++; continue }
            var j = i + 1
            while (j < last && !KEY_LINE.matches(lines[j]) && !lines[j].startsWith("#")) j++
            result += Block(m.groupValues[1], i, j)
            i = j
        }
        return result
    }

    private fun insertionIndex(key: String, lines: List<String>, blocks: List<Block>): Int {
        val rank = Fields.ORDER.indexOf(key)
        if (rank < 0) return lines.size - 1
        val preceding = blocks.filter { Fields.ORDER.indexOf(it.key) in 0 until rank }.maxByOrNull { Fields.ORDER.indexOf(it.key) }
        if (preceding != null) return preceding.end
        val following = blocks.filter { Fields.ORDER.indexOf(it.key) > rank }.minByOrNull { Fields.ORDER.indexOf(it.key) }
        return following?.start ?: (lines.size - 1)
    }

    private fun joinWithEol(lines: List<String>, endsWithNewline: Boolean): String =
        lines.joinToString(eol) + if (endsWithNewline) eol else ""

    companion object {
        private val KEY_LINE = Regex("""^([A-Za-z0-9_-]+):(.*)$""")

        fun parse(text: String): PostDocument {
            val lines = splitKeepingEol(text)
            if (lines.isEmpty() || lines[0].trimEnd() != "---") return PostDocument("", text)
            for (i in 1 until lines.size) {
                if (lines[i].trimEnd() == "---") {
                    val headLen = lines.subList(0, i + 1).sumOf { it.length }
                    return PostDocument(text.substring(0, headLen), text.substring(headLen))
                }
            }
            return PostDocument("", text) // kein Ende des Frontmatters: nicht sicher teilbar
        }

        /** Zerlegt [s] in Zeilen einschließlich ihrer Zeilenenden. */
        fun splitKeepingEol(s: String): List<String> {
            if (s.isEmpty()) return emptyList()
            val out = mutableListOf<String>()
            var start = 0
            for (i in s.indices) if (s[i] == '\n') { out += s.substring(start, i + 1); start = i + 1 }
            if (start < s.length) out += s.substring(start)
            return out
        }
    }
}
