package de.flauschiversum.publisher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.posts.TagCreation
import de.flauschiversum.publisher.posts.TagResult
import de.flauschiversum.publisher.posts.TagsFile
import de.flauschiversum.publisher.posts.Yaml
import de.flauschiversum.publisher.testsupport.AppTestHarness
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.Fixtures
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

class TagsFileTest {
    private val real = Fixtures.tagsYaml()
    private fun lines(s: String) = s.lines()

    @Test fun neueZeileWirdSortiertEingefuegtRestBleibtGleich() {
        val out = TagsFile.insert(real, "Makramee")
        val before = lines(real); val after = lines(out)
        assertEquals(before.size + 1, after.size)
        assertEquals(before.filter { it.isNotEmpty() }, after.filter { it != "- name: Makramee" && it.isNotEmpty() })
        val i = after.indexOf("- name: Makramee")
        assertTrue(after[i - 1].startsWith("- name: ") && TagsFile.compare(after[i - 1].removePrefix("- name: "), "Makramee") < 0)
        assertTrue(TagsFile.compare("Makramee", after[i + 1].removePrefix("- name: ")) < 0)
        assertEquals(real.length + "- name: Makramee\n".length, out.length)
        assertTrue(TagsFile.names(out).contains("Makramee"))
    }
    @Test fun umlauteSortierenUnterIhremGrundbuchstaben() {
        val out = TagsFile.insert("- name: Apfel\n- name: Zebra\n", "Äpfel")
        assertEquals("- name: Apfel\n- name: Äpfel\n- name: Zebra\n", out)
        assertEquals("- name: Apfel\n- name: Zebra\n- name: Zürich\n", TagsFile.insert("- name: Apfel\n- name: Zebra\n", "Zürich"))
    }
    @Test fun sonderzeichenWerdenAngefuehrtUndDieDateiBleibtGueltigesYaml() {
        for (n in listOf("Tipps: Wolle", "C# Tipps", "Ja", "123", "Mit \"Anführung\"")) {
            val out = TagsFile.insert(real, n)
            assertTrue(n, TagsFile.names(out).contains(n))
            val parsed = org.snakeyaml.engine.v2.api.Load(org.snakeyaml.engine.v2.api.LoadSettings.builder().setSchema(org.snakeyaml.engine.v2.schema.FailsafeSchema()).build()).loadFromString(out) as List<*>
            assertTrue(n, parsed.any { (it as Map<*, *>)["name"] == n })
        }
        assertTrue(TagsFile.insert(real, "Tipps: Wolle").contains("- name: \"Tipps: Wolle\""))
    }
    @Test fun kommentareUndZeilenendenBleiben() {
        val out = TagsFile.insert(real, "Makramee")
        assertTrue(out.startsWith(real.lineSequence().take(3).joinToString("\n")))
        assertEquals("# c\r\n- name: A\r\n- name: B\r\n- name: C\r\n", TagsFile.insert("# c\r\n- name: A\r\n- name: C\r\n", "B"))
        assertEquals("- name: A\n- name: B\n", TagsFile.insert("- name: A", "B"))
        assertEquals("- name: B\n", TagsFile.insert("", "B"))
    }
    @Test fun namenspruefung() {
        val existing = TagsFile.names(real)
        assertNull(TagsFile.validate("Makramee", existing))
        assertTrue(TagsFile.validate("   ", existing)!!.contains("Namen"))
        assertTrue(TagsFile.validate("x".repeat(41), existing)!!.contains("40"))
        assertNull(TagsFile.validate("x".repeat(40), existing))
        assertTrue(TagsFile.validate("a\nb", existing)!!.contains("Zeilenumbruch"))
        assertTrue(TagsFile.validate("hüte", existing)!!.contains("Hüte"))
        assertTrue(TagsFile.validate(" Hüte ", existing) != null)
    }
}

class TagCreationTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }
    private val wf get() = blog.workflow

    @Test fun commitEnthaeltNurTagsYamlUndMainBleibt() {
        val main = wf.targetId()
        assertEquals(TagResult.Created("Makramee"), TagCreation.create(wf, " Makramee "))
        assertEquals(main, wf.targetId())
        assertEquals(listOf("content/tags.yaml"), wf.ownChanges().map { it.path })
        assertEquals("Schlagwort: Makramee", wf.parseCommit(wf.draftId()!!).shortMessage)
        assertEquals(listOf("Makramee"), TagCreation.newTags(wf))
    }
    @Test fun vorhandenesSchlagwortWirdNichtDoppeltEingetragen() {
        assertEquals(TagResult.Existing("Hüte"), TagCreation.create(wf, "hüte"))
        assertFalse(wf.hasDraft())
    }
    @Test fun ungueltigerNameErzeugtKeinenCommit() {
        assertTrue(TagCreation.create(wf, "  ") is TagResult.Invalid)
        assertFalse(wf.hasDraft())
    }
    @Test fun beitragVerwerfenLaesstSchlagwortBestehenAlleVerwerfenNimmtEsZurueck() {
        TagCreation.create(wf, "Makramee")
        wf.commit(listOf(de.flauschiversum.publisher.git.TreeChange.Put("content/posts/neu/index.md", "---\ntitle: \"Neu\"\ndate: 2026-10-13\ntags: [Makramee]\n---\nText\n".toByteArray())), "Beitrag")
        wf.discard("neu")
        assertEquals(listOf("Makramee"), TagCreation.newTags(wf))
        wf.discardAllLocal()
        assertTrue(TagCreation.newTags(wf).isEmpty())
    }
    @Test fun serverhatDasSchlagwortInzwischenSelbstErgaenzt() {
        val withTag = TagsFile.insert(Fixtures.tagsYaml(), "Makramee")
        TestRepos.commit(blog.bare, "main", mapOf("content/tags.yaml" to withTag), "Server ergänzt Schlagwort")
        blog.ops.fetch(listOf("main"))
        assertEquals(TagResult.Existing("Makramee"), TagCreation.create(wf, "makramee"))
        assertFalse(wf.hasDraft())
    }
    @Test fun veroeffentlichenFuehrtSchlagwortUndBeitragZusammen() {
        TagCreation.create(wf, "Makramee")
        wf.commit(listOf(de.flauschiversum.publisher.git.TreeChange.Put("content/posts/neu/index.md", "---\ntitle: \"Neu\"\ndate: 2026-10-13\ntags: [Makramee]\n---\nText\n".toByteArray())), "Beitrag")
        wf.pushDraft()
        assertTrue(wf.publish() is de.flauschiversum.publisher.git.PublishResult.Published)
        val server = de.flauschiversum.publisher.git.GitStore(blog.bare)
        assertTrue(TagsFile.names(server.readText("refs/heads/main", "content/tags.yaml")!!).contains("Makramee"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagsUiTest {
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

    private fun properties(): EditorViewModel {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        val vm = EditorViewModel(harness.container, SavedStateHandle(), null)
        compose.setContent { FlauschiversumPublisherTheme { EditorScreen(vm, 1f, {}) } }
        waitFor { vm.ui.value.loaded }
        compose.onNodeWithTag("editor-menu").performClick(); compose.onNodeWithTag("open-properties").performClick()
        waitFor { exists("new-tag") }
        return vm
    }

    @Test fun neuesSchlagwortAnlegenWaehltEsSofortAus() {
        val vm = properties()
        compose.onNodeWithTag("new-tag").performScrollTo().performClick()
        compose.onNodeWithTag("new-tag-name").performTextInput("Makramee")
        compose.onNodeWithTag("new-tag-create").performClick()
        waitFor { vm.ui.value.tags == listOf("Makramee") }
        val wf = h!!.container.session.workflow()!!
        assertTrue(wf.store.readText(wf.refs.draft, "content/tags.yaml")!!.contains("- name: Makramee"))
        assertEquals(listOf("content/tags.yaml"), wf.ownChanges().map { it.path })
        waitFor { h!!.blog.serverRef("refs/heads/draft") != null }
    }

    @Test fun nurLeerzeichenSperrtAnlegenUndZeigtDenGrund() {
        properties()
        compose.onNodeWithTag("new-tag").performScrollTo().performClick()
        compose.onNodeWithTag("new-tag-name").performTextInput("   ")
        compose.onNodeWithTag("new-tag-create").assertIsNotEnabled()
        compose.onNodeWithText("Bitte einen Namen eingeben.", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun vorhandenesSchlagwortBietetAuswahlAn() {
        val vm = properties()
        compose.onNodeWithTag("new-tag").performScrollTo().performClick()
        compose.onNodeWithTag("new-tag-name").performTextInput("hüte")
        waitFor { exists("new-tag-use-existing") }
        compose.onNodeWithTag("new-tag-create").assertIsNotEnabled()
        compose.onNodeWithTag("new-tag-use-existing").performClick()
        waitFor { vm.ui.value.tags == listOf("Hüte") }
        assertFalse(h!!.container.session.workflow()!!.hasDraft())
    }

    @Test fun entwurfslisteUndAbfrageNennenDasNeueSchlagwort() {
        val harness = AppTestHarness(tmp.newFolder()).also { it.completeSetup(); h = it }
        TagCreation.create(harness.container.session.workflow()!!, "Makramee")
        val vm = HomeViewModel(harness.container)
        compose.setContent { FlauschiversumPublisherTheme { HomeScreen(vm, onOpenPost = {}, onSettings = {}, onFixAccess = {}) } }
        waitFor { exists("new-tag-Makramee") }
        compose.onNodeWithTag("publish").performClick()
        waitFor { exists("publish-new-tag") }
        compose.onNodeWithTag("publish-new-tag").assertIsDisplayed()
    }
}
