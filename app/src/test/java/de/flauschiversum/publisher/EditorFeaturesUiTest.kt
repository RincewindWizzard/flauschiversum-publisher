package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.TreeChange
import androidx.compose.ui.test.performTextClearance
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
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
class EditorFeaturesUiTest {
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

    private fun editor(dir: String? = null, onDone: () -> Unit = {}): EditorViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        val vm = EditorViewModel(harness.container, SavedStateHandle(), dir)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, onDone) } }
        waitFor { vm.ui.value.loaded }
        return vm
    }

    private fun tool(tag: String) = if (tag.startsWith("tool-")) compose.onNodeWithTag(tag).performScrollTo().performClick() else compose.onNodeWithTag(tag).performClick()

    @Test fun markierterTextWirdMitDerWerkzeugleisteFett() {
        val vm = editor()
        compose.onNodeWithTag("body-input").performTextInput("Eine Mütze hier")
        waitFor { vm.ui.value.body == "Eine Mütze hier" }
        compose.onNodeWithTag("body-input").performTextInputSelection(TextRange(5, 10))
        tool("tool-bold")
        waitFor { vm.ui.value.body == "Eine **Mütze** hier" }
    }

    @Test fun linkOhneMarkierungFuegtKlammernEin() {
        val vm = editor()
        tool("tool-link")
        waitFor { vm.ui.value.body == "[](https://)" }
    }

    @Test fun rueckgaengigStelltGeloeschtenTextWiederHer() {
        val vm = editor("baumaerkte")
        waitFor { vm.ui.value.body.isNotEmpty() }
        val original = vm.ui.value.body
        compose.onNodeWithTag("body-input").performTextInputSelection(TextRange(0, original.length))
        tool("tool-quote") // eine sichtbare Änderung, die rückgängig gemacht wird
        waitFor { vm.ui.value.body != original }
        tool("tool-undo")
        waitFor { vm.ui.value.body == original }
        tool("tool-redo")
        waitFor { vm.ui.value.body != original }
    }

    @Test fun fehlerErscheintNachKurzerPauseUndVerschwindetBeiBehebung() {
        val vm = editor()
        compose.onNodeWithTag("body-input").performTextInput("Das ist **sehr schön")
        waitFor { vm.ui.value.errorCount == 1 }
        tool("open-findings")
        waitFor { exists("findings-list") }
        compose.onNodeWithTag("finding-1").assertIsDisplayed().performClick()
        waitFor { !exists("findings-list") }
        // Behebung: schließendes ** ans Ende setzen
        compose.onNodeWithTag("body-input").performTextInputSelection(TextRange(vm.ui.value.body.length))
        compose.onNodeWithTag("body-input").performTextInput("**")
        waitFor { vm.ui.value.errorCount == 0 && vm.ui.value.analyzedText == vm.ui.value.body }
    }

    @Test fun speichernMitFehlerFragtNachUndLaesstFortfahren() {
        var done = false; val vm = editor { done = true }
        compose.onNodeWithTag("body-input").performTextInput("Das ist **sehr schön")
        waitFor { vm.ui.value.body.isNotEmpty() }
        vm.setTitle("Fehlerhaft")
        tool("save")
        waitFor { exists("save-anyway") }
        assertFalse(h!!.container.session.workflow()!!.hasDraft())
        compose.onNodeWithTag("save-back").performClick()
        waitFor { !exists("save-anyway") }
        tool("save"); waitFor { exists("save-anyway") }
        compose.onNodeWithTag("save-anyway").performClick()
        waitFor { done }
        assertTrue(h!!.container.session.workflow()!!.hasDraft())
    }

    @Test fun warnungenFuehrenNichtZurRueckfrage() {
        var done = false; val vm = editor { done = true }
        compose.onNodeWithTag("body-input").performTextInput("## Eins\n\n#### Vier\n")
        waitFor { vm.ui.value.warningCount == 1 }
        vm.setTitle("Mit Warnung")
        tool("save")
        waitFor { done }
    }

    @Test fun rechtschreibfehlerBietetVorschlagAnUndErsetzt() {
        val vm = editor()
        compose.onNodeWithTag("body-input").performTextInput("Das ist wundershön.")
        waitFor(60_000) { vm.ui.value.misspellings.size == 1 }
        compose.onNodeWithTag("body-input").performTextInputSelection(TextRange(10))
        waitFor { exists("suggestion-wunderschön") }
        compose.onNodeWithTag("suggestion-wunderschön").performClick()
        waitFor { vm.ui.value.body == "Das ist wunderschön." }
        waitFor { vm.ui.value.misspellings.isEmpty() && vm.ui.value.analyzedText == vm.ui.value.body }
    }

    @Test fun wortInsPersoenlicheWoerterbuchAufnehmen() {
        val vm = editor()
        compose.onNodeWithTag("body-input").performTextInput("Zzyxwolle ist weich.")
        waitFor(60_000) { vm.ui.value.misspellings.size == 1 }
        compose.onNodeWithTag("body-input").performTextInputSelection(TextRange(3))
        waitFor { exists("add-word") }
        compose.onNodeWithTag("add-word").performClick()
        waitFor { vm.ui.value.misspellings.isEmpty() && vm.ui.value.analyzedText == vm.ui.value.body }
        assertEquals(listOf("Zzyxwolle"), h!!.container.spell.personal.all())
    }

    @Test fun verlassenMitAenderungenFragtNach() {
        var left = false; val vm = editor { left = true }
        compose.onNodeWithTag("body-input").performTextInput("Etwas Text")
        waitFor { vm.ui.value.body.isNotEmpty() }
        tool("editor-back")
        waitFor { exists("leave-stay") }
        assertFalse(left)
        compose.onNodeWithTag("leave-stay").performClick()
        waitFor { !exists("leave-stay") }
        tool("editor-back"); waitFor { exists("leave-discard") }
        compose.onNodeWithTag("leave-discard").performClick()
        waitFor { left }
    }

    @Test fun verlassenOhneAenderungFragtNichtNach() {
        var left = false; editor("baumaerkte") { left = true }
        tool("editor-back")
        waitFor { left }
    }

    // ---- Beitrag bearbeiten (beitrag-bearbeiten)
    private fun changeOnServer(dir: String, text: String) {
        TestRepos.commit(h!!.blog.bare, "main", mapOf("content/posts/$dir/index.md" to text), "Server: $dir")
        h!!.container.requestSync(SyncReason.MANUAL)
    }
    private fun postText(dir: String, body: String) = Fixtures.post(dir).let { orig ->
        val head = orig.substringBefore("\n---\n", "") + "\n---\n"
        head + body
    }

    @Test fun neuereFassungOhneEingabeWirdUebernommen() {
        val vm = editor("baumaerkte"); waitFor { vm.ui.value.body.isNotEmpty() }
        waitFor { !h!!.container.sync.state.value.running && h!!.container.sync.state.value.generation >= 1 }
        changeOnServer("baumaerkte", postText("baumaerkte", "Neu vom Server.\n"))
        waitFor(60_000) { vm.ui.value.body == "Neu vom Server.\n" }
        waitFor { exists("updated-from-server") }
    }

    @Test fun neuereFassungMitEingabeZeigtNurHinweis() {
        val vm = editor("baumaerkte"); waitFor { vm.ui.value.body.isNotEmpty() }
        waitFor { !h!!.container.sync.state.value.running && h!!.container.sync.state.value.generation >= 1 }
        compose.onNodeWithTag("body-input").performTextInput(" Meine Ergänzung")
        waitFor { vm.ui.value.body.endsWith("Meine Ergänzung") }
        changeOnServer("baumaerkte", postText("baumaerkte", "Neu vom Server.\n"))
        waitFor(60_000) { vm.ui.value.serverChanged }
        assertTrue(vm.ui.value.body.endsWith("Meine Ergänzung"))
        waitFor { exists("server-changed") }
    }

    @Test fun beitragMitEntwurfZeigtDenEntwurfstext() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        val wf = harness.container.session.workflow()!!
        val doc = Fixtures.post("baumaerkte").let { it.substringBefore("\n---\n") + "\n---\nEntwurfstext\n" }
        wf.commit(listOf(TreeChange.Put("content/posts/baumaerkte/index.md", doc.toByteArray())), "Entwurf")
        val vm = EditorViewModel(harness.container, SavedStateHandle(), "baumaerkte")
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() }
        assertEquals("Entwurfstext\n", vm.ui.value.body)
    }

    @Test fun speichernOhneAenderungErzeugtKeinenCommit() {
        var done = false; val vm = editor("baumaerkte") { done = true }
        waitFor { vm.ui.value.body.isNotEmpty() }
        tool("save"); waitFor { done }
        assertEquals(null, h!!.container.session.workflow()!!.draftId())
    }

    @Test fun umlauteUndHtmlBleibenByteGenau() {
        var done = false; val vm = editor("baumaerkte") { done = true }
        waitFor { vm.ui.value.body.isNotEmpty() }
        val text = "Größe und Fußnote<br/>\n\nNadja Bender<br/>\n"
        compose.onNodeWithTag("body-input").performTextClearance()
        compose.onNodeWithTag("body-input").performTextInput(text)
        waitFor { vm.ui.value.body == text }
        tool("save"); waitFor { done }
        val wf = h!!.container.session.workflow()!!
        val file = String(wf.store.readBytes(wf.refs.draft, "content/posts/baumaerkte/index.md")!!, Charsets.UTF_8)
        assertTrue(file.endsWith("---\n$text"))
        assertEquals(Fixtures.post("baumaerkte").substringBefore("\n---\n") + "\n---\n", file.removeSuffix(text))
    }

    @Test fun dateiOhneFrontmatterEndeLaesstSichNichtBearbeiten() {
        val harness = AppTestHarness(tmp.newFolder(), BlogFixture.defaultFiles() + mapOf("content/posts/kaputt/index.md" to "---\ntitle: \"Kaputt\"\nKein Ende\n")).also { it.completeSetup(); h = it }
        val vm = EditorViewModel(harness.container, SavedStateHandle(), "kaputt")
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded }
        waitFor { exists("load-problem") }
    }
}
