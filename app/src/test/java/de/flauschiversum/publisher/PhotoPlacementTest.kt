package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.images.ImageProcessor
import de.flauschiversum.publisher.images.ProcessedImage
import de.flauschiversum.publisher.images.Rights
import de.flauschiversum.publisher.photos.PhotoPlacement
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.PhotoWithAlt
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.posts.PostBody
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.testsupport.BlogFixture
import java.io.File
import java.io.InputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoPlacementTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture

    /** Ersatz für die Android-Bildverarbeitung: gibt die Bytes unverändert zurück. */
    private val passthrough = object : ImageProcessor {
        override fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights) =
            ProcessedImage(input().use { it.readBytes() }, originalName, 10, 10, true)
    }
    private val placement = PhotoPlacement(passthrough) { Rights("Ich", "https://x/") }
    private val wf get() = blog.workflow

    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }

    private fun photo(name: String, content: String = name) =
        SharedPhoto(tmp.newFile().also { it.writeText(content) }, name, "image/jpeg")

    @Test fun fotoZuVeroeffentlichtemBeitragLandetImVerzeichnisUndUnterAnhaengen() {
        val before = wf.targetId()
        val r = placement.place(wf, PhotoTarget.Existing("baumaerkte"), listOf(PhotoWithAlt(photo("IMG_1.jpg"), "Ein Baum")))
        assertEquals(listOf("IMG_1.jpg"), r.fileNames)
        assertEquals(before, wf.targetId())
        val doc = PostDocument.parse(blog.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md")!!)
        assertTrue(doc.body.trimEnd().endsWith("## Anhänge\n\n![Ein Baum](IMG_1.jpg)"))
        assertEquals("IMG_1.jpg", String(blog.store.readBytes(wf.refs.draft, "content/posts/baumaerkte/IMG_1.jpg")!!))
        // Frontmatter bleibt Zeichen für Zeichen
        val old = PostDocument.parse(blog.store.readText(wf.refs.target, "content/posts/baumaerkte/index.md")!!)
        assertEquals(old.head, doc.head)
        // genau ein Commit mit Bild und index.md
        val changed = wf.ownChanges().map { it.path }.toSet()
        assertEquals(setOf("content/posts/baumaerkte/IMG_1.jpg", "content/posts/baumaerkte/index.md"), changed)
        assertEquals(1, wf.overview().posts.size)
    }

    @Test fun namenskollisionBekommtZaehlerUndMehrereBilderBildenEineGruppe() {
        placement.place(wf, PhotoTarget.Existing("baumaerkte"), listOf(PhotoWithAlt(photo("IMG_1.jpg"), "a")))
        val r = placement.place(wf, PhotoTarget.Existing("baumaerkte"),
            listOf(PhotoWithAlt(photo("IMG_1.jpg", "zweites"), "b"), PhotoWithAlt(photo("IMG_2.jpg"), "c [x]")))
        assertEquals(listOf("IMG_1-2.jpg", "IMG_2.jpg"), r.fileNames)
        val body = PostDocument.parse(blog.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md")!!).body
        assertEquals(1, PostBody.attachmentsHeadingIndex(body).let { if (it >= 0) 1 else 0 })
        assertTrue(body.trimEnd().endsWith("![a](IMG_1.jpg)\n![b](IMG_1-2.jpg)\n![c \\[x\\]](IMG_2.jpg)"))
        assertEquals("a", PostBody.imageLines(body).first().second.alt)
    }

    @Test fun neuerBeitragEntstehtMitTitelDatumUndFotos() {
        val r = placement.place(wf, PhotoTarget.New("Süße Mütze", "Ermeline"), listOf(PhotoWithAlt(photo("a b.jpg"), "Mütze")))
        assertEquals("suesse-muetze", r.dir)
        val doc = PostDocument.parse(blog.store.readText(wf.refs.draft, "content/posts/suesse-muetze/index.md")!!)
        val s = de.flauschiversum.publisher.posts.PostSummary.from(r.dir, doc.text)
        assertEquals("Süße Mütze", s.title); assertEquals("Ermeline", s.author); assertNull(s.problem); assertTrue(s.date != null)
        assertTrue(doc.body.contains("![Mütze](a b.jpg)"))
        val d = wf.overview().posts.single()
        assertEquals(DraftKind.NEW, d.kind)
    }

    @Test fun entwurfWirdAlsEinCommitHochgeladen() {
        placement.place(wf, PhotoTarget.Existing("baumaerkte"), listOf(PhotoWithAlt(photo("IMG_1.jpg"), "x")))
        wf.pushDraft()
        assertEquals(wf.draftId(), blog.store.refId(wf.refs.draftRemote))
    }

    @Test fun unbekannterBeitragWirdAbgelehnt() {
        val e = runCatching { placement.place(wf, PhotoTarget.Existing("gibtsnicht"), listOf(PhotoWithAlt(photo("a.jpg"), "x"))) }
        assertTrue(e.isFailure)
        assertNull(wf.draftId())
    }
}
