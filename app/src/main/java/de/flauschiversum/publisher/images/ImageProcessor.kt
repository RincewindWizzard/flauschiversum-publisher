package de.flauschiversum.publisher.images

import java.io.InputStream

/** Ein verarbeitetes Bild, fertig zum Ablegen im Beitragsverzeichnis. */
class ProcessedImage(
    val bytes: ByteArray,
    /** Dateiname mit der zum Format passenden Endung. */
    val fileName: String,
    val width: Int,
    val height: Int,
    /** Die Datei ist höchstens [ImageSizing.MAX_BYTES] groß (bei verlustfreien Formaten nicht immer erreichbar). */
    val withinLimit: Boolean,
)

class ImageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Macht aus einem geteilten Foto ein Bild für den Blog. */
interface ImageProcessor {
    /**
     * Skaliert so, dass die längere Kante höchstens [ImageSizing.MAX_EDGE] misst, wendet die EXIF-Drehung an,
     * entfernt Standortdaten und schreibt die [rights]. Das Dateiformat bleibt erhalten.
     */
    fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights): ProcessedImage
}

object ImageNames {
    /** Dateiname aus dem Anzeigenamen eines geteilten Bildes: ohne Pfadteile, ohne Steuerzeichen. */
    fun sanitize(name: String?, fallbackBase: String = "Foto", ext: String = "jpg"): String {
        val base = (name ?: "").substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001F\"*:<>?|]"), "_").trim().trim('.')
        if (base.isEmpty() || base.substringBeforeLast('.', "").isEmpty()) {
            val stem = base.ifEmpty { fallbackBase }.substringBeforeLast('.')
            return "$stem.$ext"
        }
        return base
    }

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun withExtension(name: String, ext: String): String = name.substringBeforeLast('.', name) + "." + ext

    /** Macht [name] eindeutig gegenüber [taken]: `IMG_1.jpg` wird zu `IMG_1-2.jpg`, `IMG_1-3.jpg` … (ohne Beachtung der Groß-/Kleinschreibung). */
    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        val stem = name.substringBeforeLast('.', name); val ext = name.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem-$n" else "$stem-$n.$ext"
            if (candidate.lowercase() !in lower) return candidate
            n++
        }
    }
}
