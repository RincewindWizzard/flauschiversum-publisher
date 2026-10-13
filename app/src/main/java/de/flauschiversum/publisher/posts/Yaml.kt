package de.flauschiversum.publisher.posts

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.FailsafeSchema

/** Kleine Hilfen zum Schreiben und Lesen der YAML-Werte im Frontmatter der Beiträge. */
object Yaml {
    private val reserved = setOf("true", "false", "null", "yes", "no", "on", "off", "y", "n", "~")
    private val number = Regex("""^[-+]?(\d[\d_]*)?(\.\d+)?([eE][-+]?\d+)?$""")
    private val safePlain = Regex("""^[A-Za-zÄÖÜäöüß][A-Za-z0-9ÄÖÜäöüß ._/\-]*$""")

    /** Ob [s] ohne Anführungszeichen als YAML-Wert stehen kann. */
    fun isPlainSafe(s: String): Boolean =
        s.isNotEmpty() && s == s.trim() && safePlain.matches(s) && s.lowercase() !in reserved && !number.matches(s) &&
            ": " !in s && " #" !in s

    /** Doppelt angeführt, wie es der Blog im Frontmatter schreibt (`title: "…"`). */
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    fun plainOrQuoted(s: String): String = if (isPlainSafe(s)) s else quote(s)

    fun inlineList(items: List<String>): String = items.joinToString(", ", "[", "]") { plainOrQuoted(it) }

    /** Liest YAML (nur Zeichenketten, Listen und Zuordnungen; keine Datums- oder Zahlenumwandlung). */
    fun parse(text: String): Map<String, Any?> {
        val settings = LoadSettings.builder().setSchema(FailsafeSchema()).build()
        val loaded = Load(settings).loadFromString(text)
        @Suppress("UNCHECKED_CAST")
        return (loaded as? Map<String, Any?>) ?: emptyMap()
    }
}
