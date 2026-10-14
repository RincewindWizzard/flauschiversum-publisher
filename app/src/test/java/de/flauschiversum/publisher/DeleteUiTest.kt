package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DeleteUiTest {
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

    private fun home(): HomeViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { !vm.ui.value.loading && vm.ui.value.posts.isNotEmpty() }
        return vm
    }
    private fun wf() = h!!.container.session.workflow()!!
    private fun clickDelete(dir: String) {
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("delete-$dir"))
        compose.onNodeWithTag("delete-$dir").performClick()
        waitFor { exists("delete-dialog") }
    }

    @Test fun loeschenMitStandardWeiterleitungErzeugtEntwurfGeloescht() {
        val vm = home(); val main = wf().targetId()
        clickDelete("baumaerkte")
        compose.onNodeWithTag("delete-path").assertIsDisplayed()
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { vm.ui.value.drafts.any { it.summary.dir == "baumaerkte" && it.draft == DraftKind.DELETED } }
        assertEquals(main, wf().targetId())
        assertFalse(wf().store.exists(wf().refs.draft, "content/posts/baumaerkte/index.md"))
        assertTrue(wf().store.readText(wf().refs.draft, "content/redirects.yaml")!!.trimEnd().endsWith("/2013/07/baumarkte/: /beitraege/"))
    }

    @Test fun abbrechenAendertNichts() {
        home(); clickDelete("baumaerkte")
        compose.onNodeWithTag("delete-cancel").performClick()
        waitFor { !exists("delete-dialog") }
        assertNull(wf().draftId())
    }

    @Test fun keineWeiterleitungWaehlen() {
        val vm = home(); clickDelete("baumaerkte")
        compose.onNodeWithTag("redirect-none").performScrollTo().performClick()
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { vm.ui.value.drafts.any { it.draft == DraftKind.DELETED } }
        assertEquals(Fixtures.redirectsYaml(), wf().store.readText(wf().refs.draft, "content/redirects.yaml"))
    }

    @Test fun weiterleitungAufAnderenBeitrag() {
        val vm = home(); clickDelete("baumaerkte")
        compose.onNodeWithTag("redirect-post").performScrollTo().performClick()
        waitFor { exists("redirect-search") }
        compose.onNodeWithTag("redirect-search").performScrollTo().performTextInput("Flauschangriff")
        try { waitFor(8000) { exists("redirect-to-flauschangriff") } } catch (e: AssertionError) { throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(60), e) }
        compose.onNodeWithTag("redirect-to-flauschangriff").performScrollTo().performClick()
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { vm.ui.value.drafts.any { it.draft == DraftKind.DELETED } }
        val line = wf().store.readText(wf().refs.draft, "content/redirects.yaml")!!.trimEnd().lines().last()
        assertTrue(line, line.startsWith("/2013/07/baumarkte/: /20") && !line.endsWith("/beitraege/"))
    }

    @Test fun geloeschterBeitragLaesstSichVerwerfenUndIstDannWiederda() {
        val vm = home(); clickDelete("baumaerkte")
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { vm.ui.value.drafts.any { it.draft == DraftKind.DELETED } }
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("post-baumaerkte"))
        compose.onNodeWithText("Verwerfen").performClick()
        waitFor { vm.ui.value.drafts.isEmpty() }
        assertEquals(Fixtures.redirectsYaml(), wf().store.readText(wf().refs.draft, "content/redirects.yaml"))
        assertTrue(wf().store.exists(wf().refs.draft, "content/posts/baumaerkte/index.md"))
    }

    @Test fun nurEntwurfWirdVerworfenOhneWeiterleitungshinweis() {
        val vm = home()
        wf().commit(listOf(TreeChange.Put("content/posts/muetze/index.md", "---\ntitle: \"Mütze\"\ndate: 2026-10-13\n---\nT\n".toByteArray())), "x")
        h!!.container.draftChanges.value++
        waitFor { vm.ui.value.drafts.any { it.summary.dir == "muetze" } }
        clickDelete("muetze")
        assertFalse(exists("redirect-overview"))
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { vm.ui.value.drafts.isEmpty() }
    }

    @Test fun editorLoeschtDenBeitrag() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        var done = false
        val vm = EditorViewModel(harness.container, SavedStateHandle(), "baumaerkte")
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, { done = true }) } }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() }
        compose.onNodeWithTag("editor-delete").performClick()
        waitFor { exists("delete-dialog") }
        compose.onNodeWithTag("delete-confirm").performClick()
        waitFor { done }
        assertFalse(wf().store.exists(wf().refs.draft, "content/posts/baumaerkte/index.md"))
    }
}
