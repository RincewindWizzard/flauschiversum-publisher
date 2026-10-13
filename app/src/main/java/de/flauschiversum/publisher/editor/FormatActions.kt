package de.flauschiversum.publisher.editor

/** Text samt Auswahl (Cursor, wenn [selStart] == [selEnd]); Ergebnis einer Formatierung. */
data class EditState(val text: String, val selStart: Int, val selEnd: Int = selStart)

/** Umformungen der Werkzeugleiste. Alle sind reine Funktionen auf Text und Auswahl. */
object FormatActions {
    fun bold(s: EditState) = wrap(s, "**", "**")
    fun italic(s: EditState) = wrap(s, "*", "*")

    /** Setzt [open] und [close] um die Markierung; ohne Markierung steht der Cursor dazwischen. Nochmaliges Anwenden nimmt sie wieder weg. */
    fun wrap(s: EditState, open: String, close: String): EditState {
        val a = minOf(s.selStart, s.selEnd); val b = maxOf(s.selStart, s.selEnd)
        val sel = s.text.substring(a, b)
        if (sel.length >= open.length + close.length && sel.startsWith(open) && sel.endsWith(close) && open != "*") {
            val inner = sel.substring(open.length, sel.length - close.length)
            return EditState(s.text.replaceRange(a, b, inner), a, a + inner.length)
        }
        val text = s.text.replaceRange(a, b, open + sel + close)
        return if (a == b) EditState(text, a + open.length) else EditState(text, a + open.length, a + open.length + sel.length)
    }

    /** Überschrift der Ebene [level] in den betroffenen Zeilen; steht dort schon genau diese Ebene, wird sie entfernt. */
    fun heading(s: EditState, level: Int = 2): EditState {
        val prefix = "#".repeat(level) + " "
        return lines(s) { l -> if (l.startsWith(prefix)) l.removePrefix(prefix) else prefix + l.replace(Regex("^#{1,6} "), "") }
    }

    fun bullet(s: EditState) = lines(s) { l -> if (l.startsWith("- ")) l.removePrefix("- ") else "- $l" }
    fun quote(s: EditState) = lines(s) { l -> if (l.startsWith("> ")) l.removePrefix("> ") else "> $l" }

    /** `[Markierung](https://)`; ohne Markierung `[](https://)` mit Cursor zwischen den eckigen Klammern. */
    fun link(s: EditState): EditState {
        val a = minOf(s.selStart, s.selEnd); val b = maxOf(s.selStart, s.selEnd)
        val sel = s.text.substring(a, b)
        val text = s.text.replaceRange(a, b, "[$sel](https://)")
        if (a == b) return EditState(text, a + 1)
        val urlStart = a + sel.length + 3
        return EditState(text, urlStart, urlStart + "https://".length)
    }

    private fun lines(s: EditState, f: (String) -> String): EditState {
        val a = minOf(s.selStart, s.selEnd); val b = maxOf(s.selStart, s.selEnd)
        val from = s.text.lastIndexOf('\n', a - 1).let { if (it < 0) 0 else it + 1 }
        val to = s.text.indexOf('\n', b).let { if (it < 0) s.text.length else it }
        val block = s.text.substring(from, to)
        val replaced = block.split('\n').joinToString("\n") { f(it) }
        val text = s.text.replaceRange(from, to, replaced)
        val delta = replaced.length - block.length
        val firstDelta = f(block.substringBefore('\n')).length - block.substringBefore('\n').length
        return EditState(text, (a + firstDelta).coerceAtLeast(from), (b + delta).coerceAtLeast(from))
    }
}

/** Rückgängig/Wiederholen: Schnappschüsse des Textes mit Cursor; schnelles Tippen wird zusammengefasst. */
class UndoStack(initial: EditState, private val mergeWindowMs: Long = 800, private val limit: Int = 200, private val clock: () -> Long = System::currentTimeMillis) {
    private val undo = ArrayDeque<EditState>()
    private val redo = ArrayDeque<EditState>()
    private var current = initial
    private var lastChangeAt = 0L
    private var lastKind = 0

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Meldet einen neuen Stand. Tippen und Löschen direkt hintereinander bilden einen Schritt. */
    fun record(next: EditState) {
        if (next.text == current.text) { current = next; return }
        val now = clock()
        val kind = if (next.text.length >= current.text.length) 1 else 2
        val merge = undo.isNotEmpty() && now - lastChangeAt <= mergeWindowMs && kind == lastKind && kotlin.math.abs(next.text.length - current.text.length) <= 1
        if (!merge) { undo.addLast(current); while (undo.size > limit) undo.removeFirst() }
        redo.clear()
        current = next; lastChangeAt = now; lastKind = kind
    }

    fun undo(): EditState? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current); current = prev; lastKind = 0
        return prev
    }

    fun redo(): EditState? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current); current = next; lastKind = 0
        return next
    }
}

object Excerpt {
    /** Erster Satz des Textes ohne Auszeichnungen: bis zum ersten `.`, `!` oder `?` vor Leerzeichen/Ende; die Ellipse „…“ beendet keinen Satz. */
    fun firstSentence(body: String): String {
        val para = body.lineSequence().map { it.trim() }
            .dropWhile { it.isEmpty() || it.startsWith("#") || it.startsWith("!") || it.startsWith("<") }
            .takeWhile { it.isNotEmpty() && !it.startsWith("#") }.joinToString(" ")
        val plain = para
            .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
            .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
            .replace(Regex("""[*_`~]+"""), "")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("\\s+"), " ").trim()
        val m = Regex("""^.*?[.!?](?=\s|$)""").find(plain)
        return (m?.value ?: plain).trim()
    }
}
