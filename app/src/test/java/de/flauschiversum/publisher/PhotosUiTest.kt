package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.ui.photos.PhotosScreen
import de.flauschiversum.publisher.ui.photos.PhotosViewModel
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
class PhotosUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }
    private val index = "---\ntitle: \"Fotos\"\ndate: 2026-10-13\nimage: a.jpg\n---\nText\n\n## Anhänge\n\n![eins](a.jpg)\n![zwei](b.jpg)\n![drei](c.jpg)\n"
    private fun waitFor(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            ShadowLooper.idleMainLooper(); compose.waitForIdle(); Thread.sleep(20)
        }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun open(): PhotosViewModel {
        val files = BlogFixture.defaultFiles() + mapOf("content/posts/fotos/index.md" to index, "content/posts/fotos/a.jpg" to "A", "content/posts/fotos/b.jpg" to "B",
            "content/posts/fotos/c.jpg" to "C", "content/posts/fotos/d.jpg" to "D")
        val harness = AppTestHarness(tmp.newFolder(), files).also { it.completeSetup(); h = it }
        val vm = PhotosViewModel(harness.container, "fotos")
        compose.setContent { FlauschiversumPublisherTheme { PhotosScreen(vm, onBack = {}) } }
        waitFor { vm.ui.value.photos.size == 4 }
        return vm
    }
    private fun wf() = h!!.container.session.workflow()!!
    private fun body() = PostDocument.parse(wf().store.readText(wf().refs.draft, "content/posts/fotos/index.md")!!).body
    private fun scrollTo(tag: String) { compose.onNodeWithTag("photos-list").performScrollToNode(hasTestTag(tag)) }

    @Test fun rasterMitTitelbildUndNichtImTextKennzeichnung() {
        open()
        scrollTo("badge-cover-a.jpg"); compose.onNodeWithTag("badge-cover-a.jpg", useUnmergedTree = true).assertIsDisplayed()
        scrollTo("badge-notintext-d.jpg"); compose.onNodeWithTag("badge-notintext-d.jpg", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(exists("badge-notintext-b.jpg"))
    }

    @Test fun altTextAendernSchreibtNurIndexMd() {
        val vm = open()
        scrollTo("alt-b.jpg")
        compose.onNodeWithTag("alt-b.jpg").performTextReplacement("Zweites Foto")
        waitFor { vm.ui.value.altEdits["b.jpg"] == "Zweites Foto" }
        compose.waitForIdle()
        compose.onNodeWithTag("save-alt-b.jpg").performScrollTo().performClick()
        waitFor { wf().hasDraft() && body().contains("![Zweites Foto](b.jpg)") }
        assertEquals(listOf("content/posts/fotos/index.md"), wf().ownChanges().map { it.path })
        waitFor { vm.ui.value.photos.first { it.file == "b.jpg" }.alt == "Zweites Foto" }
    }

    @Test fun nichtEingebundenesBildEinbinden() {
        open()
        scrollTo("alt-d.jpg")
        compose.onNodeWithTag("alt-d.jpg").performScrollTo().performTextInput("Detail")
        compose.onNodeWithTag("include-d.jpg").performScrollTo().performClick()
        waitFor { wf().hasDraft() && body().trimEnd().endsWith("![Detail](d.jpg)") }
    }

    @Test fun reihenfolgeAendern() {
        val vm = open()
        scrollTo("up-c.jpg"); compose.onNodeWithTag("up-c.jpg").performScrollTo().performClick()
        waitFor { wf().hasDraft() && body().contains("![zwei](b.jpg)\n![drei](c.jpg)").not() }
        assertEquals(listOf("a.jpg", "c.jpg", "b.jpg"), vm.ui.value.photos.filter { it.inAttachments }.map { it.file }.let { waitFor { vm.ui.value.photos.filter { p -> p.inAttachments }.map { p -> p.file } == listOf("a.jpg", "c.jpg", "b.jpg") }; it })
    }

    @Test fun fotoEntfernenMitBestaetigungUndAbbrechen() {
        open()
        scrollTo("remove-b.jpg"); compose.onNodeWithTag("remove-b.jpg").performScrollTo().performClick()
        waitFor { exists("remove-dialog") }
        compose.onNodeWithTag("remove-cancel").performClick()
        waitFor { !exists("remove-dialog") }
        assertFalse(wf().hasDraft())
        compose.onNodeWithTag("remove-b.jpg").performScrollTo().performClick()
        waitFor { exists("remove-dialog") }
        compose.onNodeWithTag("remove-confirm").performClick()
        waitFor { wf().hasDraft() && !body().contains("b.jpg") }
        assertNull(wf().store.readBytes(wf().refs.draft, "content/posts/fotos/b.jpg"))
    }

    @Test fun titelbildEntfernenVerlangtZuerstEineWahl() {
        open()
        scrollTo("remove-a.jpg"); compose.onNodeWithTag("remove-a.jpg").performScrollTo().performClick()
        waitFor { exists("cover-required") }
        compose.onNodeWithTag("remove-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("newcover-none").performClick()
        compose.onNodeWithTag("remove-confirm").performClick()
        waitFor { wf().hasDraft() && !body().contains("a.jpg") }
        assertFalse(PostDocument.parse(wf().store.readText(wf().refs.draft, "content/posts/fotos/index.md")!!).head.contains("image:"))
    }
}
