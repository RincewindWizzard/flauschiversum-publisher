package de.flauschiversum.publisher.editor

import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

enum class Severity { ERROR, WARNING }

/** Ein Befund der Markdown-Prüfung; [start] und [end] sind Zeichenpositionen im Text (Ende ausgeschlossen). */
data class Finding(val severity: Severity, val message: String, val start: Int, val end: Int)

/** Umrechnung zwischen Zeichenposition und Zeilennummer (ab 1). */
class LineIndex(val text: String) {
    val starts: IntArray = buildList { add(0); text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }.toIntArray()
    fun offset(line: Int, column: Int): Int = (starts[line.coerceIn(0, starts.size - 1)] + column).coerceAtMost(text.length)
    fun lineOf(offset: Int): Int { var lo = 0; var hi = starts.size - 1; while (lo < hi) { val m = (lo + hi + 1) / 2; if (starts[m] <= offset) lo = m else hi = m - 1 }; return lo + 1 }
    fun lineStart(line: Int): Int = starts[(line - 1).coerceIn(0, starts.size - 1)]
    fun lineEnd(line: Int): Int = if (line >= starts.size) text.length else (starts[line] - 1).coerceAtLeast(starts[line - 1])
}

/**
 * Prüfung des Markdown-Textes nach dem Dialekt des Blogs (CommonMark mit Tabellen, Durchstreichen, Fußnoten; HTML erlaubt).
 * CommonMark kennt keine Syntaxfehler; deshalb sucht die Prüfung im Baum nach wörtlich stehengebliebenen Resten.
 */
object MarkdownLint {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), FootnotesExtension.create())
    val parser: Parser = Parser.builder().extensions(extensions).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()

    /**
     * Der Blog schreibt Bildziele mit Leerzeichen vor dem Parsen um, CommonMark würde sie als Text lassen. Hier werden
     * die Leerzeichen durch geschützte Leerzeichen gleicher Länge ersetzt, damit Positionen stimmen.
     */
    fun normalizeForParsing(text: String): String =
        IMAGE_DEST.replace(text) { m -> m.groupValues[1] + m.groupValues[2].replace(' ', '\u00A0') + m.groupValues[3] }

    private val ESCAPED = Regex("""\\.""")
    private val IMAGE_DEST = Regex("""(!\[[^\]\n]*]\()([^)\n]*)(\))""")

    fun parse(text: String): Node = parser.parse(normalizeForParsing(text))

    /** Prüft [text]. [original] ist der Text beim Öffnen: Fehlt der Abschnitt `## Anhänge` jetzt, gibt es eine Warnung. */
    fun check(text: String, original: String? = null): List<Finding> {
        val idx = LineIndex(text)
        val findings = mutableListOf<Finding>()
        val doc = parse(text)
        val seen = HashSet<Triple<Severity, Int, String>>()
        fun add(sev: Severity, msg: String, start: Int, end: Int) {
            if (seen.add(Triple(sev, start, msg))) findings += Finding(sev, msg, start, maxOf(end, start + 1).coerceAtMost(text.length))
        }
        var lastLevel = 0
        doc.accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                if (lastLevel != 0 && heading.level > lastLevel + 1)
                    spans(heading, idx).let { (s, e) -> add(Severity.WARNING, "Überschriftenebene übersprungen (von ${lastLevel} auf ${heading.level})", s, e) }
                lastLevel = heading.level
                super.visit(heading)
            }
            override fun visit(t: Text) {
                val (s, e) = spans(t, idx)
                if (e <= s) return
                // Maskierte Zeichen (\*) sind gewollt und zählen nicht
                val masked = text.substring(s, e).replace(ESCAPED) { "  " }
                textRules(masked, s, ::add)
            }
            override fun visit(link: Link) {
                val (s, e) = spans(link, idx)
                if (link.destination.isNullOrBlank()) add(Severity.WARNING, "Link ohne Ziel", s, e)
                else if (!hasText(link)) add(Severity.WARNING, "Link ohne Text", s, e)
                super.visit(link)
            }
            override fun visit(image: Image) { super.visit(image) }
            override fun visit(code: Code) {}
            override fun visit(block: FencedCodeBlock) {}
            override fun visit(custom: org.commonmark.node.CustomBlock) {
                if (custom is TableBlock) tableRules(custom, idx, text, ::add)
                super.visit(custom)
            }
        })
        unclosedFences(text, idx, ::add)
        blankLines(text, idx, ::add)
        spacesBeforeHeading(text, idx, ::add)
        if (original != null && hasAttachments(original) && !hasAttachments(text))
            add(Severity.WARNING, "Der Abschnitt „## Anhänge“ fehlt (Bilder der App werden dort eingefügt)", text.length, text.length)
        return findings.sortedBy { it.start }
    }

    private fun hasAttachments(t: String) = t.lineSequence().any { it.trimEnd() == "## Anhänge" }

    private fun hasText(link: Link): Boolean {
        var n: Node? = link.firstChild
        while (n != null) { if (n !is Text || n.literal.isNotBlank()) return true; n = n.next }
        return false
    }

    private fun spans(n: Node, idx: LineIndex): Pair<Int, Int> {
        val list = n.sourceSpans
        if (list.isEmpty()) return 0 to 0
        val first = list.first(); val last = list.last()
        return idx.offset(first.lineIndex, first.columnIndex) to idx.offset(last.lineIndex, last.columnIndex + last.length)
    }

    // ---- Reste im Text: nicht geschlossene Auszeichnungen, Klammern
    private fun textRules(literal: String, base: Int, add: (Severity, String, Int, Int) -> Unit) {
        for (marker in listOf("**", "__", "~~")) {
            var i = literal.indexOf(marker)
            while (i >= 0) { add(Severity.ERROR, "Nicht geschlossene Hervorhebung „$marker“", base + i, base + i + marker.length); i = literal.indexOf(marker, i + marker.length) }
        }
        LONELY_EM.findAll(literal).forEach { m -> add(Severity.ERROR, "Nicht geschlossene Hervorhebung „${m.value.trim()}“", base + m.range.first + m.value.indexOfFirst { it == '*' || it == '_' }, base + m.range.last + 1) }
        var i = literal.indexOf('`')
        if (i >= 0) add(Severity.ERROR, "Nicht geschlossener Code-Schnipsel (`)", base + i, base + i + 1)
        i = literal.indexOf("](")
        while (i >= 0) { add(Severity.ERROR, "Link oder Bild: Klammer nicht geschlossen", base + i, base + i + 2); i = literal.indexOf("](", i + 2) }
        val bang = literal.indexOf("![")
        if (bang >= 0 && literal.indexOf("](", bang) < 0 && !literal.substring(bang).contains("]")) add(Severity.ERROR, "Bild: eckige Klammer nicht geschlossen", base + bang, base + bang + 2)
    }

    /** `*wort` oder `_wort` am Wortanfang ohne schließendes Zeichen (CommonMark ließ sie wörtlich stehen). */
    private val LONELY_EM = Regex("""(^|\s)[*_](?=\S)[^\s*_]+""")

    // ---- Codeblöcke
    private fun unclosedFences(text: String, idx: LineIndex, add: (Severity, String, Int, Int) -> Unit) {
        var open: Pair<Char, Int>? = null; var openLine = 0
        var line = 1
        for (raw in text.split('\n')) {
            val t = raw.trimStart()
            val fence = if (raw.length - t.length <= 3 && (t.startsWith("```") || t.startsWith("~~~"))) t[0] else null
            if (fence != null) {
                val run = t.takeWhile { it == fence }.length
                val o = open
                if (o == null) { open = fence to run; openLine = line }
                else if (o.first == fence && run >= o.second && t.drop(run).isBlank()) open = null
            }
            line++
        }
        open?.let { add(Severity.ERROR, "Codeblock nicht geschlossen", idx.lineStart(openLine), idx.lineEnd(openLine)) }
    }

    // ---- Tabellen
    private fun tableRules(table: TableBlock, idx: LineIndex, text: String, add: (Severity, String, Int, Int) -> Unit) {
        val (s, e) = spans(table, idx)
        if (e <= s) return
        val firstLine = idx.lineOf(s); val lastLine = idx.lineOf(maxOf(s, e - 1))
        val cols = cells(lineText(text, idx, firstLine))
        for (l in firstLine + 2..lastLine) {
            val n = cells(lineText(text, idx, l))
            if (n != cols) add(Severity.ERROR, "Tabellenzeile hat $n statt $cols Zellen", idx.lineStart(l), idx.lineEnd(l))
        }
    }

    private fun lineText(text: String, idx: LineIndex, line: Int) = text.substring(idx.lineStart(line), idx.lineEnd(line))

    /** Zahl der Zellen einer Tabellenzeile (maskierte `\|` und `|` in Code zählen nicht). */
    internal fun cells(line: String): Int {
        var t = line.trim()
        if (t.startsWith("|")) t = t.drop(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        var n = 1; var i = 0; var inCode = false
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' -> i++
                c == '`' -> inCode = !inCode
                c == '|' && !inCode -> n++
            }
            i++
        }
        return n
    }

    // ---- Warnungen ohne Baum
    private fun blankLines(text: String, idx: LineIndex, add: (Severity, String, Int, Int) -> Unit) {
        val lines = text.split('\n')
        var run = 0; var runStart = 0; var inFence = false
        for ((i, l) in lines.withIndex()) {
            if (l.trimStart().startsWith("```") || l.trimStart().startsWith("~~~")) inFence = !inFence
            if (l.isBlank() && !inFence) { if (run == 0) runStart = i + 1; run++ }
            else { if (run >= 2) add(Severity.WARNING, "Mehrere Leerzeilen hintereinander", idx.lineStart(runStart), idx.lineEnd(runStart + run - 1)); run = 0 }
        }
    }

    private fun spacesBeforeHeading(text: String, idx: LineIndex, add: (Severity, String, Int, Int) -> Unit) {
        val lines = text.split('\n')
        for (i in 1 until lines.size) {
            if (lines[i].startsWith("#") && Regex("^#{1,6} ").containsMatchIn(lines[i]) && lines[i - 1].isNotBlank() && lines[i - 1].length != lines[i - 1].trimEnd().length)
                add(Severity.WARNING, "Leerzeichen am Zeilenende vor einer Überschrift", idx.lineStart(i) + 0, idx.lineEnd(i))
        }
    }
}
