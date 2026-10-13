package de.flauschiversum.publisher.posts

import java.time.LocalDate

/** Namen und Adressen von Beiträgen nach den Regeln des Blogs (Spec `url-scheme`). */
object PostNaming {
    /**
     * Wie der Blog aus einem Verzeichnisnamen den Teil der Adresse bildet: klein, `ä`→`ae`, `ö`→`oe`, `ü`→`ue`,
     * `ß`→`ss`, alle anderen Zeichen außer `a-z0-9` werden zu `-` (mehrere hintereinander zu einem).
     */
    fun slug(text: String): String {
        val sb = StringBuilder()
        for (c in text.lowercase()) {
            when (c) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'ß' -> sb.append("ss")
                in 'a'..'z', in '0'..'9' -> sb.append(c)
                else -> if (sb.isNotEmpty() && sb.last() != '-') sb.append('-')
            }
        }
        return sb.toString().trim('-')
    }

    /** Vorschlag für den Verzeichnisnamen eines neuen Beitrags aus dem Titel. */
    fun dirNameFromTitle(title: String): String = slug(title).ifEmpty { "beitrag" }

    /**
     * Macht [base] eindeutig gegenüber den vorhandenen Verzeichnisnamen: erst mit dem Jahr, dann mit Zähler.
     * Der Vergleich ignoriert Groß-/Kleinschreibung (Dateisysteme von Windows und macOS).
     */
    fun uniqueDirName(base: String, existing: Collection<String>, year: Int): String {
        val taken = existing.map { it.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        val withYear = "$base-$year"
        if (withYear.lowercase() !in taken) return withYear
        var n = 2
        while ("$withYear-$n".lowercase() in taken) n++
        return "$withYear-$n"
    }

    /** Adresse eines Beitrags nach dem Schema `/JJJJ/MM/slug/`. */
    fun permalink(date: LocalDate, dirName: String): String =
        "/%04d/%02d/%s/".format(date.year, date.monthValue, slug(dirName))

    /** Prüft einen Permalink: beginnt und endet mit `/`, keine Leerzeichen, keine doppelten Schrägstriche. */
    fun permalinkError(value: String): String? = when {
        value.isBlank() -> "Der Permalink darf nicht leer sein."
        !value.startsWith("/") -> "Der Permalink muss mit „/“ beginnen."
        !value.endsWith("/") -> "Der Permalink muss mit „/“ enden."
        value.any { it.isWhitespace() } -> "Der Permalink darf keine Leerzeichen enthalten."
        "//" in value -> "Der Permalink darf kein „//“ enthalten."
        else -> null
    }
}
