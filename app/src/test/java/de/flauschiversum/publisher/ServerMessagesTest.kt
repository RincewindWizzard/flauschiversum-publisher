package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.PublishResult
import de.flauschiversum.publisher.git.ServerMessages
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.testsupport.BlogFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Meldungen des Servers beim Push (z. B. ein fehlgeschlagenes Deployment) kommen bei der App an. */
class ServerMessagesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var blog: BlogFixture
    @Before fun setUp() { blog = BlogFixture(tmp.newFolder("blog")); blog.clone() }
    @After fun tearDown() { blog.close() }

    private val failed = listOf(
        "==> Deployment von a834e9c6ed (refs/heads/main) als Benutzer flauschiversum",
        "==> Export von a834e9c6ed aus /media/git/nadja/flauschiversum",
        "    Finished `release` profile [optimized] target(s) in 0.23s",
        "[   0.0 s] Lese Inhalte (Beiträge, Seiten, Schlagworte) …",
        "Fehler: content/redirects.yaml: Weiterleitung `/2026/10/katze/` → `/`: Adressen müssen mit `/` beginnen und enden",
        "Fehler: Inhalte enthalten Fehler",
        "Fehler: Der Build ist fehlgeschlagen, es wird nichts übertragen.",
        "==> Deployment FEHLGESCHLAGEN. Die Seite auf dem Server ist unverändert oder auf dem vorigen Stand.",
    )

    @Test fun fehlerAusDemDeployHookWirdErkanntUndZusammengefasst() {
        val m = ServerMessages(failed.joinToString("\n") { "remote: $it" })
        assertTrue(m.hasProblem)
        assertTrue(m.summary, m.summary.contains("content/redirects.yaml: Weiterleitung `/2026/10/katze/`"))
        assertTrue(m.summary.contains("Der Build ist fehlgeschlagen"))
        assertFalse("Fortschritt gehört nicht in die Kurzfassung: ${m.summary}", m.summary.contains("Export von"))
        assertTrue(m.full.contains("Export von"))
        assertFalse(m.full.contains("remote:"))
    }

    @Test fun erfolgsmeldungenSindKeinFehler() {
        val m = ServerMessages("remote: ==> Deployment von a834e9c6ed als Benutzer x\nremote: ==> Deployment erfolgreich.")
        assertFalse(m.hasProblem); assertFalse(m.isEmpty)
        assertFalse(ServerMessages.from(null).hasProblem); assertTrue(ServerMessages.from("  ").isEmpty)
    }

    @Test fun veroeffentlichenMeldetDenFehlerDesServersObwohlDerPushGelang() {
        blog.server.receiveMessages = failed
        blog.workflow.commit(listOf(TreeChange.Put("content/posts/katze/index.md", "---\ntitle: \"Katze\"\ndate: 2026-10-13\n---\nText\n".toByteArray())), "Beitrag: Katze")
        val r = blog.workflow.publish()
        assertTrue("war: $r", r is PublishResult.Published)
        val sm = (r as PublishResult.Published).serverMessages
        assertTrue(sm.text, sm.hasProblem)
        assertTrue(sm.summary, sm.summary.contains("redirects.yaml"))
        // der Push selbst ist angekommen
        assertTrue(blog.serverFile("refs/heads/main", "content/posts/katze/index.md") != null)
    }

    @Test fun veroeffentlichenOhneMeldungenHatKeinenFund() {
        blog.workflow.commit(listOf(TreeChange.Put("content/posts/katze/index.md", "---\ntitle: \"Katze\"\ndate: 2026-10-13\n---\nText\n".toByteArray())), "Beitrag: Katze")
        val sm = (blog.workflow.publish() as PublishResult.Published).serverMessages
        assertTrue(sm.isEmpty); assertFalse(sm.hasProblem)
    }

    @Test fun hochladenDesEntwurfsMerktSichDieMeldungen() {
        blog.server.receiveMessages = listOf("Fehler: Hook kaputt")
        blog.workflow.commit(listOf(TreeChange.Put("content/posts/katze/index.md", "---\ntitle: \"Katze\"\ndate: 2026-10-13\n---\nText\n".toByteArray())), "Beitrag: Katze")
        blog.workflow.pushDraft()
        assertEquals("Fehler: Hook kaputt", blog.workflow.lastDraftMessages.summary)
    }
}
