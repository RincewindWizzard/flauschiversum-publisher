package de.flauschiversum.publisher

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
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

/** Aktualisieren per Wischgeste (Spec `uebersicht-aktualisieren`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefreshGestureTest {
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
    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun home(minRefresh: Long = 500): HomeViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        val vm = HomeViewModel(harness.container, minRefresh)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { vm.ui.value.posts.isNotEmpty() }
        waitFor { !h!!.container.sync.state.value.running && h!!.container.sync.state.value.generation >= 1 } // erster Lauf beim Start
        return vm
    }
    private fun pull() { compose.onNodeWithTag("list").performTouchInput { swipeDown() } }

    @Test fun ziehenAmOberenRandAktualisiertUndHoltNeueBeitraege() {
        val vm = home()
        TestRepos.commit(h!!.blog.bare, "main", mapOf("content/posts/frisch/index.md" to "---\ntitle: \"Frisch\"\ndate: 2026-10-14\n---\nT\n"), "frisch")
        val before = h!!.container.sync.state.value.generation
        pull()
        waitFor { vm.ui.value.refreshing }
        waitFor { vm.ui.value.posts.any { it.summary.dir == "frisch" } }
        assertTrue(h!!.container.sync.state.value.generation > before)
        assertTrue(h!!.container.sync.state.value.lastSuccessMillis > 0)
    }

    @Test fun weiterUntenScrolltDieListeUndAktualisiertNicht() {
        val vm = home()
        val before = h!!.container.sync.state.value.generation
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("post-${vm.ui.value.posts.last().summary.dir}"))
        compose.waitForIdle()
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = centerY, endY = centerY + 150f, durationMillis = 200) }
        compose.waitForIdle(); Thread.sleep(600); ShadowLooper.idleMainLooper()
        assertFalse(vm.ui.value.refreshing)
        assertEquals(before, h!!.container.sync.state.value.generation)
    }

    @Test fun auchKeineTrefferLassenSichHerunterziehen() {
        val vm = home()
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("search")); compose.onNodeWithTag("search").performTextInput("xyzxyzxyz")
        waitFor { exists("no-results") }
        pull()
        waitFor { vm.ui.value.refreshing }
        waitFor { !vm.ui.value.refreshing }
    }

    @Test fun ladekreisBleibtBeiSchnellemHolenKurzSichtbar() {
        val vm = home(minRefresh = 1500)
        val t0 = System.currentTimeMillis()
        pull(); waitFor { vm.ui.value.refreshing }
        waitFor { !h!!.container.sync.state.value.running && h!!.container.sync.state.value.generation >= 2 }
        assertTrue("Ladekreis zu früh weg", vm.ui.value.refreshing || System.currentTimeMillis() - t0 >= 1500)
        waitFor { !vm.ui.value.refreshing }
        assertTrue(System.currentTimeMillis() - t0 >= 1400)
    }

    @Test fun laeuftSchonEinLaufStartetKeinZweiter() {
        val vm = home()
        val c = h!!.container
        waitFor { !c.sync.state.value.running && c.sync.state.value.generation >= 1 } // erst den Lauf nach dem Start abwarten
        h!!.blog.server.pause() // Lauf bleibt kurz hängen/scheitert, bleibt aber ein einzelner Lauf
        val gen = c.sync.state.value.generation
        c.requestSync(SyncReason.MANUAL)
        vm.refreshNow(); vm.refreshNow()
        waitFor { !vm.ui.value.refreshing }
        assertEquals(gen + 1, c.sync.state.value.generation) // genau ein Lauf, nicht drei
        h!!.blog.server.resume()
    }

    @Test fun keinNetzBehaeltDieListeUndZeigtDenHinweis() {
        val vm = home()
        val before = vm.ui.value.posts.map { it.summary.dir }
        h!!.blog.server.pause()
        h!!.container.network.delegate = object : de.flauschiversum.publisher.git.NetworkInfo { override fun isOnline() = false; override fun isMetered() = false }
        pull()
        waitFor { vm.ui.value.refreshing }
        waitFor { exists("offline") }
        waitFor { !vm.ui.value.refreshing }
        assertEquals(before, vm.ui.value.posts.map { it.summary.dir })
        h!!.blog.server.resume()
    }

    @Test fun bedienungshilfeBietetDieAktionAktualisierenAn() {
        val vm = home()
        val before = h!!.container.sync.state.value.generation
        val node = compose.onNodeWithTag("list").fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == "Aktualisieren" }
        compose.runOnUiThread { assertTrue(action.action()) }
        waitFor { h!!.container.sync.state.value.generation > before }
        waitFor { !vm.ui.value.refreshing }
    }

    @Test fun keinAktualisierenSymbolMehrInDerKopfleiste() {
        home()
        assertTrue(compose.onAllNodes(hasContentDescription("Aktualisieren")).fetchSemanticsNodes().isEmpty())
        assertFalse(exists("refresh"))
    }
}
