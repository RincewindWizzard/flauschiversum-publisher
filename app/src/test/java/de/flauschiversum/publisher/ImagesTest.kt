package de.flauschiversum.publisher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import de.flauschiversum.publisher.images.AndroidImageProcessor
import de.flauschiversum.publisher.images.ImageException
import de.flauschiversum.publisher.images.ImageNames
import de.flauschiversum.publisher.images.ImageSizing
import de.flauschiversum.publisher.images.JpegXmp
import de.flauschiversum.publisher.images.Rights
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class ImageSizingTest {
    @Test fun grosseQuerformatBilder() {
        assertEquals(ImageSizing.Size(1000, 750), ImageSizing.target(4000, 3000))
    }
    @Test fun grosseHochformatBilderBegrenzenDieLaengereKante() {
        assertEquals(ImageSizing.Size(750, 1000), ImageSizing.target(3000, 4000))
    }
    @Test fun kleineBilderWerdenNichtVergroessert() {
        assertEquals(ImageSizing.Size(562, 750), ImageSizing.target(562, 750))
        assertEquals(ImageSizing.Size(1000, 1000), ImageSizing.target(1000, 1000))
    }
    @Test fun ausrichtungVertauschtBreiteUndHoehe() {
        assertEquals(ImageSizing.Size(1200, 1600), ImageSizing.upright(1600, 1200, 6))
        assertEquals(ImageSizing.Size(1600, 1200), ImageSizing.upright(1600, 1200, 1))
        assertEquals(ImageSizing.Size(1200, 1600), ImageSizing.upright(1600, 1200, 8))
    }
    @Test fun sampleSizeBleibtGroessAlsDasZiel() {
        assertEquals(4, ImageSizing.sampleSize(4000, 1000)); assertEquals(1, ImageSizing.sampleSize(1500, 1000)); assertEquals(4, ImageSizing.sampleSize(4096, 1000))
    }
}

class ImageNamesTest {
    @Test fun dateinameBleibtMitUmlautenUndLeerzeichen() {
        assertEquals("Schwacher Moment.jpg", ImageNames.sanitize("Schwacher Moment.jpg"))
        assertEquals("Ärmel nähen.jpg", ImageNames.sanitize("Ärmel nähen.jpg"))
    }
    @Test fun pfadteileUndSteuerzeichenWerdenEntfernt() {
        assertEquals("a.jpg", ImageNames.sanitize("../../etc/a.jpg"))
        assertEquals("a_b.jpg", ImageNames.sanitize("a:b.jpg"))
        assertEquals("Foto.jpg", ImageNames.sanitize(null)); assertEquals("x.jpg", ImageNames.sanitize("x"))
    }
    @Test fun namenskollisionBekommtZaehler() {
        assertEquals("IMG_1.jpg", ImageNames.unique("IMG_1.jpg", listOf("a.jpg")))
        assertEquals("IMG_1-2.jpg", ImageNames.unique("IMG_1.jpg", listOf("IMG_1.jpg")))
        assertEquals("IMG_1-3.jpg", ImageNames.unique("IMG_1.jpg", listOf("img_1.JPG", "IMG_1-2.jpg")))
    }
    @Test fun endungWirdAnDasFormatAngepasst() {
        assertEquals("a.jpg", ImageNames.withExtension("a.heic", "jpg")); assertEquals("jpg", ImageNames.extension("A.JPG"))
    }
}

class JpegXmpTest {
    private val rights = Rights("Flauschiversum / Ermeline Wollknoll", "https://flauschiversum.de/impressum/")
    private fun tinyJpeg(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0x00, 0xFF.toByte(), 0xD9.toByte())

    @Test fun xmpWirdEingefuegtUndLaesstSichLesen() {
        val out = JpegXmp.insert(tinyJpeg(), rights)
        val xmp = JpegXmp.read(out)!!
        assertTrue(xmp.contains("<dc:creator>")); assertTrue(xmp.contains("Flauschiversum / Ermeline Wollknoll"))
        assertTrue(xmp.contains("<dc:rights>")); assertTrue(xmp.contains("<xmpRights:WebStatement>https://flauschiversum.de/impressum/</xmpRights:WebStatement>"))
        assertEquals(0xFF.toByte(), out[0]); assertEquals(0xD8.toByte(), out[1])
    }
    @Test fun vorhandenesXmpWirdErsetztNichtVerdoppelt() {
        val once = JpegXmp.insert(tinyJpeg(), rights)
        val twice = JpegXmp.insert(once, rights.copy(holder = "Anderer"))
        assertTrue(JpegXmp.read(twice)!!.contains("Anderer")); assertFalse(JpegXmp.read(twice)!!.contains("Ermeline"))
        assertEquals(1, Regex("<x:xmpmeta").findAll(JpegXmp.read(twice)!!).count())
    }
    @Test fun sonderzeichenImRechtetextWerdenMaskiert() {
        val xmp = JpegXmp.read(JpegXmp.insert(tinyJpeg(), Rights("A & B <c>", "https://x/")))!!
        assertTrue(xmp.contains("A &amp; B &lt;c&gt;"))
    }
    @Test fun keineJpegDateiWirdAbgelehnt() {
        assertTrue(runCatching { JpegXmp.insert(byteArrayOf(1, 2, 3, 4, 5), rights) }.isFailure)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageProcessorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rights = Rights("Flauschiversum / Ermeline Wollknoll", "https://flauschiversum.de/impressum/")
    private fun processor() = AndroidImageProcessor(tmp.newFolder("exif"))

    private fun noisyBitmap(w: Int, h: Int, seed: Long = 1): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = Random(seed); val row = IntArray(w)
        for (y in 0 until h) { for (x in 0 until w) row[x] = (0xFF shl 24) or (rnd.nextInt() and 0xFFFFFF); bmp.setPixels(row, 0, w, 0, y, w, 1) }
        return bmp
    }
    private fun gradientBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        for (y in 0 until h) { for (x in 0 until w) row[x] = (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or 128; bmp.setPixels(row, 0, w, 0, y, w, 1) }
        return bmp
    }
    private fun jpeg(bmp: Bitmap, orientation: Int? = null, gps: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val bytes = out.toByteArray()
        if (orientation == null && !gps) return bytes
        val f = tmp.newFile(); f.writeBytes(bytes)
        val exif = ExifInterface(f)
        if (orientation != null) exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        if (gps) exif.setLatLong(54.3233, 10.1228)
        exif.saveAttributes()
        return f.readBytes()
    }
    private fun dims(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o); return o.outWidth to o.outHeight
    }
    private fun run(bytes: ByteArray, name: String = "IMG_1.jpg", mime: String? = "image/jpeg") = processor().process({ ByteArrayInputStream(bytes) }, name, mime, rights)

    @Test fun grossesHandyfotoWirdAufTausendPixelBreiteUndEinMegabyteVerkleinert() {
        val r = run(jpeg(noisyBitmap(4000, 3000)))
        assertEquals(1000 to 750, dims(r.bytes)); assertEquals(1000, r.width); assertEquals(750, r.height)
        assertTrue("Größe ${r.bytes.size}", r.bytes.size <= ImageSizing.MAX_BYTES); assertTrue(r.withinLimit)
        assertEquals("IMG_1.jpg", r.fileName)
    }

    @Test fun hochformatWirdAufTausendPixelHoeheBegrenzt() {
        val r = run(jpeg(gradientBitmap(3000, 4000)))
        assertEquals(750 to 1000, dims(r.bytes))
    }

    @Test fun kleinesBildWirdNichtVergroessert() {
        val r = run(jpeg(gradientBitmap(562, 750)))
        assertEquals(562 to 750, dims(r.bytes))
    }

    @Test fun exifDrehungSechsErgibtAufrechtesHochformat() {
        // Quer gespeichert (1600×1200) mit Orientation 6 → aufrecht 1200×1600 → 750×1000
        val r = run(jpeg(gradientBitmap(1600, 1200), orientation = ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(750 to 1000, dims(r.bytes))
        val exif = ExifInterface(ByteArrayInputStream(r.bytes))
        val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        assertTrue("Ausrichtung muss neutral sein, war $o", o == ExifInterface.ORIENTATION_NORMAL || o == ExifInterface.ORIENTATION_UNDEFINED)
    }

    @Test fun standortDatenWerdenEntferntRechteangabenGeschrieben() {
        val input = jpeg(gradientBitmap(800, 600), gps = true)
        assertNotNull(ExifInterface(ByteArrayInputStream(input)).latLong)
        val r = run(input)
        val exif = ExifInterface(ByteArrayInputStream(r.bytes))
        assertNull("GPS muss weg sein", exif.latLong)
        assertEquals("Flauschiversum / Ermeline Wollknoll", exif.getAttribute(ExifInterface.TAG_ARTIST))
        assertEquals("Flauschiversum / Ermeline Wollknoll", exif.getAttribute(ExifInterface.TAG_COPYRIGHT))
        val xmp = JpegXmp.read(r.bytes)!!
        assertTrue(xmp.contains("Flauschiversum / Ermeline Wollknoll")); assertTrue(xmp.contains("https://flauschiversum.de/impressum/"))
    }

    @Test fun geaenderterRechtetextLandetImNaechstenBild() {
        val r = processor().process({ ByteArrayInputStream(jpeg(gradientBitmap(300, 200))) }, "a.jpg", "image/jpeg", Rights("Mein Blog", "https://x.example/"))
        assertEquals("Mein Blog", ExifInterface(ByteArrayInputStream(r.bytes)).getAttribute(ExifInterface.TAG_ARTIST))
        assertTrue(JpegXmp.read(r.bytes)!!.contains("Mein Blog"))
    }

    @Test fun pngBleibtPng() {
        val out = ByteArrayOutputStream(); gradientBitmap(1500, 1000).compress(Bitmap.CompressFormat.PNG, 100, out)
        val r = run(out.toByteArray(), "Skizze.png", "image/png")
        assertEquals("Skizze.png", r.fileName); assertEquals(1000 to 667, dims(r.bytes))
        assertEquals(0x89.toByte(), r.bytes[0]) // PNG-Kennung
    }

    @Test fun gifWirdUnveraendertUebernommen() {
        val gif = "GIF89a".toByteArray() + ByteArray(100) { it.toByte() }
        val r = run(gif, "anim.gif", "image/gif")
        assertArrayEquals(gif, r.bytes); assertEquals("anim.gif", r.fileName)
    }

    @Test fun unbekanntesBildformatWirdAlsJpegAbgelegt() {
        val r = run(jpeg(gradientBitmap(400, 300)), "IMG_0001.HEIC", "image/heic")
        assertEquals("IMG_0001.jpg", r.fileName); assertEquals(0xFF.toByte(), r.bytes[0])
    }

    @Test fun nichtLesbareDateiMeldetEinenFehler() {
        val e = runCatching { run(ByteArray(64) { 7 }, "kaputt.jpg") }.exceptionOrNull()
        assertTrue("war: $e", e is ImageException && e.message!!.contains("kaputt.jpg"))
    }

    @Test fun dateinameMitLeerzeichenBleibtErhalten() {
        assertEquals("Schwacher Moment.jpg", run(jpeg(gradientBitmap(200, 100)), "Schwacher Moment.jpg").fileName)
    }
}
