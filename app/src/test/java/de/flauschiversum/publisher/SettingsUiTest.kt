package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import de.flauschiversum.publisher.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
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
class SettingsUiTest {
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

    private fun screen(): AppTestHarness {
        val h = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); this.h = it }
        val vm = SettingsViewModel(h.container)
        compose.setContent { FlauschiversumPublisherTheme { SettingsScreen(vm, onBack = {}, onSetup = {}) } }
        return h
    }

    @Test fun persoenlichesWoerterbuchWortHinzufuegenUndEntfernen() {
        val h = screen()
        compose.onNodeWithTag("dict-new").performScrollTo().performTextInput("Zzyxwolle")
        compose.onNodeWithTag("dict-add").performScrollTo().performClick()
        waitFor { exists("dict-Zzyxwolle") }
        assertEquals(listOf("Zzyxwolle"), h.container.spell.personal.all())
        compose.onNodeWithTag("dict-remove-Zzyxwolle").performScrollTo().performClick()
        waitFor { !exists("dict-Zzyxwolle") }
        assertTrue(h.container.spell.personal.all().isEmpty())
    }

    @Test fun vorgabenWerdenAngezeigt() {
        val h = screen()
        compose.onNodeWithTag("settings-address").assertIsDisplayed()
        compose.onNodeWithTag("settings-branch").performScrollTo().assertIsDisplayed()
        assertEquals("Flauschiversum / Ermeline Wollknoll", h.container.settingsSnapshot.rightsText)
    }

    @Test fun aenderungenWerdenGespeichertUndBleibenErhalten() {
        val h = screen()
        compose.onNodeWithTag("settings-commit-name").performScrollTo().performTextReplacement("Ermeline Wollknoll")
        compose.onNodeWithTag("settings-rights").performScrollTo().performTextReplacement("Mein Blog")
        waitFor { h.container.settingsSnapshot.commitName == "Ermeline Wollknoll" && h.container.settingsSnapshot.rightsText == "Mein Blog" }
        val persisted = runBlocking { h.container.settings.current() }
        assertEquals("Ermeline Wollknoll", persisted.commitName); assertEquals("Mein Blog", persisted.rightsText)
    }

    @Test fun darstellungUndHochladenSchalter() {
        val h = screen()
        compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        waitFor { h.container.settingsSnapshot.themeMode == ThemeMode.DARK }
        compose.onNodeWithTag("wifi-only").performScrollTo().performClick()
        waitFor { h.container.settingsSnapshot.uploadWifiOnly }
    }

    @Test fun bekannteServerWerdenGelistetUndKoennenZurueckgesetztWerden() {
        val h = screen()
        val id = "[127.0.0.1]:${h.blog.server.port}"
        assertTrue(h.container.hostKeys.list().any { it.hostId == id })
        compose.onNodeWithTag("hostkey-$id").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zurücksetzen …").performScrollTo().performClick()
        compose.onNodeWithText("Zurücksetzen", substring = false).performClick()
        waitFor { h.container.hostKeys.list().isEmpty() }
        assertFalse(exists("hostkey-$id"))
        // Danach fragt die App beim nächsten Kontakt wieder nach
        val r = h.container.accessChecker.check(h.blog.address)
        assertTrue("war: $r", r is de.flauschiversum.publisher.ssh.AccessResult.Failed && r.failure is de.flauschiversum.publisher.git.GitFailure.HostKeyUnknown)
    }

    @Test fun fingerabdruckUndVersionSindSichtbar() {
        val h = screen()
        compose.onNodeWithText(h.container.keyStore.load()!!.fingerprint).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("about-version").performScrollTo().assertIsDisplayed()
        assertNull(null)
    }
}
