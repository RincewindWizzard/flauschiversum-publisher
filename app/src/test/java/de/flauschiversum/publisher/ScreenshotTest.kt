package de.flauschiversum.publisher

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.outbox.OutboxScreen
import de.flauschiversum.publisher.ui.photos.PhotosScreen
import de.flauschiversum.publisher.ui.photos.PhotosViewModel
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.setup.SetupScreen
import de.flauschiversum.publisher.ui.setup.SetupViewModel
import de.flauschiversum.publisher.ui.share.ShareScreen
import de.flauschiversum.publisher.ui.share.ShareViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import de.flauschiversum.publisher.ui.theme.ThemeMode
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Erzeugt Bildschirmfotos der App (nur wenn SCREENSHOT_DIR gesetzt ist). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()
    private var h: AppTestHarness? = null
    @After fun tearDown() { h?.close() }
    private val out: File? = System.getenv("SCREENSHOT_DIR")?.let { File(it).also { d -> d.mkdirs() } }

    private fun waitFor(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!cond()) { check(System.currentTimeMillis() < end) { "Zeitüberschreitung" }; ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(50); Thread.sleep(20) }
    }
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun harness(files: Map<String, String> = de.flauschiversum.publisher.testsupport.BlogFixture.defaultFiles()) = AppTestHarness(tmp.newFolder(), files).also { it.completeSetup(); h = it }
    private fun shot(name: String) {
        val dir = out ?: return
        Thread.sleep(300); ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(500)
        val view = compose.activity.window.decorView
        val w = view.width.takeIf { it > 0 } ?: 1080; val hgt = view.height.takeIf { it > 0 } ?: 2220
        val bmp = android.graphics.Bitmap.createBitmap(w, hgt, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun show(mode: ThemeMode = ThemeMode.LIGHT, content: @androidx.compose.runtime.Composable () -> Unit) { compose.mainClock.autoAdvance = false; compose.setContent { FlauschiversumPublisherTheme(mode) { content() } } }

    @Test fun uebersicht() {
        val harness = harness(); val vm = HomeViewModel(harness.container)
        show { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) }
        waitFor { vm.ui.value.posts.isNotEmpty() }
        shot("01-uebersicht")
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("search")); compose.onNodeWithTag("search").performTextInput("Filz")
        waitFor { vm.ui.value.query.text == "Filz" }; shot("02-suche")
    }

    @Test fun uebersichtMitEntwuerfenDunkel() {
        val harness = harness(); val vm = HomeViewModel(harness.container)
        harness.container.session.workflow()!!.commit(listOf(de.flauschiversum.publisher.git.TreeChange.Put("content/posts/muetze/index.md", "---\ntitle: \"Warme Mütze\"\ndate: 2026-10-13\ntags: [Hüte]\n---\nText\n".toByteArray())), "x")
        show(ThemeMode.DARK) { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) }
        waitFor { vm.ui.value.drafts.isNotEmpty() && vm.ui.value.posts.isNotEmpty() }; shot("03-entwuerfe-dunkel")
        compose.onNodeWithTag("publish").performClick(); waitFor { exists("publish-confirm") }; shot("04-veroeffentlichen")
    }

    @Test fun editor() {
        val harness = harness(); val vm = EditorViewModel(harness.container, SavedStateHandle(), "winter-is-coming")
        show { EditorScreen(vm, 1f, {}) }
        waitFor { vm.ui.value.loaded && vm.ui.value.body.isNotEmpty() && vm.ui.value.analyzedText != null }
        shot("05-editor")
    }

    @Test fun eigenschaften() {
        val harness = harness(); val vm = EditorViewModel(harness.container, SavedStateHandle(), "baumaerkte", startWithProperties = true)
        show { EditorScreen(vm, 1f, {}) }
        waitFor { vm.ui.value.loaded && vm.ui.value.title.isNotEmpty() }; shot("07-eigenschaften")
    }

    @Test fun fotos() {
        val files = de.flauschiversum.publisher.testsupport.BlogFixture.defaultFiles() + mapOf("content/posts/fotos/index.md" to "---\ntitle: \"Mein Fotobeitrag\"\ndate: 2026-10-13\nimage: a.jpg\n---\nText\n\n## Anhänge\n\n![Die Mütze](a.jpg)\n![Detail](b.jpg)\n", "content/posts/fotos/a.jpg" to "x", "content/posts/fotos/b.jpg" to "x", "content/posts/fotos/c.jpg" to "x")
        val harness = harness(files); val vm = PhotosViewModel(harness.container, "fotos")
        show { PhotosScreen(vm, onBack = {}) }
        waitFor { vm.ui.value.photos.size == 3 }; shot("08-fotos")
    }

    @Test fun teilen() {
        val harness = harness()
        harness.container.incoming.pending.value = listOf("IMG_1.jpg", "IMG_2.jpg").map { SharedPhoto(tmp.newFile(), it, "image/jpeg") }
        val vm = ShareViewModel(harness.container)
        show { ShareScreen(vm, onClose = {}) }
        waitFor { vm.ui.value.posts.isNotEmpty() }; shot("09-teilen")
    }

    @Test fun ausgang() {
        val harness = harness(); harness.blog.server.revokeAll()
        harness.container.uploads.submit(listOf(SharedPhoto(tmp.newFile().also { it.writeText("d") }, "a.jpg", "image/jpeg")), listOf("Alt"), PhotoTarget.Existing("baumaerkte"))
        show { OutboxScreen(harness.container, onBack = {}) }
        waitFor { exists("outbox-list") }; shot("10-ausgang")
    }

    @Test fun einstellungen() {
        val harness = harness(); val vm = SettingsViewModel(harness.container)
        show { SettingsScreen(vm, onBack = {}, onSetup = {}) }
        compose.mainClock.advanceTimeBy(200); shot("11-einstellungen")
    }

    @Test fun einrichtung() {
        val harness = AppTestHarness(tmp.newFolder()).also { h = it }
        val vm = SetupViewModel(harness.container)
        show { SetupScreen(vm, onFinished = {}) }
        compose.mainClock.advanceTimeBy(200); shot("12-einrichtung")
    }
}
