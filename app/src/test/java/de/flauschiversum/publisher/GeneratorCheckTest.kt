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
import java.io.File
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

    @Test fun bearbeiteterBeitragBestehtDiePruefungDesGenerators() {
        val base = baseline()
        val reader = de.flauschiversum.publisher.posts.PostReader(blog.store)
        val old = reader.document(wf.refs.target, "baumaerkte")!!
        val body = "Größe & Mütze **fett**, _kursiv_ und eine Tabelle:\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\nNadja Bender<br/>\n"
        val props = PostEditing.read(old)
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", PostEditing.apply(old, props, props, body).text.toByteArray())), "Beitrag geändert")
        val after = GeneratorCheck.check(GeneratorCheck.prepare(tmp.newFolder("after"), wf))
        assertEquals(after.output, 0, after.exitCode)
        assertEquals(base.warnings, after.warnings)
    }

    @Test fun beitragMitNeuemSchlagwortBautUndErzeugtDieSchlagwortSeite() {
        de.flauschiversum.publisher.posts.TagCreation.create(wf, "Makramee")
        val date = PostDate(LocalDate.of(2026, 10, 13), null)
        val doc = PostEditing.apply(null, null, PostProps("Makramee-Test", date, PostNaming.permalink(date.date, "makramee-test"), listOf("Makramee"), "Ermeline", null, null), "Knoten.\n")
        wf.commit(listOf(TreeChange.Put("content/posts/makramee-test/index.md", doc.text.toByteArray())), "Beitrag: Makramee-Test")
        val work = GeneratorCheck.prepare(tmp.newFolder("tag"), wf)
        val (result, dist) = GeneratorCheck.build(work)
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(File(dist, "tag/makramee/index.html").isFile)
        assertTrue(File(dist, "tag/makramee/index.html").readText().contains("Makramee-Test"))
    }

    @Test fun geloeschterBeitragVerschwindetUndLeitetWeiter() {
        de.flauschiversum.publisher.posts.PostDeletion.delete(wf, "baumaerkte", de.flauschiversum.publisher.posts.PostDeletion.DEFAULT_TARGET)
        val work = GeneratorCheck.prepare(tmp.newFolder("del"), wf)
        val (result, dist) = GeneratorCheck.build(work)
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output, result.errors.isEmpty())
        val old = File(dist, "2013/07/baumarkte/index.html")
        assertTrue("Weiterleitungsseite fehlt", old.isFile)
        assertTrue(old.readText().contains("/beitraege/"))
        assertTrue(!File(dist, "2013/07/baumarkte/index.md").exists())
    }

    @Test fun fotoverwaltungErzeugtKeineWarnungenZuFehlendenMedien() {
        val base = baseline()
        placement.place(wf, PhotoTarget.Existing("baumaerkte"), listOf(PhotoWithAlt(photo("A.jpg"), "a"), PhotoWithAlt(photo("B.jpg"), "b"), PhotoWithAlt(photo("C.jpg"), "c")))
        de.flauschiversum.publisher.posts.PhotoManagement.move(wf, "baumaerkte", "C.jpg", 0)
        de.flauschiversum.publisher.posts.PhotoManagement.setAlt(wf, "baumaerkte", "B.jpg", "Neu [b]")
        de.flauschiversum.publisher.posts.PhotoManagement.setCover(wf, "baumaerkte", "A.jpg")
        de.flauschiversum.publisher.posts.PhotoManagement.remove(wf, "baumaerkte", "B.jpg")
        val after = GeneratorCheck.check(GeneratorCheck.prepare(tmp.newFolder("fotos"), wf))
        assertEquals(after.output, 0, after.exitCode)
        assertEquals(base.warnings, after.warnings)
    }
}
