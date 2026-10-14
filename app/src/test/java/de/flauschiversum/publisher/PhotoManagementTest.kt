package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.posts.PhotoManagement
import de.flauschiversum.publisher.posts.PhotoMarkdown
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoMarkdownTest {
    private val body = "Text\n\n## Anhänge\n\n![a](1.jpg)\n![b](2.jpg)\n![c](3.jpg)\n"

    @Test fun altTextErgaenzen() {
        assertEquals("Hallo\n\n![Fertige Mütze](IMG_1.jpg)\n", PhotoMarkdown.setAlt("Hallo\n\n![](IMG_1.jpg)\n", "IMG_1.jpg", "Fertige Mütze"))
    }
    @Test fun dateinameMitLeerzeichenWirdGefunden() {
        val t = "x\n\n![Mein schwacher Moment](Schwacher Moment.jpg)\n\ny\n"
        assertEquals("x\n\n![Neu \\[1\\]](Schwacher Moment.jpg)\n\ny\n", PhotoMarkdown.setAlt(t, "Schwacher Moment.jpg", "Neu [1]"))
    }
    @Test fun altTextGiltInAllenZeilenRestBleibt() {
        val t = "![a](x.jpg)\nText ![nicht](anders.jpg) Mitte\n![a](x.jpg)\r\n"
        assertEquals("![neu](x.jpg)\nText ![nicht](anders.jpg) Mitte\n![neu](x.jpg)\r\n", PhotoMarkdown.setAlt(t, "x.jpg", "neu"))
    }
    @Test fun reihenfolgeBildNachVorn() {
        assertEquals("Text\n\n## Anhänge\n\n![c](3.jpg)\n![a](1.jpg)\n![b](2.jpg)\n", PhotoMarkdown.move(body, "3.jpg", 0))
        assertEquals("Text\n\n## Anhänge\n\n![b](2.jpg)\n![c](3.jpg)\n![a](1.jpg)\n", PhotoMarkdown.move(body, "1.jpg", 2))
        assertEquals(body, PhotoMarkdown.move(body, "1.jpg", 0))
    }
    @Test fun reihenfolgeLaesstBilderImFliesstextUndLetzteZeileOhneUmbruch() {
        val t = "![fliess](f.jpg)\n\n## Anhänge\n\n![a](1.jpg)\n![b](2.jpg)"
        assertEquals("![fliess](f.jpg)\n\n## Anhänge\n\n![b](2.jpg)\n![a](1.jpg)", PhotoMarkdown.move(t, "2.jpg", 0))
        assertEquals(t, PhotoMarkdown.move(t, "f.jpg", 0)) // außerhalb: unverändert
    }
    @Test fun entfernenEinzelnesBild() {
        assertEquals("Text\n\n## Anhänge\n\n![a](1.jpg)\n![c](3.jpg)\n", PhotoMarkdown.remove(body, "2.jpg"))
    }
    @Test fun letztesBildEntferntAuchDieUeberschrift() {
        assertEquals("Text\n", PhotoMarkdown.remove("Text\n\n## Anhänge\n\n![a](1.jpg)\n", "1.jpg"))
        assertEquals("", PhotoMarkdown.remove("## Anhänge\n\n![a](1.jpg)\n", "1.jpg"))
    }
    @Test fun verweiseImFliesstextWerdenGezaehlt() {
        val t = "Siehe ![x](f.jpg) mitten im Satz\n\n![fliess](f.jpg)\n\n## Anhänge\n\n![a](f.jpg)\n"
        assertEquals(1, PhotoMarkdown.outsideCount(t, "f.jpg"))
        assertEquals(1, PhotoMarkdown.inlineMentions(t, "f.jpg"))
        assertEquals(listOf("f.jpg"), PhotoMarkdown.attachmentFiles(t))
    }
    @Test fun einbindenAmEndeVonAnhaenge() {
        assertEquals("Text\n\n## Anhänge\n\n![a](1.jpg)\n![Detail](2.jpg)\n", PhotoMarkdown.include("Text\n\n## Anhänge\n\n![a](1.jpg)\n", "2.jpg", "Detail"))
        assertEquals("Text\n\n## Anhänge\n\n![Detail](2.jpg)\n", PhotoMarkdown.include("Text\n", "2.jpg", "Detail"))
    }
}

class PhotoManagementTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    private val index = "---\ntitle: \"Fotos\"\ndate: 2026-10-13\nimage: a.jpg\n---\nText\n\n## Anhänge\n\n![eins](a.jpg)\n![zwei](b.jpg)\n![drei](c.jpg)\n"
    @Before fun setUp() {
        blog = BlogFixture(tmp.newFolder("blog"), BlogFixture.defaultFiles() + mapOf(
            "content/posts/fotos/index.md" to index, "content/posts/fotos/a.jpg" to "A", "content/posts/fotos/b.jpg" to "B",
            "content/posts/fotos/c.jpg" to "C", "content/posts/fotos/d.jpg" to "D")); blog.clone()
    }
    @After fun tearDown() { blog.close() }
    private val wf get() = blog.workflow
    private fun body() = PostDocument.parse(wf.store.readText(wf.refs.draft, "content/posts/fotos/index.md")!!).body

    @Test fun rasterMitKennzeichnung() {
        val p = PhotoManagement.photos(wf, "fotos")
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg"), p.map { it.file })
        assertTrue(p[0].isCover); assertFalse(p[1].isCover)
        assertTrue(p[3].notInText); assertFalse(p[0].notInText)
        assertEquals("eins", p[0].alt)
    }
    @Test fun reihenfolgeAendernIstEinCommitNurIndexMd() {
        val main = wf.targetId()
        assertTrue(PhotoManagement.move(wf, "fotos", "c.jpg", 0))
        assertEquals(main, wf.targetId())
        assertEquals(listOf("content/posts/fotos/index.md"), wf.ownChanges().map { it.path })
        assertEquals("c.jpg", PhotoManagement.photos(wf, "fotos").first().file)
    }
    @Test fun altTextUndEinbinden() {
        PhotoManagement.setAlt(wf, "fotos", "b.jpg", "Neu")
        assertTrue(body().contains("![Neu](b.jpg)"))
        PhotoManagement.include(wf, "fotos", "d.jpg", "Detail")
        assertTrue(body().trimEnd().endsWith("![Detail](d.jpg)"))
        assertFalse(PhotoManagement.photos(wf, "fotos").first { it.file == "d.jpg" }.notInText)
    }
    @Test fun entfernenLoeschtZeileUndDateiInEinemCommit() {
        PhotoManagement.remove(wf, "fotos", "b.jpg")
        assertFalse(body().contains("b.jpg"))
        assertNull(wf.store.readBytes(wf.refs.draft, "content/posts/fotos/b.jpg"))
        assertEquals(1, wf.parseCommit(wf.draftId()!!).parentCount)
        assertEquals(wf.targetId(), wf.parseCommit(wf.draftId()!!).getParent(0).id)
        assertEquals(setOf("content/posts/fotos/index.md", "content/posts/fotos/b.jpg"), wf.ownChanges().map { it.path }.toSet())
    }
    @Test fun titelbildMussErstGeaendertWerden() {
        val e = runCatching { PhotoManagement.remove(wf, "fotos", "a.jpg") }
        assertTrue(e.isFailure); assertFalse(wf.hasDraft())
        PhotoManagement.setCover(wf, "fotos", null)
        PhotoManagement.remove(wf, "fotos", "a.jpg")
        assertFalse(PostDocument.parse(wf.store.readText(wf.refs.draft, "content/posts/fotos/index.md")!!).head.contains("image:"))
    }
    @Test fun wiederherstellenUeberVerwerfen() {
        PhotoManagement.remove(wf, "fotos", "b.jpg")
        wf.discard("fotos")
        assertEquals("B", String(wf.store.readBytes(wf.refs.draft, "content/posts/fotos/b.jpg")!!))
        assertEquals(index, wf.store.readText(wf.refs.draft, "content/posts/fotos/index.md"))
    }
    @Test fun veroeffentlichtenBeitragAendernMachtEntwurfGeaendert() {
        PhotoManagement.move(wf, "fotos", "c.jpg", 0)
        assertEquals(listOf(de.flauschiversum.publisher.git.DraftKind.CHANGED), wf.overview().posts.map { it.kind })
    }
}
