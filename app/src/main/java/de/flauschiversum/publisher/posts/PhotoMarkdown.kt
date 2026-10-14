package de.flauschiversum.publisher.posts

/** Änderungen an den Bildzeilen im Markdown eines Beitrags (Specs `foto-verwaltung`). */
object PhotoMarkdown {
    private fun lines(body: String) = PostDocument.splitKeepingEol(body)
    private fun eolOf(line: String) = line.takeLast(line.length - line.trimEnd('\r', '\n').length)

    /** Zeilennummer (ab 0) der Überschrift `## Anhänge`, sonst -1. */
    private fun headingLine(ls: List<String>) = ls.indexOfFirst { it.trimEnd() == PostBody.ATTACHMENTS_HEADING }

    /** Bildzeilen mit Zeilennummer, Verweis und Angabe, ob sie unter `## Anhänge` stehen. */
    data class Ref(val line: Int, val ref: ImageRef, val inAttachments: Boolean)

    fun refs(body: String): List<Ref> {
        val h = headingLine(lines(body))
        return PostBody.imageLines(body).map { (i, r) -> Ref(i, r, h in 0 until i) }
    }

    /** Alt-Text in allen Bildzeilen, die auf [file] verweisen; der übrige Text bleibt unverändert. */
    fun setAlt(body: String, file: String, alt: String): String {
        val ls = lines(body).toMutableList()
        for (r in refs(body)) if (r.ref.file == file) {
            val old = ls[r.line]
            val indent = old.takeWhile { it == ' ' || it == '\t' }
            ls[r.line] = indent + ImageRef(alt, file).render() + eolOf(old)
        }
        return ls.joinToString("")
    }

    /** Entfernt alle Bildzeilen für [file]; war es die letzte unter `## Anhänge`, verschwindet auch die Überschrift. */
    fun remove(body: String, file: String): String {
        val ls = lines(body).toMutableList()
        val doomed = refs(body).filter { it.ref.file == file }.map { it.line }.toSet()
        if (doomed.isEmpty()) return body
        val kept = ls.filterIndexed { i, _ -> i !in doomed }.toMutableList()
        val h = headingLine(kept)
        if (h >= 0 && kept.drop(h + 1).all { it.isBlank() }) {
            // leere Überschrift samt Leerzeilen davor und danach entfernen
            var start = h
            while (start > 0 && kept[start - 1].isBlank()) start--
            val eol = ls.firstOrNull { it.endsWith("\n") }?.let { eolOf(it) } ?: "\n"
            val head = kept.subList(0, start).joinToString("")
            return if (head.isEmpty()) "" else head.trimEnd('\r', '\n') + eol
        }
        return kept.joinToString("")
    }

    /** Anzahl der Verweise auf [file] außerhalb von `## Anhänge`. */
    fun outsideCount(body: String, file: String) = refs(body).count { it.ref.file == file && !it.inAttachments }

    /** Verweise `(file)` mitten in einem Absatz (keine eigene Bildzeile): Sie bleiben beim Entfernen stehen, die App warnt davor. */
    fun inlineMentions(body: String, file: String): Int {
        val lineRefs = refs(body).count { it.ref.file == file }
        return Regex(Regex.escape("($file)")).findAll(body).count() - lineRefs
    }

    /** Hängt [file] am Ende von `## Anhänge` an (wie beim Teilen von Fotos). */
    fun include(body: String, file: String, alt: String): String =
        PostBody.appendAttachments(body, listOf(ImageRef(alt, file)), if (body.contains("\r\n")) "\r\n" else "\n")

    /** Dateien der Bildzeilen unter `## Anhänge` in ihrer Reihenfolge. */
    fun attachmentFiles(body: String): List<String> = refs(body).filter { it.inAttachments }.map { it.ref.file }

    /**
     * Verschiebt die Bildzeile von [file] unter `## Anhänge` an Position [toIndex] unter den Anhang-Bildern.
     * Zeilen außerhalb des Abschnitts bleiben unverändert; die Zeilen stehen weiter direkt untereinander.
     */
    fun move(body: String, file: String, toIndex: Int): String {
        val ls = lines(body)
        val atts = refs(body).filter { it.inAttachments }
        val from = atts.indexOfFirst { it.ref.file == file }
        if (from < 0 || atts.size < 2) return body
        val to = toIndex.coerceIn(0, atts.size - 1)
        if (from == to) return body
        val slots = atts.map { it.line }
        val texts = atts.map { ls[it.line] }.toMutableList()
        val moved = texts.removeAt(from); texts.add(to, moved)
        val out = ls.toMutableList()
        slots.forEachIndexed { k, line -> out[line] = texts[k] }
        // Zeilenende der letzten Zeile (evtl. ohne Umbruch) bleibt an der letzten Stelle
        val lastSlot = slots.last()
        if (!ls[lastSlot].endsWith("\n")) {
            out[lastSlot] = out[lastSlot].trimEnd('\r', '\n')
            val firstWithEol = slots.firstOrNull { ls[it].endsWith("\n") }
            for (k in slots) if (k != lastSlot && !out[k].endsWith("\n")) out[k] = out[k] + (firstWithEol?.let { eolOf(ls[it]) } ?: "\n")
        }
        return out.joinToString("")
    }
}
