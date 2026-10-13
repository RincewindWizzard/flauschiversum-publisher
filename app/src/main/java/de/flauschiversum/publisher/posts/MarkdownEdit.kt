package de.flauschiversum.publisher.posts

/** Textoperationen für den Editor (rein, damit sie sich testen lassen). */
object MarkdownEdit {
    /** Ergebnis: neuer Text und neue Cursorposition. */
    data class Result(val text: String, val cursor: Int)

    /**
     * Fügt die Bildzeile [ref] an der Cursorposition [cursor] in eine eigene Zeile ein. Folgt sie direkt auf eine
     * andere Bildzeile, steht keine Leerzeile dazwischen (Bildgruppe); sonst wird sie durch Leerzeilen abgesetzt.
     */
    fun insertImage(text: String, cursor: Int, ref: ImageRef): Result {
        val at = cursor.coerceIn(0, text.length)
        val before = text.substring(0, at)
        val after = text.substring(at)
        val line = ref.render()
        val prefix = when {
            before.isEmpty() -> ""
            before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> if (lastLineIsImage(before.dropLast(1))) "" else "\n"
            else -> if (lastLineIsImage(before)) "\n" else "\n\n"
        }
        val suffix = when {
            after.isEmpty() -> "\n"
            after.startsWith("\n\n") -> "\n"
            after.startsWith("\n") -> if (firstLineIsImage(after.drop(1))) "" else "\n"
            else -> if (firstLineIsImage(after)) "\n" else "\n\n"
        }
        val inserted = prefix + line + suffix
        return Result(before + inserted + after, before.length + prefix.length + line.length)
    }

    private fun lastLineIsImage(s: String) = PostBody.imageLines(s.substringAfterLast('\n')).isNotEmpty()
    private fun firstLineIsImage(s: String) = PostBody.imageLines(s.substringBefore('\n')).isNotEmpty()
}
