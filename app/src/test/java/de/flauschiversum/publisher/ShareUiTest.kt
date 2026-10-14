package de.flauschiversum.publisher

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.images.ImageProcessor
import de.flauschiversum.publisher.images.ProcessedImage
import de.flauschiversum.publisher.images.Rights
import de.flauschiversum.publisher.photos.PhotoPlacement
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.share.ShareScreen
import de.flauschiversum.publisher.ui.share.ShareViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
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
class ShareUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }

    private fun harness() = AppTestHarness(tmp.newFolder()).also {
        it.completeSetup(); h = it
        it.container.photoPlacement = PhotoPlacement(object : ImageProcessor {
            override fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights) =
                ProcessedImage(input().use { s -> s.readBytes() }, originalName, 1, 1, true)
        }) { Rights("x", "y") }
    }

    private fun waitFor(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            ShadowLooper.idleMainLooper(); compose.waitForIdle(); Thread.sleep(20)
        }
    }

    private fun share(h: AppTestHarness, vararg names: String) {
        h.container.incoming.pending.value = names.map { n -> SharedPhoto(tmp.newFile().also { it.writeText(n) }, n, "image/jpeg") }
    }

    private fun start(h: AppTestHarness, closed: AtomicBoolean) {
        val vm = ShareViewModel(h.container)
        compose.setContent { FlauschiversumPublisherTheme { ShareScreen(vm, onClose = { closed.set(true) }) } }
        waitFor { vm.ui.value.posts.isNotEmpty() }
    }

    @Test fun abbrechenAendertNichtsUndLoeschtDieKopien() {
        val h = harness(); share(h, "IMG_1.jpg"); val closed = AtomicBoolean(false)
        val file = h.container.incoming.pending.value.single().file
        start(h, closed)
        compose.onNodeWithTag("share-cancel").performClick()
        waitFor { closed.get() }
        assertNull(h.container.session.workflow()!!.draftId())
        assertTrue(h.container.incoming.pending.value.isEmpty())
        assertFalse(file.exists() && h.container.incoming.pending.value.isNotEmpty())
    }

    @Test fun ablegenBrauchtZielUndLegtFotoMitBeschreibungAb() {
        val h = harness(); share(h, "IMG_1.jpg"); val closed = AtomicBoolean(false)
        start(h, closed)
        compose.onNodeWithTag("share-save").assertIsNotEnabled()
        compose.onNodeWithTag("alt-0").performTextInput("Ein Baum")
        compose.onNodeWithTag("share-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("post-search"))
        compose.onNodeWithTag("post-search").performTextInput("Baum")
        // die Suche filtert asynchron: erst wenn der Eintrag in der Liste steht, lässt er sich anklicken
        waitFor {
            runCatching {
                compose.onNodeWithTag("share-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("post-baumaerkte"))
                compose.onNodeWithTag("post-baumaerkte").assertExists()
            }.isSuccess
        }
        compose.onNodeWithTag("post-baumaerkte").performClick()
        compose.onNodeWithTag("share-save").assertIsEnabled().performClick()
        waitFor { closed.get() }
        val wf = h.container.session.workflow()!!
        waitFor { wf.hasDraft() }
        val index = wf.store.readText(wf.refs.draft, "content/posts/baumaerkte/index.md")!!
        assertTrue(index.trimEnd().endsWith("## Anhänge\n\n![Ein Baum](IMG_1.jpg)"))
        assertEquals("IMG_1.jpg", String(wf.store.readBytes(wf.refs.draft, "content/posts/baumaerkte/IMG_1.jpg")!!))
        assertTrue(h.container.incoming.pending.value.isEmpty())
    }

    @Test fun neuerBeitragBrauchtTitel() {
        val h = harness(); share(h, "a.jpg"); val closed = AtomicBoolean(false)
        start(h, closed)
        compose.onNodeWithTag("share-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("choose-new"))
        compose.onNodeWithTag("choose-new").performClick()
        compose.onNodeWithTag("share-save").assertIsNotEnabled()
        compose.onNodeWithTag("share-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("new-title"))
        compose.onNodeWithTag("new-title").performTextInput("Fotoabend")
        compose.onNodeWithTag("share-save").assertIsEnabled().performClick()
        waitFor { closed.get() }
        val wf = h.container.session.workflow()!!
        waitFor { wf.hasDraft() }
        assertTrue(wf.store.exists(wf.refs.draft, "content/posts/fotoabend/index.md"))
    }

    @Test fun teilenIntentOeffnetDieZuordnungTextTeilenNicht() {
        val h = harness()
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        fun resolves(type: String, action: String = Intent.ACTION_SEND) =
            pm.queryIntentActivities(Intent(action).setType(type).setPackage("de.flauschiversum.publisher"), 0).any { it.activityInfo.name.endsWith("MainActivity") }
        assertTrue(resolves("image/jpeg")); assertTrue(resolves("image/png", Intent.ACTION_SEND_MULTIPLE))
        assertFalse(resolves("text/plain"))
        assertFalse(h.container.incoming.isShare(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }
}
