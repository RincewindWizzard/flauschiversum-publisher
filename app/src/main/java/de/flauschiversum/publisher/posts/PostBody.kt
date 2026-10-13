package de.flauschiversum.publisher.posts

/** Eine Bildzeile `![Alt-Text](Datei)` im Markdown eines Beitrags. */
data class ImageRef(val alt: String, val file: String) {
    fun render(): String = "![${escapeAlt(alt)}]($file)"

    companion object {
        fun escapeAlt(alt: String): String =
            alt.replace(Regex("""\s*[\r\n]+\s*"""), " ").replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]").trim()
    }
}

/** Operationen auf dem Text (Markdown) eines Beitrags. */
object PostBody {
    const val ATTACHMENTS_HEADING = "## Anhänge"

    /**
     * Bindet [images] unter `## Anhänge` am Dateiende ein: Fehlt die Überschrift, wird sie angelegt (mit Leerzeile
     * davor und danach); Bildzeilen stehen ohne Leerzeile untereinander (eine Bildgruppe). Alles darüber bleibt
     * unverändert.
     */
    fun appendAttachments(body: String, images: List<ImageRef>, eol: String = "\n"): String {
        if (images.isEmpty()) return body
        val lines = images.joinToString("") { it.render() + eol }
        val headingAt = attachmentsHeadingIndex(body)
        if (headingAt < 0) {
            val trimmed = body.trimEnd('\n', '\r', ' ', '\t')
            val lead = if (trimmed.isEmpty()) "" else trimmed + eol + eol
            return lead + ATTACHMENTS_HEADING + eol + eol + lines
        }
        // Abschnitt existiert: neue Zeilen ans Dateiende, ohne Leerzeilen davor
        val trimmed = body.trimEnd('\n', '\r', ' ', '\t')
        val onlyHeading = trimmed.substringAfterLast('\n').trimEnd() == ATTACHMENTS_HEADING
        return trimmed + eol + (if (onlyHeading) eol else "") + lines
    }

    /** Zeichenposition der Überschrift `## Anhänge` (als eigene Zeile), sonst -1. */
    fun attachmentsHeadingIndex(body: String): Int {
        var pos = 0
        for (line in PostDocument.splitKeepingEol(body)) {
            if (line.trimEnd() == ATTACHMENTS_HEADING) return pos
            pos += line.length
        }
        return -1
    }

    /** Bildzeilen `![Alt](Datei)` des Textes mit ihrer Zeilennummer (ab 0); erkennt Dateinamen mit Leerzeichen. */
    fun imageLines(body: String): List<Pair<Int, ImageRef>> {
        val result = mutableListOf<Pair<Int, ImageRef>>()
        PostDocument.splitKeepingEol(body).forEachIndexed { i, raw ->
            val line = raw.trimEnd('\n', '\r')
            val m = IMAGE_LINE.matchEntire(line.trim()) ?: return@forEachIndexed
            result += i to ImageRef(unescapeAlt(m.groupValues[1]), m.groupValues[2])
        }
        return result
    }

    private val IMAGE_LINE = Regex("""^!\[((?:\\.|[^\]\\])*)]\(([^)]*)\)$""")
    private fun unescapeAlt(s: String) = s.replace(Regex("""\\(.)"""), "$1")
}
