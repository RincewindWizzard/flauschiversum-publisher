package de.flauschiversum.publisher

import android.graphics.Bitmap
import de.flauschiversum.publisher.overview.Thumbnails
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailsTest {
    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF3366CC.toInt())
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    @Test fun grossesBildWirdHeruntergerechnetUndZweiterAufrufKommtAusDemZwischenspeicher() {
        var loads = 0
        val big = jpeg(3000, 2000)
        val t = Thumbnails { _, _ -> loads++; big }
        val first = t.get("rev", "a.jpg")!!
        assertTrue("Breite ${first.width}", first.width in 200..800)
        assertTrue(first.byteCount < 1_000_000) // statt 24 MB bei voller Größe
        val second = t.get("rev", "a.jpg")
        assertSame(first, second); assertEquals(1, loads); assertEquals(1, t.decodeCount)
    }

    @Test fun nichtLesbaresBildErgibtNullUndWirdMerkWuerdig() {
        val t = Thumbnails { _, _ -> byteArrayOf(1, 2, 3) }
        assertNull(t.get("r", "x.jpg")); assertNull(t.get("r", "x.jpg"))
        assertEquals(1, t.decodeCount)
        assertNull(Thumbnails { _, _ -> null }.get("r", "fehlt.jpg"))
    }

    @Test fun aeltesteEintraegeWerdenVerdraengt() {
        val small = jpeg(100, 100)
        val t = Thumbnails(maxEntries = 2) { _, _ -> small }
        t.get("r", "1"); t.get("r", "2"); t.get("r", "3")
        t.get("r", "1")
        assertEquals(4, t.decodeCount)
        assertNotNull(t.get("r", "3"))
    }
}
