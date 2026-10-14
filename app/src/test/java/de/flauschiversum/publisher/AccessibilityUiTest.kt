package de.flauschiversum.publisher

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.outbox.OutboxScreen
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Barrierefreiheit der Hauptbildschirme: Mindestgröße 48 dp, Beschriftungen, Schrift bis 200 %. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityUiTest {
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
    private fun harness() = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }

    /** Jedes anklickbare Element: mindestens 48 × 48 dp und eine Beschriftung (Text oder Inhaltsbeschreibung). */
    private fun assertTouchTargetsAndLabels(screen: String) {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("$screen: keine Bedienelemente", nodes.isNotEmpty())
        val unlabeled = nodes.filter { n ->
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            val hasChildText = n.children.any { c -> c.config.getOrNull(SemanticsProperties.Text) != null }
            text.isBlank() && desc.isBlank() && !hasChildText && n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("post-") != true
        }
        assertTrue("$screen: Elemente ohne Beschriftung: ${unlabeled.map { it.config.getOrNull(SemanticsProperties.TestTag) }}", unlabeled.isEmpty())
        val density = compose.density
        val small = nodes.filter { n ->
            val w = with(density) { n.size.width.toDp() }; val hgt = with(density) { n.size.height.toDp() }
            w < 48.dp || hgt < 48.dp
        }.map { n -> (n.config.getOrNull(SemanticsProperties.TestTag) ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }) to "${n.size.width}x${n.size.height}" }
        assertTrue("$screen: Bedienelemente kleiner als 48 dp: $small", small.isEmpty())
    }

    @Test fun uebersicht() {
        val harness = harness(); val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { vm.ui.value.posts.isNotEmpty() }
        assertTouchTargetsAndLabels("Übersicht")
    }

    @Test fun editor() {
        val harness = harness(); val vm = EditorViewModel(harness.container, SavedStateHandle(), "baumaerkte")
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() }
        assertTouchTargetsAndLabels("Editor")
    }

    @Test fun eigenschaften() {
        val harness = harness(); val vm = EditorViewModel(harness.container, SavedStateHandle(), null, startWithProperties = true)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded }
        assertTouchTargetsAndLabels("Eigenschaften")
    }

    @Test fun einstellungen() {
        val harness = harness(); val vm = SettingsViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { SettingsScreen(vm, onBack = {}, onSetup = {}) } }
        compose.waitForIdle()
        assertTouchTargetsAndLabels("Einstellungen")
    }

    @Test fun ausgang() {
        val harness = harness()
        compose.setContent { FlauschiversumPublisherTheme { OutboxScreen(harness.container, onBack = {}) } }
        compose.waitForIdle()
        assertTouchTargetsAndLabels("Ausgang")
    }

    @Test fun pflichtfeldfehlerZeigtSymbolUndText() {
        val harness = harness(); val vm = EditorViewModel(harness.container, SavedStateHandle(), null)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded }
        compose.onNodeWithTag("save").performClick()
        waitFor { vm.ui.value.errors.isNotEmpty() && compose.onAllNodes(androidx.compose.ui.test.hasText("⚠ Bitte einen Titel eingeben.", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun schriftBis200ProzentBleibtBedienbar() {
        val harness = harness(); val vm = HomeViewModel(harness.container)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        }
        waitFor { vm.ui.value.posts.isNotEmpty() }
        compose.onNodeWithTag("new-post").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
        compose.onNodeWithTag("list").assertIsDisplayed()
    }
}
