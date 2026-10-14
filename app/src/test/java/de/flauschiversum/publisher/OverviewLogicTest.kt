package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.overview.IndexedPost
import de.flauschiversum.publisher.overview.PostIndex
import de.flauschiversum.publisher.overview.PostQuery
import de.flauschiversum.publisher.overview.PostSearch
import de.flauschiversum.publisher.overview.SortOrder
import de.flauschiversum.publisher.posts.PostSummary
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostIndexTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }
    private val wf get() = blog.workflow

    @Test fun indexEnthaeltAlleBeitraegeMitDatumMitUndOhneUhrzeit() {
        val index = PostIndex(blog.store, File(tmp.root, "idx.tsv")).build(wf.refs.target)
        assertEquals(Fixtures.posts().toSet(), index.map { it.dir }.toSet())
        assertTrue(index.any { it.summary.date?.time != null }); assertTrue(index.any { it.summary.date?.time == null })
        val p = index.first { it.dir == "baumaerkte" }
        assertEquals("Baumärkte", p.summary.title); assertTrue(p.body.isNotEmpty()); assertTrue("baumärkte" in p.meta)
    }

    @Test fun kaputtesFrontmatterWirdTrotzdemAufgenommen() {
        wf.commit(listOf(TreeChange.Put("content/posts/kaputt/index.md", "Kein Frontmatter hier\n".toByteArray()),
            TreeChange.Put("content/posts/kaputt2/index.md", "---\ntitle: [kaputt\n---\nText\n".toByteArray())), "x")
        val index = PostIndex(blog.store).build(wf.refs.draft)
        val a = index.first { it.dir == "kaputt" }; val b = index.first { it.dir == "kaputt2" }
        assertEquals("kaputt", a.summary.title); assertTrue(a.summary.problem != null)
        assertTrue(b.summary.problem != null)
    }

    @Test fun zweiterStartLiestNichtsNeuUndGeaendertesWirdAktualisiert() {
        val cache = File(tmp.root, "idx.tsv")
        val first = PostIndex(blog.store, cache); first.build(wf.refs.target)
        assertEquals(Fixtures.posts().size, first.lastReadCount)
        val second = PostIndex(blog.store, cache)
        val again = second.build(wf.refs.target)
        assertEquals(0, second.lastReadCount); assertEquals(Fixtures.posts().size, again.size)
        assertEquals("Baumärkte", again.first { it.dir == "baumaerkte" }.summary.title)
        // ein Beitrag ändert sich auf dem Server
        val text = Fixtures.post("baumaerkte").replace("Baumärkte", "Baumarkt neu")
        TestRepos.commit(blog.bare, "main", mapOf("content/posts/baumaerkte/index.md" to text), "geändert")
        blog.ops.fetch(listOf("main"))
        val third = PostIndex(blog.store, cache)
        val updated = third.build(wf.refs.target)
        assertEquals(1, third.lastReadCount)
        assertEquals("Baumarkt neu", updated.first { it.dir == "baumaerkte" }.summary.title)
    }

    @Test fun titelbildErmittlung() {
        assertEquals("b.jpg", PostIndex.cover("b.jpg", listOf("a.jpg", "b.jpg", "index.md")))
        assertEquals("a.jpg", PostIndex.cover("fehlt.jpg", listOf("b.png", "a.jpg", "index.md")))
        assertEquals("a.jpg", PostIndex.cover(null, listOf("b.png", "a.jpg")))
        assertNull(PostIndex.cover(null, listOf("index.md", "notiz.txt")))
    }

    @Test fun entwurfErscheintImIndexOhneArbeitsbaum() {
        wf.commit(listOf(TreeChange.Put("content/posts/muetze/index.md", "---\ntitle: \"Mütze\"\ndate: 2026-10-13\n---\nText\n".toByteArray())), "x")
        val draft = PostIndex(blog.store).build(wf.refs.draft)
        assertTrue(draft.any { it.dir == "muetze" })
        assertTrue(PostIndex(blog.store).build(wf.refs.target).none { it.dir == "muetze" })
        assertEquals(listOf(DraftKind.NEW), wf.overview().posts.map { it.kind })
        assertTrue(!File(blog.store.dir, "content").exists()) // bare: kein Arbeitsbaum
    }
}

class PostSearchTest {
    private fun post(dir: String, title: String, date: String, tags: List<String> = emptyList(), body: String = "", excerpt: String? = null): IndexedPost {
        val fm = "---\ntitle: \"$title\"\ndate: $date\n" + (if (tags.isNotEmpty()) "tags: [${tags.joinToString()}]\n" else "") + (excerpt?.let { "excerpt: \"$it\"\n" } ?: "") + "---\n$body\n"
        val s = PostSummary.from(dir, fm)
        return IndexedPost(s, listOfNotNull(s.title, s.excerpt, s.tags.joinToString(" ")).joinToString(" ").lowercase(), body.lowercase(), null, dir)
    }
    private val posts = listOf(
        post("a", "Ärmel nähen", "2020-03-01", listOf("Nähen"), "Anleitung für Ärmel"),
        post("b", "Berta", "2021-05-05", listOf("Filzen", "Wolle"), "Eine Mütze aus Filz"),
        post("c", "Wollmütze", "2021-06-01", listOf("Wolle"), "Warm und weich", "Eine warme Mütze"),
        post("d", "Zebra", "2019-01-01"),
    )
    private fun dirs(q: PostQuery) = PostSearch.apply(posts, q).map { it.dir }

    @Test fun suchePerTitelTextUndKleinschreibung() {
        assertEquals(listOf("c"), dirs(PostQuery(text = "WOLLMÜTZE")))
        assertEquals(listOf("c", "b"), dirs(PostQuery(text = "mütze")))
        assertEquals(listOf("a"), dirs(PostQuery(text = "anleitung")))
    }
    @Test fun mehrereWoerterSindUnd() {
        assertEquals(listOf("c"), dirs(PostQuery(text = "mütze wollmütze")))
        assertEquals(emptyList<String>(), dirs(PostQuery(text = "mütze zebra")))
    }
    @Test fun keineTreffer() { assertEquals(emptyList<String>(), dirs(PostQuery(text = "xyzxyz"))) }
    @Test fun schlagwortUndJahrKombinierbarUndEinzelnEntfernbar() {
        val q = PostQuery(tags = setOf("Wolle"), year = 2021)
        assertEquals(listOf("c", "b"), dirs(q))
        assertEquals(listOf("b"), dirs(q.copy(tags = setOf("Wolle", "Filzen"))))
        assertEquals(listOf("c", "b"), dirs(q.copy(year = null)))
        assertEquals(listOf("c", "b"), dirs(q.copy(tags = setOf("wolle"), year = null)))
        assertEquals(listOf("b"), dirs(q.copy(text = "filz")))
        assertEquals(4, dirs(PostQuery()).size)
    }
    @Test fun sortierung() {
        assertEquals(listOf("c", "b", "a", "d"), dirs(PostQuery()))
        assertEquals(listOf("d", "a", "b", "c"), dirs(PostQuery(sort = SortOrder.OLDEST)))
        assertEquals(listOf("a", "b", "c", "d"), dirs(PostQuery(sort = SortOrder.TITLE))) // „Ärmel“ vor „Berta“
    }
    @Test fun jahreUndSchlagworte() {
        assertEquals(listOf(2021, 2020, 2019), PostSearch.years(posts))
        assertEquals(listOf("Filzen", "Nähen", "Wolle"), PostSearch.tags(posts))
    }
    @Test fun sucheUeberTausendBeitraegeIstSchnell() {
        val many = (1..1000).map { post("p$it", "Beitrag $it", "20${10 + it % 10}-01-01", listOf("Wolle"), "Dies ist ein längerer Text über Wolle, Mützen und Filz. ".repeat(60) + " eindeutig$it") }
        val t0 = System.nanoTime()
        val r = PostSearch.apply(many, PostQuery(text = "eindeutig777 filz"))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(listOf("p777"), r.map { it.dir }); assertTrue("dauerte $ms ms", ms < 1000)
    }
}
