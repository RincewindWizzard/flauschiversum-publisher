package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import de.flauschiversum.publisher.setup.SetupStep
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.setup.SetupScreen
import de.flauschiversum.publisher.ui.setup.SetupViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import java.util.concurrent.atomic.AtomicBoolean
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }

    private fun harness() = AppTestHarness(tmp.newFolder()).also { h = it }

    private fun start(vm: SetupViewModel, finished: AtomicBoolean = AtomicBoolean(false)) {
        compose.setContent { FlauschiversumPublisherTheme { SetupScreen(vm, onFinished = { finished.set(true) }) } }
    }

    private fun waitFor(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Zeitüberschreitung nach $timeoutMs ms")
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }
    private fun exists(tag: String) = compose.onAllNodesWithTagSafe(tag)
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun ersterStartZeigtSchrittEinsMitVorgabeAdresse() {
        val h = harness(); start(SetupViewModel(h.container))
        compose.onNodeWithTag("setup-step").assertIsDisplayed()
        compose.onNodeWithText("Schritt 1 von 5: Repository").assertIsDisplayed()
        compose.onNodeWithText("git@github.com:RincewindWizzard/flauschiversum.git").assertIsDisplayed()
    }

    @Test fun httpsAdresseWirdErkanntUndSshVorgeschlagen() {
        val h = harness(); start(SetupViewModel(h.container))
        compose.onNodeWithTag("address-input").performTextClearance()
        compose.onNodeWithTag("address-input").performTextInput("https://github.com/RincewindWizzard/flauschiversum")
        compose.onNodeWithTag("address-next").performClick()
        waitFor { exists("address-message") }
        compose.onNodeWithText("SSH-Adresse übernehmen").assertIsDisplayed()
    }

    @Test fun ungueltigeAdresseZeigtBeispiele() {
        val h = harness(); start(SetupViewModel(h.container))
        compose.onNodeWithTag("address-input").performTextClearance()
        compose.onNodeWithTag("address-input").performTextInput("flauschiversum")
        compose.onNodeWithTag("address-next").performClick()
        waitFor { exists("address-message") }
        compose.onNodeWithTag("address-message").assertIsDisplayed()
    }

    @Test fun kompletterAblaufBisZurUebersicht() {
        val h = harness(); val finished = AtomicBoolean(false)
        val vm = SetupViewModel(h.container)
        start(vm, finished)
        // Schritt 1: Adresse des eigenen Servers
        compose.onNodeWithTag("address-input").performTextClearance()
        compose.onNodeWithTag("address-input").performTextInput(h.address)
        compose.onNodeWithTag("address-next").performClick()
        // Schritt 2: Schlüssel erzeugen
        waitFor { exists("generate-key") }
        compose.onNodeWithTag("generate-key").performClick()
        // Schritt 3: Anleitung für eigenen Server, Schlüssel dem Test-Server bekannt machen
        waitFor { exists("register-next") }
        compose.onNodeWithTag("plan-server").assertIsDisplayed()
        val publicLine = h.container.keyStore.load()!!.publicLine
        assertEquals(publicLine, h.container.keyStore.load()!!.publicLine)
        h.blog.server.allowWrite(publicLine)
        compose.onNodeWithTag("register-next").performScrollTo().performClick()
        // Schritt 4: Verbindung prüfen, Host-Schlüssel bestätigen
        waitFor { exists("run-check") }
        compose.onNodeWithTag("run-check").performClick()
        waitFor { exists("trust-host") }
        compose.onNodeWithTag("trust-host").performClick()
        try { waitFor { exists("check-ok") } } catch (e: Throwable) { throw AssertionError("Prüfung ohne Ergebnis, Zustand: ${vm.ui.value.check}", e) }
        compose.onNodeWithTag("check-write-unverified").assertIsDisplayed()
        compose.onNodeWithTag("check-next").performScrollTo().performClick()
        // Schritt 5: Klon
        waitFor { exists("start-clone") }
        compose.onNodeWithTag("start-clone").performScrollTo().performClick()
        try { waitFor(60_000) { finished.get() } } catch (e: Throwable) { throw AssertionError("Klon nicht fertig, Zustand: ${vm.ui.value.clone} step=${vm.ui.value.shown} finished=${vm.ui.value.finished} facts=${vm.ui.value.facts} persisted=${kotlinx.coroutines.runBlocking { h.container.setupState.current() }} sync=${h.container.sync.state.value}", e) }
        assertTrue(h.container.session.isInstalled())
        assertTrue(h.container.session.store()!!.readText("refs/remotes/origin/main", "content/tags.yaml") != null)
    }

    @Test fun schluesselNichtEingetragenZeigtFehlerUndWegZumEintragen() {
        val h = harness(); h.container.settings.let { kotlinx.coroutines.runBlocking { it.update { s -> s.copy(repositoryAddress = h.address) } } }
        kotlinx.coroutines.runBlocking {
            h.container.setupState.update { it.copy(confirmedAddress = h.address) }
            h.container.keyStore.generate("x")
            val id = "${h.container.keyStore.load()!!.fingerprint}@${h.address}"
            h.container.setupState.update { it.copy(registeredFor = id) }
        }
        start(SetupViewModel(h.container))
        compose.onNodeWithText("Schritt 4 von 5: Verbindung prüfen").assertIsDisplayed()
        compose.onNodeWithTag("run-check").performClick()
        waitFor { exists("trust-host") }
        compose.onNodeWithTag("trust-host").performClick()
        waitFor { exists("check-error") }
        compose.onNodeWithTag("check-error").assertIsDisplayed()
        compose.onNodeWithText("Zum Eintragen").assertIsDisplayed()
    }

    @Test fun mobilfunkFragtNachBevorGeklontWird() {
        val h = harness(); h.metered = true; h.completeSetupUntilClone()
        start(SetupViewModel(h.container))
        compose.onNodeWithText("Schritt 5 von 5: Repository holen").assertIsDisplayed()
        compose.onNodeWithTag("start-clone").performScrollTo().performClick()
        waitFor { exists("confirm-mobile") }
        assertFalse(h.container.session.isInstalled())
    }

    @Test fun zuWenigSpeicherMeldetDenPlatzmangelUndKlontNicht() {
        val h = harness(); h.completeSetupUntilClone(); h.container.freeSpace = { 100_000_000L }
        start(SetupViewModel(h.container))
        compose.onNodeWithTag("start-clone").performScrollTo().performClick()
        waitFor { exists("clone-problem") }
        compose.onNodeWithTag("clone-problem").assertIsDisplayed()
        assertFalse(h.container.session.dir.exists())
    }

    @Test fun neustartNachSchrittDreiBeginntBeiSchrittDrei() {
        val h = harness()
        kotlinx.coroutines.runBlocking {
            h.container.settings.update { it.copy(repositoryAddress = h.address) }
            h.container.setupState.update { it.copy(confirmedAddress = h.address) }
            h.container.keyStore.generate("x")
        }
        start(SetupViewModel(h.container))
        compose.onNodeWithText("Schritt 3 von 5: Schlüssel eintragen").assertIsDisplayed()
    }

    @Test fun unterbrochenerKlonWirdBeimStartEntfernt() {
        val h = harness(); h.completeSetupUntilClone()
        h.container.session.dir.mkdirs(); java.io.File(h.container.session.dir, "halb").writeText("x")
        start(SetupViewModel(h.container))
        waitFor { !h.container.session.dir.exists() }
        compose.onNodeWithText("Schritt 5 von 5: Repository holen").assertIsDisplayed()
        assertEquals(SetupStep.CLONE.number, 5)
    }
}

/** Setzt alle Schritte bis vor das Klonen. */
fun AppTestHarness.completeSetupUntilClone() = kotlinx.coroutines.runBlocking {
    val c = container
    c.settings.update { it.copy(repositoryAddress = address) }
    c.keyStore.save(blog.key)
    c.accessChecker.trust(c.accessChecker.probeHostKey(blog.address)!!)
    val id = "${blog.key.fingerprint}@$address"
    c.setupState.update { it.copy(confirmedAddress = address, registeredFor = id, accessVerifiedFor = id) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessProblemUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }

    @Test fun zugangsproblemFuehrtZuSchrittDreiMitSchluesselAnzeige() {
        val h = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); this.h = it }
        var navigated = false
        val vm = de.flauschiversum.publisher.ui.home.HomeViewModel(h.container)
        vm.fixAccess { navigated = true }
        val end = System.currentTimeMillis() + 10_000
        while (!navigated && System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20) }
        assertTrue(navigated)
        val setup = SetupViewModel(h.container)
        compose.setContent { FlauschiversumPublisherTheme { SetupScreen(setup, onFinished = {}) } }
        compose.onNodeWithText("Schritt 3 von 5: Schlüssel eintragen").assertIsDisplayed()
        compose.onNodeWithTag("public-key").assertIsDisplayed()
    }
}
