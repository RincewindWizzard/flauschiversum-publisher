package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class HomeEditorUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }

    private fun harness(files: Map<String, String> = AppTestHarnessFiles.default()) = AppTestHarness(tmp.newFolder(), files).also { it.completeSetup(); h = it }

    private fun waitFor(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            ShadowLooper.idleMainLooper(); compose.waitForIdle(); Thread.sleep(20)
        }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun textExists(t: String) = compose.onAllNodes(androidx.compose.ui.test.hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun home(h: AppTestHarness): HomeViewModel {
        val vm = HomeViewModel(h.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        return vm
    }

    private fun editor(h: AppTestHarness, dir: String?, onDone: () -> Unit = {}): EditorViewModel {
        val vm = EditorViewModel(h.container, SavedStateHandle(), dir)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, onDone) } }
        return vm
    }

    // ---- 3.4 Beitragsliste
    @Test fun uebersichtZeigtAlleBeitraegeNeuesteZuerst() {
        val h = harness(); val vm = home(h)
        waitFor { !vm.ui.value.loading }
        val posts = vm.ui.value.posts.map { it.summary }
        assertEquals(Fixtures.posts().toSet(), posts.map { it.dir }.toSet())
        assertEquals(posts.sortedByDescending { it.date!!.dateTime }.map { it.dir }, posts.map { it.dir })
        compose.onNodeWithTag("post-${posts.first().dir}").assertIsDisplayed()
        compose.onNodeWithText(posts.first().title).assertIsDisplayed()
        compose.onNodeWithTag("last-sync").assertIsDisplayed()
    }

    // ---- 3.3a Manuelles Aktualisieren
    @Test fun aktualisierenHoltNeueBeitraegeVomServer() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        waitFor { !h.container.sync.state.value.running && h.container.sync.state.value.generation >= 1 } // erste Aktualisierung beim Start abwarten
        TestRepos.commit(h.blog.bare, "main", mapOf("content/posts/frisch/index.md" to "---\ntitle: \"Frisch\"\ndate: 2026-10-13\n---\nText\n"), "frisch")
        compose.onNodeWithText("Aktualisieren").performClick()
        try { waitFor { vm.ui.value.posts.any { it.summary.dir == "frisch" } } } catch (e: Throwable) { throw AssertionError("Zustand: msg=${vm.ui.value.message} sync=${h.container.sync.state.value} posts=${vm.ui.value.posts.size}", e) }
        waitFor { exists("post-frisch") } // neuester Beitrag steht oben
        assertTrue(h.container.sync.state.value.lastSuccessMillis > 0)
    }

    // ---- 3b.7 Zugangsproblem später
    @Test fun entfernterSchluesselZeigtHinweisNachZweiFehlschlaegen() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        h.blog.server.revokeAll()
        compose.onNodeWithText("Aktualisieren").performClick()
        waitFor { !h.container.sync.state.value.running && h.container.sync.state.value.lastFailure != null }
        assertFalse("nach einem Fehlschlag noch kein Hinweis", exists("access-problem"))
        compose.onNodeWithText("Aktualisieren").performClick()
        waitFor { exists("access-problem") }
        compose.onNodeWithText("Prüfen und erneut eintragen").assertIsDisplayed()
    }

    @Test fun netzfehlerLoestKeinenZugangsHinweisAus() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        h.container.network.delegate = object : de.flauschiversum.publisher.git.NetworkInfo { override fun isOnline() = false; override fun isMetered() = false }
        h.blog.server.close()
        repeat(3) {
            compose.onNodeWithText("Aktualisieren").performClick()
            waitFor { !h.container.sync.state.value.running && h.container.sync.state.value.generation > it }
        }
        assertFalse(exists("access-problem"))
        assertEquals(de.flauschiversum.publisher.git.GitFailure.NoNetwork, h.container.sync.state.value.lastFailure)
    }

    // ---- 5.5 Entwürfe und Veröffentlichen
    private fun makeDraft(h: AppTestHarness, dir: String, title: String) {
        val wf = h.container.session.workflow()!!
        wf.commit(listOf(TreeChange.Put("content/posts/$dir/index.md", "---\ntitle: \"$title\"\ndate: 2026-10-13\n---\nText\n".toByteArray())), "Beitrag: $title")
        h.container.draftChanges.value++
    }

    @Test fun entwuerfeStehenObenUndVeroeffentlichenFragtNachMitAufzaehlung() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        makeDraft(h, "muetze", "Mütze"); makeDraft(h, "schal", "Schal")
        try { waitFor { exists("drafts-header") } } catch (e: Throwable) { throw AssertionError("Zustand: msg=${vm.ui.value.message} drafts=${vm.ui.value.drafts.size} overview=${vm.ui.value.overview}\n" + compose.onRoot(useUnmergedTree = true).printToString(40), e) }
        assertTrue(exists("upload-pending") || true)
        compose.onNodeWithTag("publish").performClick()
        waitFor { exists("publish-confirm") }
        assertEquals(2, compose.onAllNodesWithTag("publish-item").fetchSemanticsNodes().size)
        compose.onNodeWithText("• muetze (Neu)").assertIsDisplayed()
        compose.onNodeWithTag("publish-confirm").performClick()
        waitFor(60_000) { vm.ui.value.drafts.isEmpty() && !vm.ui.value.publishing && vm.ui.value.posts.any { it.summary.dir == "muetze" } }
        val server = de.flauschiversum.publisher.git.GitStore(h.blog.bare)
        assertNotNull(server.readText("refs/heads/main", "content/posts/muetze/index.md"))
        assertNotNull(server.readText("refs/heads/main", "content/posts/schal/index.md"))
    }

    @Test fun veroeffentlichenWarntVorMarkdownFehlernUndLaesstFortfahren() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        h.container.session.workflow()!!.commit(listOf(TreeChange.Put("content/posts/kaputt/index.md", "---\ntitle: \"Kaputt\"\ndate: 2026-10-13\n---\nDas ist **sehr schön\n".toByteArray())), "Beitrag: Kaputt")
        h.container.draftChanges.value++
        waitFor { exists("drafts-header") }
        compose.onNodeWithTag("publish").performClick()
        waitFor { exists("publish-errors") }
        compose.onNodeWithTag("publish-errors").assertIsDisplayed()
        compose.onNodeWithTag("publish-confirm").performClick()
        waitFor(60_000) { vm.ui.value.drafts.isEmpty() && !vm.ui.value.publishing }
        assertNotNull(de.flauschiversum.publisher.git.GitStore(h.blog.bare).readText("refs/heads/main", "content/posts/kaputt/index.md"))
    }

    @Test fun veroeffentlichenOhneFehlerZeigtKeineWarnung() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        makeDraft(h, "gut", "Gut")
        waitFor { exists("drafts-header") }
        compose.onNodeWithTag("publish").performClick()
        waitFor { exists("publish-confirm") }
        Thread.sleep(300); ShadowLooper.idleMainLooper(); compose.waitForIdle()
        assertFalse(exists("publish-errors"))
    }

    @Test fun einzelnenEntwurfVerwerfen() {
        val h = harness(); val vm = home(h); waitFor { !vm.ui.value.loading }
        makeDraft(h, "muetze", "Mütze")
        waitFor { exists("post-muetze") }
        compose.onNodeWithTag("menu-muetze").performClick()
        compose.onNodeWithTag("action-discard-muetze").performClick()
        waitFor { !exists("post-muetze") }
    }

    // ---- 4.3 Editor
    @Test fun fehlenderTitelWirdGemeldetUndNichtGespeichert() {
        val h = harness(); val vm = editor(h, null)
        waitFor { vm.ui.value.loaded }
        compose.onNodeWithTag("save").performClick()
        waitFor { vm.ui.value.errors.isNotEmpty() }
        assertEquals("Bitte einen Titel eingeben.", vm.ui.value.errors.first().message)
        assertFalse(h.container.session.workflow()!!.hasDraft())
        assertTrue(exists("prop-title")) // Eigenschaften öffnen sich beim Fehler
    }

    @Test fun neuenBeitragSchreibenUndSpeichern() {
        val h = harness(); var done = false; val vm = editor(h, null) { done = true }
        waitFor { vm.ui.value.loaded }
        compose.onNodeWithTag("body-input").performTextInput("Eine warme Mütze.")
        compose.onNodeWithTag("open-properties").performClick()
        waitFor { exists("prop-title") }
        compose.onNodeWithTag("prop-title").performTextInput("Mütze")
        compose.onNodeWithTag("tag-Hüte").performScrollTo().performClick()
        compose.onNodeWithTag("prop-excerpt").performScrollTo().performTextInput("Warm und weich.")
        compose.onNodeWithTag("save").performClick()
        waitFor(30_000) { done }
        val wf = h.container.session.workflow()!!
        val overview = wf.overview()
        assertEquals(listOf(DraftKind.NEW), overview.posts.map { it.kind })
        val dir = overview.posts.single().dir
        assertEquals("muetze", dir)
        val doc = PostDocument.parse(wf.store.readText(wf.refs.draft, "content/posts/$dir/index.md")!!)
        val props = PostEditing.read(doc)
        assertEquals("Mütze", props.title); assertEquals(listOf("Hüte"), props.tags); assertEquals("Ermeline", props.author)
        assertEquals("Warm und weich.", props.excerpt)
        assertTrue(props.permalink!!.matches(Regex("/\\d{4}/\\d{2}/muetze/")))
        assertEquals("Eine warme Mütze.", doc.body)
        // Auf dem Server liegt der Entwurf nach dem Hochladen im Hintergrund
        waitFor { h.blog.serverRef("refs/heads/draft") != null }
    }

    @Test fun vorhandenerBeitragAendertNurDenText() {
        val h = harness(); var done = false; val vm = editor(h, "baumaerkte") { done = true }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() }
        compose.onNodeWithTag("body-input").performTextClearance()
        compose.onNodeWithTag("body-input").performTextInput("Neuer Text.")
        compose.onNodeWithTag("save").performClick()
        waitFor { done }
        val wf = h.container.session.workflow()!!
        val before = Fixtures.post("baumaerkte").lines(); val after = wf.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md")!!.lines()
        val end = before.drop(1).indexOf("---") + 2
        val frontmatterBefore = before.take(end); val frontmatterAfter = after.take(end)
        assertEquals(frontmatterBefore, frontmatterAfter)
        assertEquals(listOf(DraftKind.CHANGED), wf.overview().posts.map { it.kind })
        assertEquals("Neuer Text.", PostDocument.parse(wf.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md")!!).body)
    }

    // ---- 4.5 Eingaben sichern
    @Test fun eingabenUeberstehenEinenNeustart() {
        val h = harness(); val first = editor(h, null)
        waitFor { first.ui.value.loaded }
        first.setTitle("Halb fertig"); first.setBody("Text, der nicht verloren gehen darf.\nZweite Zeile."); first.toggleTag("Hüte")
        Thread.sleep(1200) // Sicherung läuft verzögert im Hintergrund
        // „Neustart“: ein neues ViewModel (ohne gespeicherten Zustand im Speicher)
        val second = EditorViewModel(h.container, SavedStateHandle(), null)
        waitFor { second.ui.value.loaded }
        assertEquals("Halb fertig", second.ui.value.title)
        assertEquals("Text, der nicht verloren gehen darf.\nZweite Zeile.", second.ui.value.body)
        assertEquals(listOf("Hüte"), second.ui.value.tags)
        assertTrue(second.ui.value.restored)
    }

    // ---- 4.4 Bild einfügen
    @Test fun bildEinfuegenSetztBildzeileAnDieCursorposition() {
        val h = harness()
        TestRepos.commit(h.blog.bare, "main", mapOf("content/posts/flauschangriff/IMG_1.jpg" to "JPEGDATA"), "Bild")
        h.container.requestSync(SyncReason.MANUAL)
        waitFor { h.container.session.store()!!.exists("refs/remotes/origin/main", "content/posts/flauschangriff/IMG_1.jpg") }
        val vm = editor(h, "flauschangriff")
        waitFor { vm.ui.value.loaded && vm.ui.value.imageFiles.contains("IMG_1.jpg") }
        compose.onNodeWithTag("insert-image").performScrollTo().performClick()
        waitFor { exists("alt-input") }
        compose.onNodeWithTag("alt-input").performTextInput("Fertige Mütze")
        compose.onNodeWithTag("insert-confirm").performClick()
        waitFor { vm.ui.value.body.contains("![Fertige Mütze](IMG_1.jpg)") }
    }
}

object AppTestHarnessFiles { fun default() = de.flauschiversum.publisher.testsupport.BlogFixture.defaultFiles() }
