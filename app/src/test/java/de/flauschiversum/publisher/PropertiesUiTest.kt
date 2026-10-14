package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
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
class PropertiesUiTest {
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

    private fun openProperties(dir: String?, prepare: (AppTestHarness) -> Unit = {}): EditorViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        prepare(harness)
        val vm = EditorViewModel(harness.container, SavedStateHandle(), dir)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded }
        compose.onNodeWithTag("editor-menu").performClick(); compose.onNodeWithTag("open-properties").performClick()
        waitFor { exists("prop-title") }
        return vm
    }
    private fun withImages(h: AppTestHarness) {
        TestRepos.commit(h.blog.bare, "main", mapOf("content/posts/baumaerkte/a.jpg" to "x", "content/posts/baumaerkte/b.jpg" to "y"), "Bilder")
        h.container.requestSync(SyncReason.MANUAL)
        waitFor { h.container.session.store()?.exists("refs/remotes/origin/main", "content/posts/baumaerkte/b.jpg") == true }
    }

    @Test fun schlagwortSucheFiltertDieAuswahl() {
        openProperties(null)
        compose.onNodeWithTag("tag-search").performScrollTo().performTextInput("Filz")
        waitFor { exists("tag-Filzen") && !exists("tag-Hüte") }
        compose.onNodeWithTag("tag-search").performTextInput("xyzxyz")
        waitFor { exists("tag-none") }
    }

    @Test fun schlagwortWaehlenUndAbwaehlen() {
        val vm = openProperties(null)
        compose.onNodeWithTag("tag-Hüte").performScrollTo().performClick()
        waitFor { vm.ui.value.tags == listOf("Hüte") }
        compose.onNodeWithTag("tag-Hüte").performClick()
        waitFor { vm.ui.value.tags.isEmpty() }
    }

    @Test fun unbekanntesSchlagwortWirdMarkiert() {
        val vm = openProperties(null)
        vm.toggleTag("Gibtsnicht")
        waitFor { exists("tag-unknown-Gibtsnicht") }
    }

    @Test fun autorAusBekanntenWaehlen() {
        val vm = openProperties(null)
        waitFor { vm.ui.value.knownAuthors.isNotEmpty() }
        val author = vm.ui.value.knownAuthors.last()
        compose.onNodeWithTag("author-$author").performScrollTo().performClick()
        waitFor { vm.ui.value.author == author }
        assertTrue("Ermeline" in vm.ui.value.knownAuthors)
    }

    @Test fun zeitWaehlenUndEntfernen() {
        val vm = openProperties(null)
        compose.onNodeWithTag("prop-time").performScrollTo().performClick()
        waitFor { exists("time-ok") }
        compose.onNodeWithTag("time-ok").performClick()
        waitFor { vm.ui.value.date?.time != null }
        assertTrue(vm.ui.value.date!!.render().matches(Regex("""\d{4}-\d{2}-\d{2} 12:00:00""")))
        compose.onNodeWithTag("prop-time-clear").performScrollTo().performClick()
        waitFor { vm.ui.value.date?.time == null }
        assertTrue(vm.ui.value.date!!.render().matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test fun kurzbeschreibungZaehlerUndAusTextUebernehmen() {
        val vm = openProperties(null)
        vm.setBody("Wer kann schon sagen er hätte einen Ring selbstgemacht? Alle, die …")
        compose.onNodeWithTag("excerpt-from-text").performScrollTo().performClick()
        waitFor { vm.ui.value.excerpt == "Wer kann schon sagen er hätte einen Ring selbstgemacht?" }
        waitFor { vm.ui.value.excerpt.length == 55 }
        compose.onNodeWithTag("excerpt-counter", useUnmergedTree = true).assertExists()
    }

    @Test fun kurzbeschreibungPrueftRechtschreibung() {
        val vm = openProperties(null)
        compose.onNodeWithTag("prop-excerpt").performScrollTo().performTextInput("Das ist wundershön.")
        waitFor(60_000) { vm.ui.value.excerptMisspellings.map { it.word } == listOf("wundershön") }
    }

    @Test fun titelbildAusDenBildernWaehlenOderEntfernen() {
        val vm = openProperties("baumaerkte") { withImages(it) }
        waitFor { vm.ui.value.imageFiles.containsAll(listOf("a.jpg", "b.jpg")) }
        compose.onNodeWithTag("cover-a.jpg").performScrollTo().performClick()
        waitFor { vm.ui.value.image == "a.jpg" }
        compose.onNodeWithTag("cover-none").performScrollTo().performClick()
        waitFor { vm.ui.value.image == null }
    }

    @Test fun fehlendesTitelbildWirdAngezeigtUndVerhindertDasSpeichern() {
        val vm = openProperties("baumaerkte")
        vm.setImage("gibtsnicht.jpg")
        waitFor { exists("cover-missing") }
        vm.save()
        waitFor { vm.ui.value.errors.any { it.field == "image" } }
        waitFor { exists("cover-error") }
        assertFalse(h!!.container.session.workflow()!!.hasDraft())
    }

    @Test fun neuerBeitragHatKeineBilderZurAuswahl() {
        val vm = openProperties(null)
        assertTrue(vm.ui.value.imageFiles.isEmpty()); assertNull(vm.ui.value.image)
        assertEquals(true, vm.ui.value.isNew)
    }
}
