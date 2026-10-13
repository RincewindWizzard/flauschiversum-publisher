package de.flauschiversum.publisher.images

/** Berechnungen zur Bildgröße und Drehung (rein, damit sie sich testen lassen). */
object ImageSizing {
    /** Längste Kante eines abgelegten Bildes in Pixeln (die Größe `1000x` des Blogs). */
    const val MAX_EDGE = 1000

    /** Obergrenze für die Dateigröße eines abgelegten Bildes in Bytes. */
    const val MAX_BYTES = 1_000_000

    data class Size(val width: Int, val height: Int)

    /**
     * Zielgröße: Die längere Kante misst höchstens [maxEdge], das Seitenverhältnis bleibt, kleinere Bilder werden
     * nicht vergrößert. [width] und [height] sind die bereits gedrehten (aufrechten) Maße.
     */
    fun target(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Size {
        require(width > 0 && height > 0) { "Ungültige Bildgröße ${width}x$height" }
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return Size(width, height)
        val factor = maxEdge.toDouble() / longest
        return Size(maxOf(1, Math.round(width * factor).toInt()), maxOf(1, Math.round(height * factor).toInt()))
    }

    /** Vertauscht Breite und Höhe, wenn die EXIF-Ausrichtung das Bild um 90° oder 270° dreht (5 bis 8). */
    fun upright(width: Int, height: Int, orientation: Int): Size =
        if (orientation in 5..8) Size(height, width) else Size(width, height)

    /** Größtes `inSampleSize` (Potenz von 2), bei dem das Bild noch mindestens so groß wie das Ziel bleibt. */
    fun sampleSize(srcLongest: Int, targetLongest: Int): Int {
        var sample = 1
        while (srcLongest / (sample * 2) >= targetLongest) sample *= 2
        return sample
    }

    /** JPEG-Qualitätsstufen, die der Reihe nach versucht werden, bis die Datei klein genug ist. */
    val JPEG_QUALITIES = listOf(92, 88, 84, 80, 75, 70, 65, 60, 50, 40)
}
