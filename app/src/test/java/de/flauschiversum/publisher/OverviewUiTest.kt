package de.flauschiversum.publisher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.overview.PostQuery
import de.flauschiversum.publisher.overview.SortOrder
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }
    private fun waitFor(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            ShadowLooper.idleMainLooper(); compose.waitForIdle(); Thread.sleep(20)
        }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun unmerged(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private class Calls { val opened = mutableListOf<String?>(); val props = mutableListOf<String>(); val photos = mutableListOf<String>() }

    private fun home(setup: Boolean = true, calls: Calls = Calls()): HomeViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { if (setup) it.completeSetup(); h = it }
        val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = { calls.opened += it }, onSettings = {}, onFixAccess = {}, onOpenProperties = { calls.props += it }, onAddPhoto = { calls.photos += it }) } }
        return vm
    }
    private fun scrollTo(tag: String) { compose.onNodeWithTag("list").performScrollToNode(hasTestTag(tag)) }

    @Test fun eintragZeigtTitelDatumUndSchlagworteMitPlatzhalter() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        scrollTo("post-flauschangriff")
        compose.onNodeWithTag("post-flauschangriff").assertIsDisplayed()
        compose.onNodeWithText("Flauschangriff").assertIsDisplayed()
        compose.onNodeWithTag("thumb-flauschangriff", useUnmergedTree = true).assertIsDisplayed() // Platzhalter mit Anfangsbuchstabe
    }

    private fun photo(color: Int): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888); bmp.eraseColor(color)
        return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    @Test fun beitragsbildStattPlatzhalterNurOhneBildDasSymbol() {
        val files = mapOf(
            "content/posts/mit/index.md" to "---\ntitle: \"Mit Bild\"\ndate: 2026-10-13\nimage: f.jpg\n---\nT\n",
            "content/posts/erstes/index.md" to "---\ntitle: \"Erstes Bild\"\ndate: 2026-10-12\n---\nT\n",
            "content/posts/ohne/index.md" to "---\ntitle: \"Ohne Bild\"\ndate: 2026-10-11\n---\nT\n",
            "content/tags.yaml" to "- name: Wolle\n", "content/redirects.yaml" to "",
        )
        val bin = mapOf("content/posts/mit/f.jpg" to photo(0xFFE0794F.toInt()), "content/posts/erstes/B.JPG" to photo(0xFF3CC8DC.toInt()), "content/posts/erstes/a.jpg" to photo(0xFF6D4FE0.toInt()))
        val harness = AppTestHarness(tmp.newFolder(), files, bin).also { it.completeSetup(); h = it }
        val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { vm.ui.value.posts.size == 3 }
        assertEquals(mapOf("mit" to "f.jpg", "erstes" to "a.jpg", "ohne" to null), vm.ui.value.posts.associate { it.summary.dir to it.cover })
        // Bilder erscheinen: Platzhalter bleibt nur beim Beitrag ohne Bild
        waitFor { !unmerged("thumb-placeholder-mit") && !unmerged("thumb-placeholder-erstes") }
        assertTrue(unmerged("thumb-placeholder-ohne"))
        // auch Suchtreffer und Entwürfe
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("search")); compose.onNodeWithTag("search").performTextInput("Mit")
        waitFor { vm.ui.value.posts.map { it.summary.dir } == listOf("mit") }
        assertFalse(unmerged("thumb-placeholder-mit"))
    }

    @Test fun ohneKlonKommtEinHinweis() {
        home(setup = false)
        waitFor { exists("no-clone") }
        compose.onNodeWithTag("no-clone").assertIsDisplayed()
    }

    @Test fun beitragMitEntwurfErscheintNurImEntwurfsAbschnitt() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        val wf = h!!.container.session.workflow()!!
        val doc = de.flauschiversum.publisher.testsupport.Fixtures.post("baumaerkte").replace("Baumärkte", "Baumärkte geändert")
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", doc.toByteArray())), "x")
        h!!.container.draftChanges.value++
        waitFor { vm.ui.value.drafts.any { it.summary.dir == "baumaerkte" && it.draft == DraftKind.CHANGED } }
        assertEquals(1, (vm.ui.value.drafts + vm.ui.value.posts).count { it.summary.dir == "baumaerkte" })
        assertEquals("Baumärkte geändert", vm.ui.value.drafts.first { it.summary.dir == "baumaerkte" }.summary.title)
    }

    @Test fun neuerBeitragNurAlsEntwurf() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        h!!.container.session.workflow()!!.commit(listOf(TreeChange.Put("content/posts/muetze/index.md", "---\ntitle: \"Mütze\"\ndate: 2026-10-13\n---\nT\n".toByteArray())), "x")
        h!!.container.draftChanges.value++
        waitFor { vm.ui.value.drafts.any { it.summary.dir == "muetze" && it.draft == DraftKind.NEW } }
        assertTrue(vm.ui.value.posts.none { it.summary.dir == "muetze" })
        waitFor { exists("post-muetze") }
    }

    @Test fun keineEntwuerfeKeinAbschnitt() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        assertFalse(exists("drafts-header"))
    }

    @Test fun suchePerTitelUndText() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        val all = vm.ui.value.posts.size
        scrollTo("search")
        compose.onNodeWithTag("search").performTextInput("flauschangriff")
        waitFor { vm.ui.value.posts.size == 1 && vm.ui.value.posts.single().summary.dir == "flauschangriff" }
        compose.onNodeWithTag("result-count").assertIsDisplayed()
        compose.onNodeWithTag("search").performTextClearance()
        waitFor { vm.ui.value.posts.size == all }
        compose.onNodeWithTag("search").performTextInput("Wollfass") // Wort aus dem Text von „winter-is-coming“
        waitFor { vm.ui.value.posts.map { it.summary.dir } == listOf("winter-is-coming") }
    }

    @Test fun keineTrefferMitZuruecksetzen() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        scrollTo("search")
        compose.onNodeWithTag("search").performTextInput("xyzxyzxyz")
        waitFor { exists("no-results") }
        compose.onNodeWithTag("reset-filters").performClick()
        waitFor { vm.ui.value.posts.isNotEmpty() && vm.ui.value.query == PostQuery() }
    }

    @Test fun schlagwortUndJahrFilterEinzelnEntfernbar() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        val p = vm.ui.value.posts.first { it.summary.tags.isNotEmpty() && it.summary.date != null }
        val tag = p.summary.tags.first(); val year = p.summary.date!!.date.year
        scrollTo("filter-tags"); compose.onNodeWithTag("filter-tags").performClick()
        waitFor { exists("tagfilter-$tag") }
        compose.onNodeWithTag("tagfilter-$tag").performClick()
        compose.onNodeWithTag("tagfilter-done").performClick()
        waitFor { vm.ui.value.query.tags == setOf(tag) }
        compose.onNodeWithTag("filter-year").performClick()
        waitFor { exists("year-$year") }
        compose.onNodeWithTag("year-$year").performClick()
        waitFor { vm.ui.value.query.year == year }
        assertTrue(vm.ui.value.posts.all { r -> tag in r.summary.tags && r.summary.date!!.date.year == year })
        compose.onNodeWithTag("active-year").performClick()
        waitFor { vm.ui.value.query.year == null && vm.ui.value.query.tags == setOf(tag) }
        compose.onNodeWithTag("active-tag-$tag").performClick()
        waitFor { vm.ui.value.query == PostQuery() }
    }

    @Test fun sortierungNachTitel() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        scrollTo("sort"); compose.onNodeWithTag("sort").performClick()
        compose.onNodeWithTag("sort-TITLE").performClick()
        waitFor { vm.ui.value.query.sort == SortOrder.TITLE }
        val titles = vm.ui.value.posts.map { it.summary.title }
        val coll = java.text.Collator.getInstance(java.util.Locale.GERMAN).apply { strength = java.text.Collator.SECONDARY }
        assertEquals(titles.sortedWith { a, b -> coll.compare(a, b) }, titles)
    }

    @Test fun menueAktionenRufenDenRichtigenAblaufAuf() {
        val calls = Calls(); val vm = home(calls = calls); waitFor { vm.ui.value.posts.isNotEmpty() }
        scrollTo("search"); compose.onNodeWithTag("search").performTextInput("Flauschangriff")
        waitFor { exists("menu-flauschangriff") }
        fun action(a: String) { compose.onNodeWithTag("menu-flauschangriff").performClick(); compose.onNodeWithTag("action-$a-flauschangriff").performClick() }
        action("edit"); assertEquals(listOf<String?>("flauschangriff"), calls.opened)
        action("props"); assertEquals(listOf("flauschangriff"), calls.props)
        action("photo"); assertEquals(listOf("flauschangriff"), calls.photos)
        compose.onNodeWithTag("post-flauschangriff").performClick(); assertEquals(2, calls.opened.size)
    }

    @Test fun neuerBeitragSchaltflaeche() {
        val calls = Calls(); home(calls = calls)
        compose.onNodeWithTag("new-post").performClick()
        assertEquals(listOf<String?>(null), calls.opened)
    }

    @Test fun keinNetzMeldung() {
        val vm = home(); waitFor { vm.ui.value.posts.isNotEmpty() }
        waitFor { !h!!.container.sync.state.value.running && h!!.container.sync.state.value.generation >= 1 }
        h!!.blog.server.close()
        h!!.container.network.delegate = object : de.flauschiversum.publisher.git.NetworkInfo { override fun isOnline() = false; override fun isMetered() = false }
        // eine Anfrage, die mitten in einen laufenden Lauf fällt, wird zusammengefasst: so lange wiederholen, bis ein Lauf ohne Netz scheitert
        waitFor(60_000) { if (!h!!.container.sync.state.value.running) h!!.container.requestSync(SyncReason.MANUAL); exists("offline") }
        assertTrue(vm.ui.value.posts.isNotEmpty())
    }

    @Test fun abfrageUeberstehtNeuaufbauDerOberflaeche() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        var current = HomeViewModel(harness.container)
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { FlauschiversumPublisherTheme { HomeScreen(current, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { current.ui.value.posts.isNotEmpty() }
        val q = PostQuery(text = "wolle", tags = setOf("Wolle"), year = 2016, sort = SortOrder.TITLE)
        current.setQuery(q)
        waitFor { current.ui.value.query == q }
        compose.waitForIdle()
        current = HomeViewModel(harness.container) // neuer Prozess: neues ViewModel ohne Abfrage
        assertEquals(PostQuery(), current.ui.value.query)
        restoration.emulateSavedInstanceStateRestore()
        waitFor { current.ui.value.query == q }
    }
}
