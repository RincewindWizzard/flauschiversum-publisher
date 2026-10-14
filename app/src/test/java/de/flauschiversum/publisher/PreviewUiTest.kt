package de.flauschiversum.publisher

import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.preview.PreviewPolicy
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.preview.PreviewLoader
import de.flauschiversum.publisher.ui.preview.PreviewScreen
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import kotlinx.coroutines.runBlocking
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
class PreviewUiTest {
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
    private fun harness() = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }

    @Test fun richtlinienFuerLinksUndSeiteneigeneAdressen() {
        assertTrue(PreviewPolicy.needsConfirmation(Uri.parse("https://beispiel.de/")))
        assertTrue(PreviewPolicy.needsConfirmation(Uri.parse("mailto:a@b.de")))
        assertFalse(PreviewPolicy.needsConfirmation(Uri.parse("https://appassets.androidplatform.net/post/x/a.jpg")))
        assertTrue(PreviewPolicy.isInternal(Uri.parse("https://appassets.androidplatform.net/page#lb-1")))
        assertFalse(PreviewPolicy.isInternal(Uri.parse("https://beispiel.de/")))
    }

    @Test fun editorVorschauZeigtDenUngespeichertenText() {
        val harness = harness()
        val vm = EditorViewModel(harness.container, SavedStateHandle(), "baumaerkte")
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() }
        compose.onNodeWithTag("body-input").performTextInput(" Ein neuer Satz.")
        waitFor { vm.ui.value.body.endsWith("Ein neuer Satz.") }
        compose.onNodeWithTag("toggle-preview").performClick()
        waitFor { exists("preview-web") }
        val data = vm.previewData()
        assertTrue(data.input.body.endsWith("Ein neuer Satz."))
        assertEquals("Baumärkte", data.input.title)
        compose.onNodeWithTag("toggle-preview").performClick()
        waitFor { exists("body-input") }
    }

    @Test fun vorschauAusDerUebersichtLaedtDenBeitrag() {
        val harness = harness()
        val data = runBlocking { PreviewLoader.load(harness.container, "baumaerkte") }!!
        assertEquals("Baumärkte", data.input.title)
        compose.setContent { FlauschiversumPublisherTheme { PreviewScreen(harness.container, "baumaerkte", onBack = {}) } }
        waitFor { exists("preview-web") }
    }

    @Test fun menueEintragVorschauRuftDieVorschauAuf() {
        val harness = harness(); val opened = mutableListOf<String>()
        val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}, onOpenPreview = { opened += it }) } }
        waitFor { vm.ui.value.posts.isNotEmpty() }
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("search"))
        compose.onNodeWithTag("search").performTextInput("Baumärkte")
        waitFor { exists("menu-baumaerkte") }
        compose.onNodeWithTag("menu-baumaerkte").performClick()
        compose.onNodeWithTag("action-preview-baumaerkte").performClick()
        assertEquals(listOf("baumaerkte"), opened)
    }

    @Test fun hinweisErscheintNurBeimErstenMal() {
        val harness = harness()
        assertFalse(harness.container.settingsSnapshot.previewHintShown)
        compose.setContent { FlauschiversumPublisherTheme { PreviewScreen(harness.container, "baumaerkte", onBack = {}) } }
        waitFor { exists("preview-hint") }
        compose.onNodeWithTag("preview-hint-ok").performClick()
        waitFor { !exists("preview-hint") }
        waitFor { harness.container.settingsSnapshot.previewHintShown }
        assertTrue(harness.container.settingsSnapshot.previewHintShown) // gilt für alle weiteren Öffnungen
    }
}
