package de.flauschiversum.publisher

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.diag.CrashHandler
import de.flauschiversum.publisher.diag.DiagKind
import de.flauschiversum.publisher.diag.DiagOp
import de.flauschiversum.publisher.diag.DiagResult
import de.flauschiversum.publisher.diag.DiagnosticLog
import de.flauschiversum.publisher.diag.DiagnosticShare
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class DiagnosticLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun eintragEnthaeltZeitVorgangServerUndFehlerart() {
        val log = DiagnosticLog(File(tmp.root, "p.log"), clock = { 0L })
        log.recordFailure(DiagOp.PUSH, GitFailure.KeyRejected, "git.example.org", 22)
        val t = log.text()
        assertTrue(t, t.contains("Push") && t.contains("fehlgeschlagen") && t.contains("Zugriff verweigert") && t.contains("git.example.org:22"))
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} · """).containsMatchIn(t))
    }
    @Test fun fehlerartenSindFesteTexteNieDieMeldung() {
        val secret = "BEGIN OPENSSH PRIVATE KEY geheim"
        assertEquals("Sonstiger Fehler", DiagKind.of(GitFailure.Other(secret)))
        val log = DiagnosticLog(File(tmp.root, "p.log"))
        log.recordFailure(DiagOp.CLONE, GitFailure.Other(secret), "h", 22)
        assertFalse(log.text().contains("geheim"))
    }
    @Test fun groessenGrenzeVerwirftDieAeltestenEintraege() {
        val log = DiagnosticLog(File(tmp.root, "p.log"), maxBytes = 5_000)
        repeat(300) { log.record(DiagOp.SYNC, DiagResult.OK, host = "server$it.example.org", port = 22) }
        assertTrue("Größe ${log.sizeBytes()}", log.sizeBytes() <= 5_000)
        val lines = log.text().lines().filter { it.isNotBlank() }
        assertTrue(lines.last().contains("server299")); assertFalse(lines.first().contains("server0.example"))
    }
    @Test fun einMegabyteGrenzeIstVorgabe() {
        val log = DiagnosticLog(File(tmp.root, "p.log"))
        repeat(20_000) { log.record(DiagOp.SYNC, DiagResult.OK, host = "server.example.org", port = 22) }
        assertTrue(log.sizeBytes() <= 1_000_000)
    }
    @Test fun loeschenLeertDasProtokoll() {
        val log = DiagnosticLog(File(tmp.root, "p.log")); log.record(DiagOp.SYNC, DiagResult.OK); log.clear()
        assertEquals("", log.text())
    }
    @Test fun absturzWirdVermerktUndAlsHinweisGemerkt() {
        val log = DiagnosticLog(File(tmp.root, "p.log"))
        val crash = CrashHandler(log, File(tmp.root, "marke"), "1.2.3 (10203, abc)")
        assertFalse(crash.pending())
        crash.handle(IllegalStateException("Beitragstext darf nicht ins Protokoll"))
        assertTrue(crash.pending())
        val t = log.text()
        assertTrue(t.contains("Absturz") && t.contains("java.lang.IllegalStateException") && t.contains("1.2.3") && t.contains("bei "))
        assertFalse(t.contains("Beitragstext"))
        crash.acknowledge(); assertFalse(crash.pending())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun fehlgeschlagenerPushErzeugtEintragOhneSchluessel() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        harness.container.photoPlacement = de.flauschiversum.publisher.photos.PhotoPlacement(object : de.flauschiversum.publisher.images.ImageProcessor {
            override fun process(input: () -> java.io.InputStream, originalName: String, mimeType: String?, rights: de.flauschiversum.publisher.images.Rights) =
                de.flauschiversum.publisher.images.ProcessedImage(input().use { it.readBytes() }, originalName, 1, 1, true)
        }) { de.flauschiversum.publisher.images.Rights("x", "y") }
        harness.blog.server.revokeAll()
        harness.container.uploads.submit(listOf(SharedPhoto(tmp.newFile().also { it.writeText("d") }, "a.jpg", "image/jpeg")), listOf("a"), PhotoTarget.Existing("baumaerkte"))
        val t = harness.container.diag.text()
        assertTrue(t, t.contains("Auftrag: Hochladen") && t.contains("Zugriff verweigert") && t.contains("127.0.0.1:${harness.blog.server.port}"))
        assertFalse(t.contains(harness.blog.key.publicLine.substringAfter(' ').take(20)))
        assertFalse(t.contains("PRIVATE"))
    }

    @Test fun anzeigenTeilenUndLoeschenInDenEinstellungen() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        harness.container.diag.record(DiagOp.PUSH, DiagResult.FAILED, "Zugriff verweigert", "git.example.org", 22)
        val vm = SettingsViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { SettingsScreen(vm, onBack = {}, onSetup = {}) } }
        compose.onNodeWithTag("diag-show").performScrollTo().performClick()
        compose.onNodeWithTag("diag-text").assertIsDisplayed()
        compose.onNodeWithTag("diag-close").performClick()
        // Teilen: Chooser-Intent mit Textdatei, die App- und Android-Version nennt
        compose.onNodeWithTag("diag-share").performScrollTo().performClick()
        val started = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("text/plain", send.type)
        val report = DiagnosticShare.report(harness.container.diag)
        assertTrue(report.contains("App-Version:") && report.contains("Android:") && report.contains("git.example.org:22"))
        // Löschen
        compose.onNodeWithTag("diag-clear").performScrollTo().performClick()
        compose.onNodeWithTag("diag-clear-confirm").performClick()
        compose.waitForIdle()
        assertEquals("", harness.container.diag.text())
    }
}
