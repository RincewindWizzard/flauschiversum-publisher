package de.flauschiversum.publisher

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.lock.AppLock
import de.flauschiversum.publisher.lock.AuthResult
import de.flauschiversum.publisher.lock.Authenticator
import de.flauschiversum.publisher.lock.LockAvailability
import de.flauschiversum.publisher.lock.LockDelay
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.lock.LockGate
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

class AppLockLogicTest {
    private var now = 0L
    private fun lock(enabled: Boolean = true, delay: LockDelay = LockDelay.ONE_MINUTE) = AppLock({ AppSettings(lockEnabled = enabled, lockDelay = delay.name) }, { now })

    @Test fun beimProzessstartGesperrtNurWennAn() { assertTrue(lock().locked.value); assertFalse(lock(enabled = false).locked.value) }
    @Test fun schnellZurueckBleibtOffen() {
        val l = lock(); l.unlock(); now = 100_000; l.onLeave(); now = 120_000; l.onReturn()
        assertFalse(l.locked.value)
    }
    @Test fun nachEinerMinuteGesperrt() {
        val l = lock(); l.unlock(); now = 100_000; l.onLeave(); now = 220_000; l.onReturn()
        assertTrue(l.locked.value)
    }
    @Test fun sofortSperrtBeimVerlassen() {
        val l = lock(delay = LockDelay.IMMEDIATE); l.unlock(); now = 1; l.onLeave(); l.onReturn()
        assertTrue(l.locked.value)
    }
    @Test fun fuenfMinuten() {
        val l = lock(delay = LockDelay.FIVE_MINUTES); l.unlock(); now = 0; l.onLeave(); now = 4 * 60_000L; l.onReturn(); assertFalse(l.locked.value)
        l.onLeave(); now += 6 * 60_000L; l.onReturn(); assertTrue(l.locked.value)
    }
    @Test fun ausgeschaltetSperrtNie() { val l = lock(enabled = false); now = 0; l.onLeave(); now = 10_000_000; l.onReturn(); assertFalse(l.locked.value) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLockUiTest {
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

    private class FakeAuth(var availability: LockAvailability = LockAvailability.AVAILABLE, var results: MutableList<AuthResult> = mutableListOf(AuthResult.SUCCESS)) : Authenticator {
        var calls = 0
        override fun availability(context: Context) = availability
        override suspend fun authenticate(context: Context, title: String): AuthResult { calls++; return if (results.isEmpty()) AuthResult.FAILED else results.removeAt(0) }
    }
    private fun harness(auth: FakeAuth): AppTestHarness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); it.container.authenticator = auth; h = it }
    private fun enableLock(harness: AppTestHarness) {
        runBlocking { harness.container.settings.update { it.copy(lockEnabled = true) } }
        waitFor { harness.container.settingsSnapshot.lockEnabled }
    }

    @Test fun gesperrteAppZeigtKeinenInhaltBisZurAuthentifizierung() {
        val auth = FakeAuth(results = mutableListOf(AuthResult.SUCCESS))
        val harness = harness(auth); enableLock(harness)
        val lock = AppLock({ harness.container.settingsSnapshot })
        // eigene Sperre statt der des Containers, damit der Startzustand „gesperrt“ ist
        val container = harness.container
        compose.setContent { FlauschiversumPublisherTheme { LockGate(container.also { it.lock = lock }) { Text("GEHEIM", modifier = androidx.compose.ui.Modifier.testTag("inhalt")) } } }
        waitFor { exists("inhalt") }
        assertEquals(1, auth.calls) // beim Öffnen sofort gefragt
    }

    @Test fun fehlschlaegeLassenDieAppGeschlossenUndBietenNurErneutesEntsperren() {
        val auth = FakeAuth(results = mutableListOf(AuthResult.FAILED, AuthResult.CANCELLED, AuthResult.FAILED))
        val harness = harness(auth); enableLock(harness)
        val lock = AppLock({ harness.container.settingsSnapshot })
        compose.setContent { FlauschiversumPublisherTheme { LockGate(harness.container.also { it.lock = lock }) { Text("GEHEIM", modifier = androidx.compose.ui.Modifier.testTag("inhalt")) } } }
        waitFor { exists("lock-failed") }
        assertFalse(exists("inhalt")); assertTrue(exists("lock-screen"))
        compose.onNodeWithTag("unlock").performClick(); waitFor { auth.calls == 2 }
        compose.onNodeWithTag("unlock").performClick(); waitFor { auth.calls == 3 }
        waitFor { exists("lock-failed") }
        assertFalse(exists("inhalt"))
        assertTrue(lock.locked.value)
    }

    @Test fun teilenBeiSperreFordertErstDieAuthentifizierungDannGehtEsWeiter() {
        val auth = FakeAuth(results = mutableListOf(AuthResult.FAILED, AuthResult.SUCCESS))
        val harness = harness(auth); enableLock(harness)
        harness.container.incoming.pending.value = listOf(de.flauschiversum.publisher.photos.SharedPhoto(tmp.newFile(), "a.jpg", "image/jpeg"))
        val lock = AppLock({ harness.container.settingsSnapshot })
        compose.setContent { FlauschiversumPublisherTheme { LockGate(harness.container.also { it.lock = lock }) { Text("ZIELAUSWAHL", modifier = androidx.compose.ui.Modifier.testTag("inhalt")) } } }
        waitFor { exists("lock-failed") }
        assertFalse(exists("inhalt"))
        compose.onNodeWithTag("unlock").performClick()
        waitFor { exists("inhalt") }
        assertEquals(1, harness.container.incoming.pending.value.size) // das geteilte Foto wartet weiter auf die Zuordnung
    }

    @Test fun hintergrundauftraegeLaufenBeiGesperrterAppWeiter() {
        val harness = harness(FakeAuth()); enableLock(harness)
        harness.container.photoPlacement = de.flauschiversum.publisher.photos.PhotoPlacement(object : de.flauschiversum.publisher.images.ImageProcessor {
            override fun process(input: () -> java.io.InputStream, originalName: String, mimeType: String?, rights: de.flauschiversum.publisher.images.Rights) =
                de.flauschiversum.publisher.images.ProcessedImage(input().use { it.readBytes() }, originalName, 1, 1, true)
        }) { de.flauschiversum.publisher.images.Rights("x", "y") }
        harness.container.lock = AppLock({ harness.container.settingsSnapshot })
        assertTrue(harness.container.lock.locked.value)
        val id = harness.container.uploads.submit(listOf(de.flauschiversum.publisher.photos.SharedPhoto(tmp.newFile().also { it.writeText("d") }, "a.jpg", "image/jpeg")), listOf("a"), de.flauschiversum.publisher.photos.PhotoTarget.Existing("baumaerkte"))
        assertEquals(de.flauschiversum.publisher.upload.JobStatus.DONE, harness.container.uploads.store.load(id)!!.status) // ohne Abfrage
    }

    // ---- Einschalten in den Einstellungen
    private fun settingsScreen(harness: AppTestHarness): SettingsViewModel {
        val vm = SettingsViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { SettingsScreen(vm, onBack = {}, onSetup = {}) } }
        return vm
    }

    @Test fun einschaltenVerlangtBestaetigung() {
        val auth = FakeAuth(); val harness = harness(auth); settingsScreen(harness)
        compose.onNodeWithTag("lock-switch").performScrollTo().performClick()
        waitFor { harness.container.settingsSnapshot.lockEnabled }
        assertEquals(1, auth.calls); assertFalse(harness.container.lock.locked.value) // bleibt offen
        waitFor { exists("lock-delay-ONE_MINUTE") }
    }

    @Test fun ohneBildschirmsperreBleibtDerSchalterAusUndErklaertWarum() {
        val auth = FakeAuth(availability = LockAvailability.NOT_SET_UP); val harness = harness(auth); settingsScreen(harness)
        compose.onNodeWithTag("lock-switch").performScrollTo().performClick()
        waitFor { exists("lock-message") }
        assertFalse(harness.container.settingsSnapshot.lockEnabled); assertEquals(0, auth.calls)
    }

    @Test fun fehlendeBestaetigungSchaltetNichtEin() {
        val auth = FakeAuth(results = mutableListOf(AuthResult.CANCELLED)); val harness = harness(auth); settingsScreen(harness)
        compose.onNodeWithTag("lock-switch").performScrollTo().performClick()
        waitFor { exists("lock-message") }
        assertFalse(harness.container.settingsSnapshot.lockEnabled)
    }

    @Test fun sperrzeitWaehlen() {
        val harness = harness(FakeAuth()); enableLock(harness); settingsScreen(harness)
        waitFor { exists("lock-delay-FIVE_MINUTES") }
        compose.onNodeWithTag("lock-delay-FIVE_MINUTES").performScrollTo().performClick()
        waitFor { harness.container.settingsSnapshot.lockDelay == "FIVE_MINUTES" }
    }

    // ---- FLAG_SECURE und Sperre beim Start der Activity
    @Test fun aktiveSperreSetztFlagSecureUndSperrtDenStart() {
        val auth = FakeAuth(results = mutableListOf())
        val harness = harness(auth)
        runBlocking { harness.container.settings.update { it.copy(lockEnabled = true) } }
        waitFor { harness.container.settingsSnapshot.lockEnabled }
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
    @Test fun ohneSperreKeinFlagSecure() {
        harness(FakeAuth())
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }
}
