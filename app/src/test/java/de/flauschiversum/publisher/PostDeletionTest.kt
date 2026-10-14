package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.PublishResult
import de.flauschiversum.publisher.posts.PostDeletion
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.RedirectsFile
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostDeletionTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }
    private val wf get() = blog.workflow
    private fun postsOf(rev: String) = wf.store.list(rev, "content/posts").map { it.name }

    // ---- alte Adresse
    @Test fun alteAdresseAusPermalinkOderAbgeleitet() {
        val withPermalink = PostDocument.parse("---\ntitle: \"A\"\npermalink: /2014/01/alraune/\ndate: 2014-01-02\n---\nx\n")
        assertEquals("/2014/01/alraune/", PostDeletion.oldPath(withPermalink, "irgendwas"))
        val without = PostDocument.parse("---\ntitle: \"Grüne Mütze\"\ndate: 2026-11-01\n---\nx\n")
        assertEquals("/2026/11/gruene-muetze/", PostDeletion.oldPath(without, "grüne-mütze"))
    }

    // ---- redirects.yaml
    @Test fun weiterleitungWirdAmEndeAngehaengtRestBleibt() {
        val real = Fixtures.redirectsYaml()
        val out = RedirectsFile.append(real, "/2014/01/alraune/", "/beitraege/")
        assertEquals(real + "/2014/01/alraune/: /beitraege/\n", out)
        assertEquals(real, RedirectsFile.append(real, "/kategorie/wolle/", "/x/")) // vorhandener Eintrag wird nicht verdoppelt
        assertEquals("a\n/x/: /y/\n", RedirectsFile.append("a", "/x/", "/y/").replace("a\n", "a\n"))
        assertEquals(real, RedirectsFile.remove(out, "/2014/01/alraune/"))
    }

    // ---- Löschen
    @Test fun veroeffentlichtenBeitragLoeschenMitStandardWeiterleitung() {
        val main = wf.targetId()
        val info = PostDeletion.info(wf, "baumaerkte")!!
        assertTrue(info.published); assertEquals("Baumärkte", info.title); assertEquals("/2013/07/baumarkte/", info.oldPath)
        PostDeletion.delete(wf, "baumaerkte", PostDeletion.DEFAULT_TARGET)
        assertEquals(main, wf.targetId())
        assertFalse("baumaerkte" in postsOf(wf.refs.draft)); assertTrue("baumaerkte" in postsOf(wf.refs.target))
        assertEquals(listOf("baumaerkte" to DraftKind.DELETED), wf.overview().posts.map { it.dir to it.kind })
        val redirects = wf.store.readText(wf.refs.draft, "content/redirects.yaml")!!
        assertEquals(Fixtures.redirectsYaml() + "/2013/07/baumarkte/: /beitraege/\n", redirects)
        assertEquals("Beitrag gelöscht: Baumärkte", wf.parseCommit(wf.draftId()!!).shortMessage)
    }

    @Test fun keineWeiterleitungLaesstRedirectsUnveraendert() {
        PostDeletion.delete(wf, "baumaerkte", null)
        assertEquals(listOf("content/posts/baumaerkte/index.md"), wf.ownChanges().map { it.path })
    }

    @Test fun weiterleitungAufAnderenBeitrag() {
        val other = PostDeletion.pathOf(wf, "flauschangriff")!!
        PostDeletion.delete(wf, "baumaerkte", other)
        assertTrue(wf.store.readText(wf.refs.draft, "content/redirects.yaml")!!.trimEnd().endsWith("/2013/07/baumarkte/: $other"))
    }

    @Test fun nurAlsEntwurfVorhandenEntsprichtVerwerfen() {
        wf.commit(listOf(de.flauschiversum.publisher.git.TreeChange.Put("content/posts/muetze/index.md", "---\ntitle: \"Mütze\"\ndate: 2026-10-13\n---\nT\n".toByteArray())), "x")
        assertFalse(PostDeletion.info(wf, "muetze")!!.published)
        PostDeletion.delete(wf, "muetze", PostDeletion.DEFAULT_TARGET)
        assertTrue(wf.overview().isEmpty)
        assertNull(wf.store.readText(wf.refs.draft, "content/redirects.yaml")?.takeIf { it != Fixtures.redirectsYaml() })
    }

    @Test fun wiederherstellenStelltVerzeichnisUndRedirectsWieAufMainHer() {
        PostDeletion.delete(wf, "baumaerkte", PostDeletion.DEFAULT_TARGET)
        PostDeletion.discard(wf, "baumaerkte")
        assertTrue(wf.overview().isEmpty)
        assertEquals(wf.store.readText(wf.refs.target, "content/posts/baumaerkte/index.md"), wf.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md"))
        assertEquals(Fixtures.redirectsYaml(), wf.store.readText(wf.refs.draft, "content/redirects.yaml"))
    }

    @Test fun veroeffentlichenEntferntDenBeitragVonMain() {
        PostDeletion.delete(wf, "baumaerkte", PostDeletion.DEFAULT_TARGET)
        wf.pushDraft()
        assertTrue(wf.publish() is PublishResult.Published)
        val server = de.flauschiversum.publisher.git.GitStore(blog.bare)
        assertNull(server.readText("refs/heads/main", "content/posts/baumaerkte/index.md"))
        assertTrue(server.readText("refs/heads/main", "content/redirects.yaml")!!.contains("/2013/07/baumarkte/: /beitraege/"))
    }

    // ---- Verweise
    @Test fun verweisendeBeitraegeWerdenGenannt() {
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/mutzekatze/index.md" to "---\ntitle: \"Mützekatze\"\npermalink: /2014/05/mutzekatze/\ndate: 2014-05-01\n---\nText\n"), "neu")
        blog.ops.fetch(listOf("main"))
        val info = PostDeletion.info(wf, "mutzekatze")!!
        assertEquals("/2014/05/mutzekatze/", info.oldPath)
        assertEquals(listOf(Fixtures.post("winter-is-coming").lineSequence().first { it.startsWith("title:") }.removePrefix("title:").trim().trim('"')), info.referrers)
        assertEquals(emptyList<String>(), PostDeletion.info(wf, "baumaerkte")!!.referrers)
    }
}
