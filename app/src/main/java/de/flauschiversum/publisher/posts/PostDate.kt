package de.flauschiversum.publisher.posts

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Das Datum eines Beitrags, `JJJJ-MM-TT` oder `JJJJ-MM-TT HH:MM:SS` (Ortszeit Europe/Berlin). */
data class PostDate(val date: LocalDate, val time: LocalTime?) {
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, time ?: LocalTime.MIDNIGHT)

    /** Schreibweise im Frontmatter; bleibt für gelesene Werte gleich, wenn nichts geändert wird. */
    fun render(): String = if (time == null) date.toString() else
        "%s %02d:%02d:%02d".format(date.toString(), time.hour, time.minute, time.second)

    companion object {
        private val pattern = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?\s*$""")

        fun parse(text: String?): PostDate? {
            val m = pattern.matchEntire(text?.trim() ?: return null) ?: return null
            return runCatching {
                val d = LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                val t = if (m.groupValues[4].isEmpty()) null else
                    LocalTime.of(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].ifEmpty { "0" }.toInt())
                PostDate(d, t)
            }.getOrNull()
        }
    }
}
