package de.flauschiversum.publisher.editor

enum class SpanKind { HEADING, BOLD, ITALIC, LINK, IMAGE, LIST, QUOTE, CODE, STRIKE }

data class StyleSpan(val start: Int, val end: Int, val kind: SpanKind)

/** Zeilenbasierte Hervorhebung von Markdown (schnell genug für lange Texte, ohne vollständigen Parser). */
object Highlighting {
    fun spans(text: String): List<StyleSpan> {
        val out = ArrayList<StyleSpan>()
        var pos = 0; var inFence = false
        for (line in text.split('\n')) {
            val end = pos + line.length
            val t = line.trimStart()
            val indent = line.length - t.length
            when {
                t.startsWith("```") || t.startsWith("~~~") -> { out += StyleSpan(pos, end, SpanKind.CODE); inFence = !inFence }
                inFence -> out += StyleSpan(pos, end, SpanKind.CODE)
                indent <= 3 && HEADING.containsMatchIn(t) -> out += StyleSpan(pos, end, SpanKind.HEADING)
                t.startsWith(">") -> { out += StyleSpan(pos, end, SpanKind.QUOTE); inline(line, pos, out) }
                IMAGE_LINE.matches(t.trimEnd()) -> out += StyleSpan(pos, end, SpanKind.IMAGE)
                else -> {
                    LIST.find(line)?.let { out += StyleSpan(pos + it.range.first, pos + it.range.last + 1, SpanKind.LIST) }
                    inline(line, pos, out)
                }
            }
            pos = end + 1
        }
        return out
    }

    private fun inline(line: String, base: Int, out: MutableList<StyleSpan>) {
        for (m in CODE_SPAN.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.CODE)
        for (m in BOLD.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.BOLD)
        for (m in ITALIC.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.ITALIC)
        for (m in STRIKE.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.STRIKE)
        for (m in IMAGE.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.IMAGE)
        for (m in LINK.findAll(line)) out += StyleSpan(base + m.range.first, base + m.range.last + 1, SpanKind.LINK)
    }

    private val HEADING = Regex("^#{1,6}(\\s|$)")
    private val LIST = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s")
    private val IMAGE_LINE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val CODE_SPAN = Regex("`[^`\n]+`")
    private val BOLD = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
    private val ITALIC = Regex("""(?<![*\w])([*_])(?=[^\s*_])(.+?)(?<=[^\s*_])\1(?![*\w])""")
    private val STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
    private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val LINK = Regex("""(?<!!)\[[^\]]*]\([^)]*\)""")
}
