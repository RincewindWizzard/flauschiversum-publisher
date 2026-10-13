package de.flauschiversum.publisher.editor

import java.io.File
import java.io.InputStream
import org.apache.lucene.analysis.hunspell.Dictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Text

/** Rechtschreibprüfung eines einzelnen Wortes. */
interface SpellEngine {
    fun isCorrect(word: String): Boolean
    fun suggest(word: String): List<String>
}

/** Hunspell-Wörterbuch (Lucene-Implementierung, rein Java). Zugriffe sind synchronisiert. */
class HunspellEngine(aff: InputStream, dic: InputStream) : SpellEngine {
    private val hunspell = Hunspell(aff.use { a -> dic.use { d -> Dictionary(ByteBuffersDirectory(), "spell", a, d) } })
    @Synchronized override fun isCorrect(word: String) = hunspell.spell(word)
    @Synchronized override fun suggest(word: String): List<String> = runCatching { hunspell.suggest(word) }.getOrDefault(emptyList())
}

/** Persönliches Wörterbuch: ein Wort pro Zeile in einer Datei, Groß-/Kleinschreibung bleibt erhalten. */
class PersonalDictionary(private val file: File) {
    private val words = linkedSetOf<String>()
    init { if (file.isFile) file.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }.forEach { words += it } }

    @Synchronized fun all(): List<String> = words.sortedWith(String.CASE_INSENSITIVE_ORDER)
    @Synchronized fun contains(word: String) = word in words
    @Synchronized fun add(word: String) { if (word.isNotBlank() && words.add(word.trim())) save() }
    @Synchronized fun remove(word: String) { if (words.remove(word)) save() }

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(words.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8); tmp.delete() }
    }
}

data class Misspelling(val start: Int, val end: Int, val word: String)

/**
 * Prüft nur Fließtext und Alt-Texte aus dem Markdown-Baum. Links, Code, HTML, URLs und Dateinamen werden nicht geprüft.
 * [known] liefert zusätzlich akzeptierte Wörter (Schlagworte, persönliches Wörterbuch, „Ignorieren“).
 */
class SpellChecker(private val engine: SpellEngine, private val known: (String) -> Boolean = { false }) {

    fun isCorrect(word: String): Boolean {
        if (known(word)) return true
        if (engine.isCorrect(word)) return true
        if ('-' in word || '\'' in word || '’' in word) {
            val parts = word.split('-', '\'', '’').filter { it.isNotEmpty() }
            if (parts.size > 1 && parts.all { known(it) || engine.isCorrect(it) }) return true
        }
        return false
    }

    fun suggestions(word: String, max: Int = 5): List<String> = engine.suggest(word).filter { it != word }.distinct().take(max)

    fun check(text: String): List<Misspelling> {
        val out = mutableListOf<Misspelling>()
        val idx = LineIndex(text)
        MarkdownLint.parse(text).accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                val only = link.firstChild as? Text
                // Automatischer Link <https://…>: Text ist die Adresse selbst
                if (only != null && only.next == null && only.literal == link.destination) return
                super.visit(link)
            }
            override fun visit(image: Image) { super.visit(image) }
            override fun visit(t: Text) {
                val spans = t.sourceSpans
                if (spans.isEmpty()) return
                for (sp in spans) {
                    val s = idx.offset(sp.lineIndex, sp.columnIndex)
                    val e = idx.offset(sp.lineIndex, sp.columnIndex + sp.length)
                    scan(text, s, e, out)
                }
            }
        })
        return out.distinctBy { it.start }.sortedBy { it.start }
    }

    private fun scan(text: String, from: Int, to: Int, out: MutableList<Misspelling>) {
        if (to <= from) return
        val masked = StringBuilder(text.substring(from, to))
        for (m in MASKED.findAll(masked)) for (i in m.range) masked.setCharAt(i, ' ')
        for (m in WORD.findAll(masked)) {
            val w = m.value
            if (w.length < 2 || w.all { it.isUpperCase() }) continue
            val before = m.range.first - 1; val after = m.range.last + 1
            if ((before >= 0 && masked[before].isDigit()) || (after < masked.length && masked[after].isDigit())) continue
            if (!isCorrect(w)) out += Misspelling(from + m.range.first, from + m.range.last + 1, w)
        }
    }

    companion object {
        private val WORD = Regex("""\p{L}[\p{L}\p{M}]*(?:[-'’]\p{L}[\p{L}\p{M}]*)*""")
        /** Adressen, Mail-Adressen, Zeichenverweise (`&amp;`) und Dateinamen mit Endung sind kein Fließtext. */
        private val MASKED = Regex("""(?:https?://|www\.)\S+|\S+@\S+\.\S+|&#?\w+;|\S+\.(?:jpe?g|png|gif|webp)\b""", RegexOption.IGNORE_CASE)
    }
}
