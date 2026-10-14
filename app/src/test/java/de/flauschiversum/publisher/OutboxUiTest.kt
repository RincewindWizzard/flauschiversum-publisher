package de.flauschiversum.publisher

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.outbox.OutboxScreen
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import de.flauschiversum.publisher.upload.CommitWorker
import de.flauschiversum.publisher.upload.JobStatus
import de.flauschiversum.publisher.upload.PushWorker
import de.flauschiversum.publisher.upload.UploadJob
import de.flauschiversum.publisher.upload.WorkManagerScheduler
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
class OutboxUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }
    private fun waitFor(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            ShadowLooper.idleMainLooper(); compose.waitForIdle(); Thread.sleep(20)
        }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun harness() = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
    private fun photos(vararg names: String) = names.map { n -> SharedPhoto(tmp.newFile().also { it.writeText("d") }, n, "image/jpeg") }
    private fun show(harness: AppTestHarness) { compose.setContent { FlauschiversumPublisherTheme { OutboxScreen(harness.container, onBack = {}) } } }

    @Test fun leereAusgangsbox() { show(harness()); compose.onNodeWithTag("outbox-empty").assertIsDisplayed() }

    @Test fun fehlgeschlagenerAuftragLaesstSichWiederholen() {
        val harness = harness(); harness.blog.server.revokeAll()
        val id = harness.container.uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        show(harness)
        waitFor { exists("status-$id") }
        compose.onNodeWithTag("status-$id").assertIsDisplayed()
        assertTrue(harness.container.uploads.store.load(id)!!.message!!.contains("Zugriff"))
        harness.blog.server.allowWrite(harness.blog.key.publicLine)
        compose.onNodeWithTag("retry-$id").performClick()
        waitFor { harness.container.uploads.store.load(id)!!.status == JobStatus.DONE }
        waitFor { !exists("retry-$id") }
    }

    @Test fun abbrechenVorDemStartMitBestaetigung() {
        val harness = harness(); harness.scheduler.paused = true
        val id = harness.container.uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        show(harness)
        waitFor { exists("cancel-$id") }
        compose.onNodeWithTag("cancel-$id").performClick()
        compose.onNodeWithTag("cancel-keep").performClick()
        waitFor { !exists("cancel-confirm") }
        assertTrue(harness.container.uploads.store.hasPhotos(id))
        compose.onNodeWithTag("cancel-$id").performClick()
        compose.onNodeWithTag("cancel-confirm").performClick()
        waitFor { !exists("job-$id") }
        assertFalse(harness.container.uploads.store.hasPhotos(id))
    }

    @Test fun wartenAufWlanZeigtJetztSenden() {
        val harness = harness(); harness.metered = true
        runBlocking { harness.container.settings.update { it.copy(uploadWifiOnly = true) } }
        waitFor { harness.container.settingsSnapshot.uploadWifiOnly }
        val id = harness.container.uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        show(harness)
        waitFor { exists("sendnow-$id") }
        compose.onNodeWithTag("sendnow-$id").performClick()
        waitFor { harness.container.uploads.store.load(id)!!.status == JobStatus.DONE }
    }

    // ---- WorkManager-Anbindung: Ergebnis der Worker je Schritt
    private inline fun <reified W : androidx.work.CoroutineWorker> run(id: String, force: Boolean = false): ListenableWorker.Result {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<W>(ctx).setInputData(workDataOf(WorkManagerScheduler.KEY_ID to id, WorkManagerScheduler.KEY_FORCE to force)).build()
        return runBlocking { worker.doWork() }
    }

    @Test fun workerMeldenErgebnisseFuerWorkManager() {
        val harness = harness()
        harness.scheduler.paused = true
        val id = harness.container.uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(ListenableWorker.Result.success(), run<CommitWorker>(id))
        harness.blog.server.pause()
        assertEquals(ListenableWorker.Result.retry(), run<PushWorker>(id)) // Funkloch: später wiederholen
        harness.blog.server.resume()
        assertEquals(ListenableWorker.Result.success(), run<PushWorker>(id))
        assertEquals(JobStatus.DONE, harness.container.uploads.store.load(id)!!.status)
        harness.blog.server.revokeAll()
        harness.scheduler.paused = false
        val failing = harness.container.uploads.submit(photos("b.jpg"), listOf("b"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(JobStatus.FAILED, harness.container.uploads.store.load(failing)!!.status)
        assertEquals(ListenableWorker.Result.failure(), run<PushWorker>(failing)) // Dauerfehler: nicht wiederholen
    }
}
