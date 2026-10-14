package de.flauschiversum.publisher

import android.graphics.Bitmap
import de.flauschiversum.publisher.overview.PostIndex
import de.flauschiversum.publisher.overview.Thumbnails
import de.flauschiversum.publisher.testsupport.BlogFixture
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
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

/** Der ganze Weg vom flachen Klon mit echten Binärbildern über den Index bis zur Vorschau. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailPipelineTest {
    @get:Rule val tmp = TemporaryFolder()
    private var blog: BlogFixture? = null
    @After fun tearDown() { blog?.close() }

    private fun jpeg(w: Int, h: Int, color: Int = 0xFF3366CC.toInt()): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bmp.eraseColor(color)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
    private fun post(title: String, image: String?) = "---\ntitle: \"$title\"\ndate: 2026-10-13\n" + (image?.let { "image: $it\n" } ?: "") + "---\nText\n"

    private fun fixture(): BlogFixture {
        val real = File("/home/user/flauschiversum/content/posts/winter-is-coming/DSCF5737.JPG").takeIf { it.isFile }?.readBytes() ?: jpeg(4000, 3000)
        val files = mapOf(
            "content/posts/mit-bild/index.md" to post("Mit Bild", "foto.jpg"),
            "content/posts/erstes-bild/index.md" to post("Erstes Bild", null),
            "content/posts/grosses-foto/index.md" to post("Großes Foto", "DSCF5737.JPG"),
            "content/posts/ohne-bild/index.md" to post("Ohne Bild", null),
            "content/posts/falsches-feld/index.md" to post("Falsches Feld", "gibtsnicht.jpg"),
            "content/tags.yaml" to "- name: Wolle\n",
        )
        val bin = mapOf(
            "content/posts/mit-bild/foto.jpg" to jpeg(800, 600), "content/posts/mit-bild/andere.jpg" to jpeg(100, 100),
            "content/posts/erstes-bild/b.PNG" to jpeg(300, 300), "content/posts/erstes-bild/A.JPG" to jpeg(300, 200),
            "content/posts/grosses-foto/DSCF5737.JPG" to real,
            "content/posts/falsches-feld/z.jpg" to jpeg(400, 400),
        )
        return BlogFixture(tmp.newFolder("blog"), files, bin).also { it.clone(); blog = it }
    }

    @Test fun jederBeitragMitBildBekommtEineVorschau() {
        val b = fixture()
        val rev = b.workflow.refs.target
        val index = PostIndex(b.store).build(rev).associateBy { it.dir }
        val thumbs = Thumbnails(size = 256) { r, p -> b.store.readBytes(r, p) }
        fun bitmapOf(dir: String) = index[dir]!!.cover?.let { thumbs.get(rev, "content/posts/$dir/$it") }
        assertEquals("foto.jpg", index["mit-bild"]!!.cover)
        assertEquals("A.JPG", index["erstes-bild"]!!.cover)           // erstes Bild nach Namen, Endung in Großbuchstaben
        assertEquals("z.jpg", index["falsches-feld"]!!.cover)         // Feld verweist auf fehlende Datei
        assertNull(index["ohne-bild"]!!.cover)
        for (dir in listOf("mit-bild", "erstes-bild", "grosses-foto", "falsches-feld")) {
            val bmp = bitmapOf(dir)
            assertNotNull("Keine Vorschau für $dir (cover=${index[dir]!!.cover})", bmp)
            assertTrue("$dir: ${bmp!!.width}x${bmp.height}", maxOf(bmp.width, bmp.height) in 128..1100)
        }
        assertNull(bitmapOf("ohne-bild"))
    }

    @Test fun grossesFotoWirdKleinDekodiert() {
        val b = fixture(); val rev = b.workflow.refs.target
        val bmp = Thumbnails(size = 256) { r, p -> b.store.readBytes(r, p) }.get(rev, "content/posts/grosses-foto/DSCF5737.JPG")!!
        assertTrue("Speicher ${bmp.byteCount}", bmp.byteCount < 1_000_000)
    }
}
