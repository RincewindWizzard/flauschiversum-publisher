package de.flauschiversum.publisher

import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.git.UploadState
import de.flauschiversum.publisher.images.ImageProcessor
import de.flauschiversum.publisher.images.ProcessedImage
import de.flauschiversum.publisher.images.Rights
import de.flauschiversum.publisher.photos.PhotoPlacement
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.upload.JobPhoto
import de.flauschiversum.publisher.upload.JobStatus
import de.flauschiversum.publisher.upload.JobStore
import de.flauschiversum.publisher.upload.UploadJob
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadTest {
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }

    private fun harness(): AppTestHarness {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        harness.container.photoPlacement = PhotoPlacement(object : ImageProcessor {
            override fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights) =
                ProcessedImage(input().use { it.readBytes() }, originalName, 1, 1, true)
        }) { Rights("x", "y") }
        return harness
    }
    private fun photos(vararg names: String) = names.map { n -> SharedPhoto(tmp.newFile().also { it.writeText("data-$n") }, n, "image/jpeg") }
    private fun wf() = h!!.container.session.workflow()!!
    private val uploads get() = h!!.container.uploads
    private fun serverDraft() = h!!.blog.serverRef("refs/heads/draft")

    // ---- 1.1 Ablage
    @Test fun auftragWirdDauerhaftGespeichertUndGeladen() {
        val store = JobStore(File(tmp.root, "box"))
        val job = UploadJob("a1", "baumaerkte", null, null, listOf(JobPhoto("0", "Schöne Mütze.jpg", "image/jpeg", "Alt [x]\nzweite Zeile")), JobStatus.QUEUED, false, null, 5L, null, 0)
        store.save(job)
        assertEquals(job, JobStore(File(tmp.root, "box")).load("a1")) // auch nach „Neustart“
        store.update("a1") { it.copy(status = JobStatus.DONE, committed = true, finishedAt = 9L) }
        assertEquals(JobStatus.DONE, store.load("a1")!!.status); assertEquals(listOf("a1"), store.all().map { it.id })
        store.remove("a1"); assertNull(store.load("a1"))
    }

    // ---- 1.2 / 2.1 Sichern, Commit ohne Netz
    @Test fun teilenSichertSofortUndCommitEntstehtOhneNetz() {
        val harness = harness()
        harness.container.network.delegate = object : de.flauschiversum.publisher.git.NetworkInfo { override fun isOnline() = false; override fun isMetered() = false }
        harness.blog.server.pause()
        val shared = photos("a.jpg", "b.jpg")
        val id = uploads.submit(shared, listOf("eins", "zwei"), PhotoTarget.Existing("baumaerkte"))
        assertTrue(shared.none { it.file.exists() }) // aus dem Eingang in den Auftrag verschoben
        val job = uploads.store.load(id)!!
        assertTrue(job.committed); assertEquals(JobStatus.WAITING_NETWORK, job.status)
        assertTrue(wf().store.readText(wf().refs.draft, "content/posts/baumaerkte/index.md")!!.contains("![eins](a.jpg)"))
        assertEquals(UploadState.PENDING, wf().upload()) // „Noch nicht hochgeladen“
        assertFalse(uploads.store.hasPhotos(id)) // Originale nach dem Commit aufgeräumt
        assertNull(serverDraft())
    }

    // ---- 2.2 Wiederholung bei Funkloch
    @Test fun funklochWirdWiederholtUndDanachHochgeladen() {
        val harness = harness()
        harness.blog.server.pause()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(JobStatus.WAITING_NETWORK, uploads.store.load(id)!!.status)
        harness.blog.server.resume()
        runBlocking { uploads.pushStep(id, force = false) }
        assertEquals(JobStatus.DONE, uploads.store.load(id)!!.status)
        assertNotNull(serverDraft()); assertEquals(UploadState.UPLOADED, wf().upload())
    }

    @Test fun zugriffVerweigertWirdNichtWiederholt() {
        val harness = harness()
        harness.blog.server.revokeAll()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        val job = uploads.store.load(id)!!
        assertEquals(JobStatus.FAILED, job.status); assertTrue(job.message!!, job.message!!.contains("Zugriff"))
        assertTrue(job.committed) // Commit bleibt lokal
        assertTrue(wf().hasDraft()); assertEquals(UploadState.PENDING, wf().upload())
        assertEquals(1, job.attempts)
    }

    @Test fun abgelehnterPushIstEinDauerfehler() {
        val harness = harness()
        harness.blog.server.rejectReceivePack = true
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(JobStatus.FAILED, uploads.store.load(id)!!.status)
    }

    @Test fun wiederholenNachFehlschlag() {
        val harness = harness()
        harness.blog.server.revokeAll()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(JobStatus.FAILED, uploads.store.load(id)!!.status)
        harness.blog.server.allowWrite(harness.blog.key.publicLine)
        uploads.retry(id)
        assertEquals(JobStatus.DONE, uploads.store.load(id)!!.status)
        assertNotNull(serverDraft())
    }

    // ---- Nur über WLAN
    @Test fun mobilfunkMitEinstellungWartetAufWlanUndJetztSendenUmgehtEs() {
        val harness = harness()
        harness.metered = true
        runBlocking { harness.container.settings.update { it.copy(uploadWifiOnly = true) } }
        org.junit.Assert.assertTrue(waitSettings { it.uploadWifiOnly })
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        val job = uploads.store.load(id)!!
        assertEquals(JobStatus.WAITING_WIFI, job.status); assertEquals("Wartet auf WLAN", job.status.label)
        assertNull(serverDraft())
        uploads.sendNow(id)
        assertEquals(JobStatus.DONE, uploads.store.load(id)!!.status); assertNotNull(serverDraft())
        assertTrue(harness.scheduler.log.any { it.contains("force=true") })
    }
    private fun waitSettings(cond: (de.flauschiversum.publisher.data.AppSettings) -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { if (cond(h!!.container.settingsSnapshot)) return true; Thread.sleep(20) }
        return false
    }

    // ---- 2.3 Reihenfolge und Konsistenz
    @Test fun zweiAuftraegeLaufenNacheinanderMitEigenemCommit() {
        val harness = harness()
        harness.scheduler.paused = true
        val a = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        val b = uploads.submit(photos("b.jpg"), listOf("b"), PhotoTarget.Existing("baumaerkte"))
        assertEquals(listOf("commit:$a", "commit:$b"), harness.scheduler.log)
        harness.scheduler.resume()
        assertEquals(JobStatus.DONE, uploads.store.load(a)!!.status); assertEquals(JobStatus.DONE, uploads.store.load(b)!!.status)
        // zwei getrennte Commits auf draft über dem Zielbranch
        val draft = wf().parseCommit(wf().draftId()!!)
        assertEquals(1, draft.parentCount); assertEquals(wf().targetId(), wf().parseCommit(draft.getParent(0).id).getParent(0).id)
        val body = wf().store.readText(wf().refs.draft, "content/posts/baumaerkte/index.md")!!
        assertTrue(body.contains("![a](a.jpg)") && body.contains("![b](b.jpg)"))
    }

    @Test fun speichernImEditorWartetNichtAufDenPush() {
        val harness = harness()
        harness.blog.server.pause()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte")) // Push hängt nicht an der Sperre
        assertEquals(JobStatus.WAITING_NETWORK, uploads.store.load(id)!!.status)
        val t0 = System.currentTimeMillis()
        runBlocking { harness.container.session.locked { wf().commit(listOf(de.flauschiversum.publisher.git.TreeChange.Put("content/posts/neu/index.md", "---\ntitle: \"N\"\ndate: 2026-10-13\n---\nT\n".toByteArray())), "Editor") } }
        assertTrue(System.currentTimeMillis() - t0 < 5_000)
        harness.blog.server.resume()
        runBlocking { uploads.pushStep(id, false) }
        // der Push nimmt beide Commits mit
        assertEquals(wf().draftId()!!.name, harness.blog.serverRef("refs/heads/draft"))
    }

    // ---- 3.2 Abbrechen, Aufräumen
    @Test fun abbrechenVorDemStartLoeschtFotosUndAuftrag() {
        val harness = harness()
        harness.scheduler.paused = true
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertTrue(uploads.store.hasPhotos(id))
        assertTrue(uploads.canCancel(uploads.store.load(id)!!))
        assertTrue(uploads.cancel(id))
        assertNull(uploads.store.load(id)); assertFalse(File(uploads.store.dir(id), "photos").exists())
        assertFalse(wf().hasDraft())
    }

    @Test fun nachDemCommitLaesstSichNichtMehrAbbrechen() {
        harness()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        assertFalse(uploads.cancel(id))
    }

    @Test fun erledigteEintraegeVerschwindenNachSiebenTagen() {
        harness()
        val old = System.currentTimeMillis() - 8L * 24 * 3600 * 1000
        val fresh = System.currentTimeMillis() - 2L * 24 * 3600 * 1000
        uploads.store.save(UploadJob("alt", "x", null, null, emptyList(), JobStatus.DONE, true, null, old, old, 1))
        uploads.store.save(UploadJob("neu", "x", null, null, emptyList(), JobStatus.DONE, true, null, fresh, fresh, 1))
        uploads.store.save(UploadJob("fehler", "x", null, null, emptyList(), JobStatus.FAILED, true, "x", old, old, 1))
        uploads.cleanup()
        assertEquals(setOf("neu", "fehler"), uploads.store.all().map { it.id }.toSet())
    }

    @Test fun nachNeustartWerdenLiegengebliebeneAuftraegeFortgesetzt() {
        val harness = harness()
        harness.scheduler.paused = true
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        harness.scheduler.pending.clear(); harness.scheduler.log.clear(); harness.scheduler.paused = false
        uploads.resumeAll() // wie nach einem Neustart des Geräts
        assertEquals(JobStatus.DONE, uploads.store.load(id)!!.status)
        assertTrue(harness.scheduler.log.first().startsWith("commit:"))
    }

    @Test fun neuerBeitragAlsAuftrag() {
        harness()
        val id = uploads.submit(photos("a.jpg"), listOf("a"), PhotoTarget.New("Fotoabend", "Ermeline"))
        assertEquals("fotoabend", uploads.store.load(id)!!.resultDir)
        assertEquals(JobStatus.DONE, uploads.store.load(id)!!.status)
    }
}
