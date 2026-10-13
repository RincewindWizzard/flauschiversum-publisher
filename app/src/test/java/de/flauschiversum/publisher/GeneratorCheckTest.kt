package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.images.ImageProcessor
import de.flauschiversum.publisher.images.ProcessedImage
import de.flauschiversum.publisher.images.Rights
import de.flauschiversum.publisher.photos.PhotoPlacement
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.PhotoWithAlt
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.posts.PostNaming
import de.flauschiversum.publisher.posts.PostProps
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.GeneratorCheck
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Prüft von der App erzeugte Beiträge mit dem echten Generator des Blogs (übersprungen, wenn `BLOG_GENERATOR_DIR` fehlt). */
class GeneratorCheckTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }
    private val wf get() = blog.workflow

    private fun jpeg(): ByteArray {
        val img = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 120) for (y in 0 until 80) img.setRGB(x, y, (x * 2 shl 16) or (y * 3 shl 8) or 90)
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }
    private val placement = PhotoPlacement(object : ImageProcessor {
        override fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights) =
            ProcessedImage(input().use { it.readBytes() }, originalName, 120, 80, true)
    }) { Rights("x", "y") }
    private fun photo(name: String) = SharedPhoto(tmp.newFile().also { it.writeBytes(jpeg()) }, name, "image/jpeg")

    private fun baseline() = GeneratorCheck.check(GeneratorCheck.prepare(tmp.newFolder("base"), wf))

    @Test fun beitraegeDerAppBestehenDiePruefungDesGenerators() {
        val base = baseline()
        assertEquals(base.output, 0, base.exitCode)
        // neuer Beitrag mit Fotos, Foto zu vorhandenem Beitrag, normal gespeicherter neuer Beitrag
        placement.place(wf, PhotoTarget.New("Süße Mütze: ein \"Test\"", "Ermeline"), listOf(PhotoWithAlt(photo("Schöne Mütze.jpg"), "Eine Mütze")))
        placement.place(wf, PhotoTarget.Existing("baumaerkte"), listOf(PhotoWithAlt(photo("IMG_1.jpg"), "Baum"), PhotoWithAlt(photo("IMG_2.jpg"), "Zweig")))
        val date = PostDate(LocalDate.of(2026, 10, 13), null)
        val doc = PostEditing.apply(null, null, PostProps("Zweiter Beitrag", date, PostNaming.permalink(date.date, "zweiter-beitrag"), listOf("Filzen"), "Ermeline", null, "Kurz."), "Hallo Welt.\n")
        wf.commit(listOf(TreeChange.Put("content/posts/zweiter-beitrag/index.md", doc.text.toByteArray())), "Beitrag: Zweiter Beitrag")
        val after = GeneratorCheck.check(GeneratorCheck.prepare(tmp.newFolder("after"), wf))
        assertEquals(after.output, 0, after.exitCode)
        assertEquals("Keine neuen Warnungen erwartet:\n${after.output}", base.warnings, after.warnings)
        assertTrue(after.output, after.output.contains("196 Posts"))
    }
}
