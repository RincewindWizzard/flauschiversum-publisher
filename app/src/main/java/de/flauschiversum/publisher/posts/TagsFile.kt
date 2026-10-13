package de.flauschiversum.publisher.posts

/** Die erlaubten Schlagworte aus `content/tags.yaml`. */
object TagsFile {
    private val ENTRY = Regex("""^-\s+name:\s*(.+?)\s*$""")

    fun names(text: String): List<String> = text.lineSequence().mapNotNull { line ->
        val m = ENTRY.matchEntire(line.trimEnd()) ?: return@mapNotNull null
        unquote(m.groupValues[1])
    }.toList()

    private fun unquote(s: String): String = when {
        s.length >= 2 && s.startsWith('"') && s.endsWith('"') -> s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        s.length >= 2 && s.startsWith('\'') && s.endsWith('\'') -> s.substring(1, s.length - 1).replace("''", "'")
        else -> s
    }

    /** Findet das Schlagwort ohne Beachtung der Groß-/Kleinschreibung in der Liste. */
    fun match(name: String, allowed: Collection<String>): String? = allowed.firstOrNull { it.equals(name, ignoreCase = true) }
}
