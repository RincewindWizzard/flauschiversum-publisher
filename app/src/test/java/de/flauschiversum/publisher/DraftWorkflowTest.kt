package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.DraftPost
import de.flauschiversum.publisher.git.PublishResult
import de.flauschiversum.publisher.git.PushOutcome
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.git.UploadState
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftWorkflowTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    private val post = { title: String -> "---\ntitle: \"$title\"\ndate: 2026-10-13\n---\nText $title\n".toByteArray() }

    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }

    private val wf get() = blog.workflow
    private fun newPost(dir: String, title: String = dir) =
        wf.commit(listOf(TreeChange.Put("content/posts/$dir/index.md", post(title))), "Beitrag: $title")

    // ---- 3.4 Beitragsliste
    @Test fun beitragslisteAusDemFlachenKlon() {
        val list = PostReader(blog.store).summaries(wf.refs.target)
        assertEquals(Fixtures.posts().size, list.size)
        // neueste zuerst: Daten mit und ohne Uhrzeit werden gelesen
        assertTrue(list.all { it.date != null && it.problem == null })
        assertEquals(list.sortedByDescending { it.date!!.dateTime }.map { it.dir }, list.map { it.dir })
        val flausch = list.first { it.dir == "flauschangriff" }
        assertEquals("Flauschangriff", flausch.title); assertEquals(listOf("Hüte", "Filzen", "Wolle"), flausch.tags)
        assertEquals("21:05:07", flausch.date!!.time.toString())
    }

    @Test fun schlagworteAusDemKlon() {
        val names = de.flauschiversum.publisher.posts.TagsFile.names(blog.store.readText(wf.refs.target, "content/tags.yaml")!!)
        assertEquals(41, names.size)
    }

    // ---- 5.1 Branch draft
    @Test fun ersterEntwurfLegtDraftAnUndLaesstMainUnveraendert() {
        val main = wf.targetId()
        assertFalse(wf.hasDraft())
        assertNotNull(newPost("muetze", "Mütze"))
        assertTrue(wf.hasDraft())
        assertEquals(main, wf.targetId())
        assertEquals(main, wf.parseCommit(wf.draftId()!!).getParent(0).id)
        assertNull(blog.store.readText(wf.refs.target, "content/posts/muetze/index.md"))
        assertNotNull(blog.store.readText(wf.refs.draft, "content/posts/muetze/index.md"))
    }

    @Test fun zweiterBeitragIstWeitererCommitAufDraft() {
        newPost("muetze"); val first = wf.draftId()!!
        newPost("schal")
        assertEquals(first, wf.parseCommit(wf.draftId()!!).getParent(0).id)
        assertEquals(listOf("muetze", "schal"), wf.overview().posts.map { it.dir })
    }

    @Test fun unveraenderteDateiErzeugtKeinenCommit() {
        val same = Fixtures.post("baumaerkte")
        assertNull(wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", same.toByteArray())), "x"))
        assertFalse(wf.hasDraft())
    }

    // ---- 5.3 Entwürfe
    @Test fun entwuerfeNeuGeaendertGeloescht() {
        newPost("muetze")
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", "---\ntitle: \"Geändert\"\n---\n".toByteArray())), "ändern")
        wf.commit(listOf(TreeChange.DeleteDir("content/posts/acab-silberring")), "löschen")
        wf.commit(listOf(TreeChange.Put("content/tags.yaml", (Fixtures.tagsYaml() + "- name: Makramee\n").toByteArray())), "Schlagwort")
        val o = wf.overview()
        assertEquals(
            listOf(DraftPost("acab-silberring", DraftKind.DELETED), DraftPost("baumaerkte", DraftKind.CHANGED), DraftPost("muetze", DraftKind.NEW)),
            o.posts,
        )
        assertEquals(listOf("content/tags.yaml"), o.otherChanges.map { it.path })
        assertEquals(wf.refs.draft, wf.revFor("muetze", o)); assertEquals(wf.refs.target, wf.revFor("flauschangriff", o))
    }

    @Test fun keinEntwurfWennDraftUndMainGleichSind() {
        assertTrue(wf.overview().isEmpty)
        assertEquals(UploadState.NONE, wf.upload())
    }

    @Test fun einzelnenEntwurfVerwerfen() {
        newPost("muetze")
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", "geändert".toByteArray())), "ändern")
        wf.discard("baumaerkte")
        assertEquals(listOf(DraftPost("muetze", DraftKind.NEW)), wf.overview().posts)
        assertEquals(Fixtures.post("baumaerkte"), blog.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md"))
        // neuer Beitrag verschwindet beim Verwerfen
        wf.discard("muetze")
        assertTrue(wf.overview().isEmpty)
        assertNull(blog.store.readText(wf.refs.draft, "content/posts/muetze/index.md"))
    }

    @Test fun geloeschtenBeitragWiederherstellenAuchMitMedien() {
        // Bild im Zielbranch hinzufügen
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/baumaerkte/bild.jpg" to "JPEGDATA"), "Bild")
        blog.clone("neu.git")
        wf.commit(listOf(TreeChange.DeleteDir("content/posts/baumaerkte")), "löschen")
        assertEquals(listOf(DraftPost("baumaerkte", DraftKind.DELETED)), wf.overview().posts)
        wf.discard("baumaerkte")
        assertTrue(wf.overview().isEmpty)
        assertEquals("JPEGDATA", blog.store.readText(wf.refs.draft, "content/posts/baumaerkte/bild.jpg"))
    }

    @Test fun alleVerwerfenSetztDraftAufMainUndErzwingtPush() {
        newPost("muetze"); assertEquals(PushOutcome.Ok, wf.pushDraft())
        assertNotNull(blog.serverRef("refs/heads/draft"))
        wf.discardAllLocal()
        assertTrue(wf.overview().isEmpty)
        assertEquals(PushOutcome.Ok, wf.pushDraft(force = true))
        assertEquals(blog.serverRef("refs/heads/main"), blog.serverRef("refs/heads/draft"))
    }

    // ---- 5.2 Push-Status
    @Test fun uploadStatus() {
        newPost("muetze")
        assertEquals(UploadState.PENDING, wf.upload())
        assertEquals(PushOutcome.Ok, wf.pushDraft())
        assertEquals(UploadState.UPLOADED, wf.upload())
    }

    @Test fun pushFehlerLaesstCommitLokalBestehen() {
        newPost("muetze"); val local = wf.draftId()
        blog.server.rejectReceivePack = true
        assertTrue(runCatching { wf.pushDraft() }.let { it.isFailure || it.getOrNull() is PushOutcome.Rejected })
        assertEquals(local, wf.draftId()); assertEquals(UploadState.PENDING, wf.upload())
        blog.server.rejectReceivePack = false
        assertEquals(PushOutcome.Ok, wf.pushDraft()); assertEquals(UploadState.UPLOADED, wf.upload())
    }

    // ---- 5.4 Veröffentlichen
    @Test fun veroeffentlichenMehrererBeitraegeAlsFastForward() {
        newPost("muetze"); newPost("schal")
        val r = wf.publish()
        assertTrue("war: $r", r is PublishResult.Published)
        assertEquals(setOf("muetze", "schal"), (r as PublishResult.Published).posts.map { it.dir }.toSet())
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/muetze/index.md"))
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/schal/index.md"))
        assertEquals(blog.serverRef("refs/heads/main"), blog.serverRef("refs/heads/draft"))
        assertTrue(wf.overview().isEmpty)
        assertEquals(wf.targetId(), wf.draftId())
    }

    @Test fun veroeffentlichenWennMainWeitergelaufenIstFuehrtZusammen() {
        newPost("muetze")
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/anderer/index.md" to "---\ntitle: Anderer\n---\n"), "anderer")
        val r = wf.publish()
        assertTrue("war: $r", r is PublishResult.Published)
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/muetze/index.md"))
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/anderer/index.md"))
        val merge = wf.parseCommit(wf.targetId()!!)
        assertEquals(2, merge.parentCount)
        assertEquals(blog.serverRef("refs/heads/main"), blog.serverRef("refs/heads/draft"))
    }

    @Test fun konfliktBrichtAbUndLaesstAllesUnveraendert() {
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", "---\ntitle: \"Lokal\"\n---\n".toByteArray())), "lokal")
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/baumaerkte/index.md" to "---\ntitle: \"Server\"\n---\n"), "server")
        val mainOnServer = blog.serverRef("refs/heads/main"); val draftLocal = wf.draftId()
        val r = wf.publish()
        assertTrue("war: $r", r is PublishResult.Conflict)
        assertTrue((r as PublishResult.Conflict).files.contains("content/posts/baumaerkte/index.md"))
        assertEquals(mainOnServer, blog.serverRef("refs/heads/main"))
        assertEquals(draftLocal, wf.draftId())
    }

    @Test fun abgelehnterPushDesZielbranchsLaesstDraftBestehen() {
        newPost("muetze"); wf.pushDraft()
        val draft = wf.draftId()
        blog.server.rejectReceivePack = true
        val r = wf.publish()
        assertFalse("war: $r", r is PublishResult.Published)
        assertEquals(draft, wf.draftId())
        assertFalse(wf.overview().isEmpty)
    }

    @Test fun nichtsZuVeroeffentlichen() {
        assertEquals(PublishResult.NothingToPublish, wf.publish())
    }

    @Test fun veroeffentlichenLaedtHistorieBeiWeitVorausgelaufenemMainNach() {
        newPost("muetze"); wf.pushDraft()
        repeat(25) { TestRepos.commit(blog.bare, "main", mapOf("content/posts/x$it/index.md" to "---\ntitle: x$it\n---\n"), "x$it") }
        blog.clone("zweitgeraet.git") // neues Gerät: beide Branches mit Tiefe 1
        assertFalse(blog.ops.hasMergeBase(wf.draftId()!!, wf.targetId()!!))
        val r = wf.publish()
        assertTrue("war: $r", r is PublishResult.Published)
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/muetze/index.md"))
        assertNotNull(blog.serverFile("refs/heads/main", "content/posts/x24/index.md"))
    }

    // ---- 3.3 / 5.6 Synchronisation
    @Test fun syncSetztDraftOhneAenderungenAufMainVor() {
        newPost("muetze"); wf.pushDraft(); wf.publish() // draft == main
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/neu/index.md" to "---\ntitle: Neu\n---\n"), "neu")
        val r = wf.sync()
        assertTrue(r.targetChanged && r.draftAdvanced)
        assertEquals(wf.targetId(), wf.draftId())
        assertEquals(blog.serverRef("refs/heads/main"), blog.serverRef("refs/heads/draft"))
    }

    @Test fun syncLaesstDraftMitAenderungenUnveraendert() {
        newPost("muetze"); val d = wf.draftId()
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/neu/index.md" to "---\ntitle: Neu\n---\n"), "neu")
        val r = wf.sync()
        assertTrue(r.targetChanged); assertFalse(r.draftAdvanced)
        assertEquals(d, wf.draftId())
        assertEquals(listOf("muetze"), wf.overview().posts.map { it.dir })
        // Beiträge ohne Entwurf werden aus dem aktuellen Zielbranch gelesen
        assertEquals(wf.refs.target, wf.revFor("neu"))
        assertNotNull(blog.store.readText(wf.refs.target, "content/posts/neu/index.md"))
    }

    @Test fun syncUebernimmtDraftVonEinemAnderenGeraet() {
        // Ein zweites Gerät lädt einen Entwurf hoch
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/fremd/index.md" to "---\ntitle: Fremd\n---\n"), "fremd-main")
        val other = File(blog.bare.parentFile, "tmp-draft").also { it.deleteRecursively() }
        org.eclipse.jgit.api.Git.open(blog.bare).use { g ->
            val main = g.repository.resolve("refs/heads/main")
            val u = g.repository.updateRef("refs/heads/draft"); u.setNewObjectId(main); u.update()
        }
        val r = wf.sync()
        assertEquals(blog.serverRef("refs/heads/draft"), wf.draftId()?.name)
        assertTrue(r.draftAdvanced || r.targetChanged)
    }
}
