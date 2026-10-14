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

    const val PATH = "content/tags.yaml"
    const val MAX_LENGTH = 40

    /** Prüft einen neuen Namen; null: gültig. [existing] sind die Schlagworte aus Zielbranch und `draft`. */
    fun validate(raw: String, existing: Collection<String>): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> "Bitte einen Namen eingeben."
            '\n' in name || '\r' in name -> "Der Name darf keinen Zeilenumbruch enthalten."
            name.length > MAX_LENGTH -> "Der Name darf höchstens $MAX_LENGTH Zeichen haben."
            match(name, existing) != null -> "Das Schlagwort gibt es schon: „${match(name, existing)}“."
            else -> null
        }
    }

    private val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.GERMAN).apply { strength = java.text.Collator.SECONDARY }

    /** Vergleich nach deutscher Sortierung (Umlaute unter ihrem Grundbuchstaben, Groß-/Kleinschreibung egal). */
    fun compare(a: String, b: String): Int = collator.compare(a, b)

    /**
     * Fügt `- name: <Name>` als eine Zeile ein, sortiert zwischen die vorhandenen Einträge; alle anderen Zeilen,
     * Kommentare und das Zeilenende bleiben unverändert.
     */
    fun insert(text: String, rawName: String): String {
        val name = rawName.trim()
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = PostDocument.splitKeepingEol(text)
        val entry = "- name: " + Yaml.plainOrQuoted(name)
        var lastEntry = -1; var at = -1
        for ((i, l) in lines.withIndex()) {
            val m = ENTRY.matchEntire(l.trimEnd('\r', '\n').trimEnd()) ?: continue
            lastEntry = i
            if (at < 0 && compare(unquote(m.groupValues[1]), name) > 0) at = i
        }
        val sb = StringBuilder()
        if (lines.isEmpty()) return entry + eol
        val insertAt = if (at >= 0) at else if (lastEntry >= 0) lastEntry + 1 else lines.size
        for ((i, l) in lines.withIndex()) {
            if (i == insertAt) sb.append(entry).append(eol)
            sb.append(l)
            // letzte Zeile ohne Zeilenende: erst abschließen, wenn dahinter etwas folgt
            if (i == lines.size - 1 && insertAt == lines.size) { if (!l.endsWith("\n")) sb.append(eol); sb.append(entry).append(eol) }
        }
        return sb.toString()
    }
}
