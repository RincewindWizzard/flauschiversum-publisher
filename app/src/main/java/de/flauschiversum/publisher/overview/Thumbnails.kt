package de.flauschiversum.publisher.overview

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Vorschaubilder (etwa [size] Pixel) mit Downsampling und einem Zwischenspeicher im Arbeitsspeicher (zuletzt benutzte
 * zuerst verdrängt). [load] liefert die Bytes des Bildes.
 */
class Thumbnails(private val maxEntries: Int = 96, private val size: Int = 200, private val load: (rev: String, path: String) -> ByteArray?) {
    private val cache = object : LinkedHashMap<String, Bitmap?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) = this.size > maxEntries
    }
    /** Wie oft ein Bild wirklich dekodiert wurde (für Tests). */
    @Volatile var decodeCount = 0
        private set

    fun get(rev: String, path: String): Bitmap? {
        val key = "$rev:$path"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val bytes = load(rev, path)
        val bmp = bytes?.let { decode(it) }
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        decodeCount++
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }.getOrNull()
}
