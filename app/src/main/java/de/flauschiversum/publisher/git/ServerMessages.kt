package de.flauschiversum.publisher.git

/**
 * Text, den ein Server beim Push mitschickt (die `remote:`-Zeilen, z. B. aus einem Deploy-Hook). Ein Fehler im Hook
 * lässt den Push selbst gelingen; ohne diese Meldungen bliebe er unbemerkt.
 */
data class ServerMessages(val text: String) {
    private val lines: List<String> = text.lines().map { clean(it) }.filter { it.isNotEmpty() }

    val isEmpty: Boolean get() = lines.isEmpty()

    /** Die Meldungen deuten auf einen Fehler hin (Wörter wie „Fehler“, „fehlgeschlagen“, „error“, „failed“). */
    val hasProblem: Boolean get() = lines.any { PROBLEM.containsMatchIn(it) }

    /** Die Zeilen, die den Fehler beschreiben (ohne Deploy-Fortschritt), höchstens [MAX_SUMMARY] Zeichen. */
    val summary: String
        get() {
            val errors = lines.filter { PROBLEM.containsMatchIn(it) }
            val picked = if (errors.isEmpty()) lines else errors
            val joined = picked.joinToString("\n")
            return if (joined.length <= MAX_SUMMARY) joined else joined.take(MAX_SUMMARY - 1).trimEnd() + "…"
        }

    /** Alle Meldungen, bereinigt, für die ausgeklappte Benachrichtigung. */
    val full: String get() = lines.joinToString("\n").let { if (it.length <= MAX_FULL) it else it.take(MAX_FULL - 1).trimEnd() + "…" }

    companion object {
        val NONE = ServerMessages("")
        private const val MAX_SUMMARY = 500
        private const val MAX_FULL = 3000
        private val PROBLEM = Regex("""(?i)\b(fehler|fehlgeschlagen|error|failed|failure|fatal|abgebrochen)\b""")

        fun from(raw: String?): ServerMessages = if (raw.isNullOrBlank()) NONE else ServerMessages(raw)

        private fun clean(line: String) = line.trim().removePrefix("remote:").trim().removePrefix("==>").trim()
    }
}
