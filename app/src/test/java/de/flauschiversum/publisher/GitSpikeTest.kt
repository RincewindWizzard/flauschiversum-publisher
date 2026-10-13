package de.flauschiversum.publisher

import de.flauschiversum.publisher.crypto.CryptoProviders
import de.flauschiversum.publisher.git.AddressParse
import de.flauschiversum.publisher.git.GitStore
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.PushOutcome
import de.flauschiversum.publisher.git.RemoteOps
import de.flauschiversum.publisher.git.RepositoryAddresses
import de.flauschiversum.publisher.git.SshRemote
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.ssh.AccessChecker
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import de.flauschiversum.publisher.ssh.HostKeyStore
import de.flauschiversum.publisher.ssh.SshKeyStore
import de.flauschiversum.publisher.testsupport.TestGit
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.testsupport.TestSshGitServer
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Aufgabe 1.2: Verhalten von flachem Klon, Nachladen, Merge und Push über SSH (gegen einen lokalen SSH-Server). */
class GitSpikeTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: TestSshGitServer
    private lateinit var bare: File
    private lateinit var ops: RemoteOps
    private lateinit var store: GitStore
    private val me = PersonIdent("Tester", "tester@example.org")
    private lateinit var addressForTest: de.flauschiversum.publisher.git.RepositoryAddress
    private lateinit var factoryForTest: AppSshSessionFactory

    @Before fun setUp() {
        TestGit.isolate(); CryptoProviders.install(); AppSshSessionFactory.configureJsch()
        server = TestSshGitServer(tmp.newFolder("repos"))
        bare = TestRepos.createBare(File(server.root, "blog.git"), mapOf("content/posts/a/index.md" to "---\ntitle: A\n---\nA1\n", "content/tags.yaml" to "- name: X\n"))
        repeat(5) { TestRepos.commit(bare, "main", mapOf("content/posts/a/index.md" to "---\ntitle: A\n---\nA${it + 2}\n"), "A ${it + 2}") }
        val keys = SshKeyStore(tmp.newFolder("ssh")); val key = keys.generate("t"); server.allowWrite(key.publicLine)
        val hostKeys = HostKeyStore(File(tmp.newFolder("hk"), "known"))
        val address = (RepositoryAddresses.parse("ssh://git@127.0.0.1:${server.port}/blog.git") as AddressParse.Valid).address
        val checker = AccessChecker(keys, hostKeys, NetworkState { true })
        checker.trust(checker.probeHostKey(address)!!)
        addressForTest = address; factoryForTest = AppSshSessionFactory(key, hostKeys)
        store = GitStore(File(tmp.root, "klon.git"))
        ops = RemoteOps(store, SshRemote(address, factoryForTest))
    }

    @After fun tearDown() { store.close(); server.close() }

    private fun commitsOf(rev: String): Int = Git(store.repo).use { it.log().add(store.resolve(rev)).call().count() }

    @Test fun flacherKlonHatNurDenAktuellenStand() {
        ops.cloneShallow(listOf("main"))
        assertTrue(store.exists())
        assertEquals(1, commitsOf("refs/remotes/origin/main"))
        assertEquals("---\ntitle: A\n---\nA6\n", store.readText("refs/remotes/origin/main", "content/posts/a/index.md"))
        assertEquals("- name: X\n", store.readText("refs/remotes/origin/main", "content/tags.yaml"))
        assertEquals(listOf("a"), store.list("refs/remotes/origin/main", "content/posts").map { it.name })
    }

    @Test fun fetchBringtNeueCommitsOhneZuVertiefen() {
        ops.cloneShallow(listOf("main"))
        TestRepos.commit(bare, "main", mapOf("content/posts/b/index.md" to "B\n"), "B")
        ops.fetch(listOf("main"))
        assertEquals("B\n", store.readText("refs/remotes/origin/main", "content/posts/b/index.md"))
        assertTrue("Klon sollte flach bleiben", commitsOf("refs/remotes/origin/main") <= 2)
    }

    @Test fun commitAufDraftOhneArbeitsbaumUndPush() {
        ops.cloneShallow(listOf("main"))
        val main = store.resolve("refs/remotes/origin/main")!!
        val c = store.commit(
            "refs/remotes/origin/main",
            listOf(TreeChange.Put("content/posts/neu/index.md", "---\ntitle: Neu\n---\nText\n".toByteArray()), TreeChange.Put("content/posts/neu/bild.jpg", ByteArray(2048) { it.toByte() })),
            "Beitrag: Neu", me, "refs/heads/draft",
        )
        assertNotNull(c)
        assertEquals(main, store.parseCommit(c!!).getParent(0).id)
        // main unverändert, draft hat den neuen Beitrag
        assertNull(store.readText("refs/remotes/origin/main", "content/posts/neu/index.md"))
        assertEquals("---\ntitle: Neu\n---\nText\n", store.readText("refs/heads/draft", "content/posts/neu/index.md"))
        val outcome = ops.push(mapOf("draft" to c))
        assertEquals(PushOutcome.Ok, outcome)
        Git.open(bare).use { g -> assertEquals(c, g.repository.resolve("refs/heads/draft")) }
        // Zweiter Commit setzt auf dem ersten auf
        val c2 = store.commit("refs/heads/draft", listOf(TreeChange.Put("content/posts/neu/index.md", "neu2".toByteArray())), "Text", me, "refs/heads/draft")!!
        assertEquals(c, store.parseCommit(c2).getParent(0).id)
        assertEquals(PushOutcome.Ok, ops.push(mapOf("draft" to c2)))
    }

    @Test fun unveraenderterBaumErzeugtKeinenCommit() {
        ops.cloneShallow(listOf("main"))
        val same = store.commit("refs/remotes/origin/main", listOf(TreeChange.Put("content/tags.yaml", "- name: X\n".toByteArray())), "x", me, "refs/heads/draft")
        assertNull(same)
        assertNull(store.refId("refs/heads/draft"))
    }

    @Test fun fetchVerbindetDieHistorieBisZurFlachenGrenze() {
        ops.cloneShallow(listOf("main"))
        val base = store.resolve("refs/remotes/origin/main")!!
        val draft = store.commit("refs/remotes/origin/main", listOf(TreeChange.Put("content/posts/neu/index.md", "N".toByteArray())), "Neu", me, "refs/heads/draft")!!
        repeat(30) { TestRepos.commit(bare, "main", mapOf("content/posts/x$it/index.md" to "x$it"), "x$it") }
        ops.fetch(listOf("main"))
        val newMain = store.resolve("refs/remotes/origin/main")!!
        // Das Holen liefert die Commits bis zur bisherigen flachen Grenze, daher gibt es einen gemeinsamen Vorfahren.
        assertTrue(ops.hasMergeBase(draft, newMain))
        val merger = org.eclipse.jgit.merge.MergeStrategy.RECURSIVE.newMerger(store.repo, true)
        assertTrue(merger.merge(newMain, draft))
        assertNotNull(merger.resultTreeId)
        assertEquals(base, store.parseCommit(draft).getParent(0).id)
    }

    @Test fun neuerKlonMitDraftVonAltemStandBrauchtNachladen() {
        // Auf dem Server liegt ein draft, der auf altem main aufbaut; main ist inzwischen weit vorausgelaufen.
        ops.cloneShallow(listOf("main"))
        val draft = store.commit("refs/remotes/origin/main", listOf(TreeChange.Put("content/posts/neu/index.md", "N".toByteArray())), "Neu", me, "refs/heads/draft")!!
        assertEquals(PushOutcome.Ok, ops.push(mapOf("draft" to draft)))
        repeat(30) { TestRepos.commit(bare, "main", mapOf("content/posts/x$it/index.md" to "x$it"), "x$it") }
        // Neues Gerät: flacher Klon beider Branches (Tiefe 1)
        store.close(); store.dir.deleteRecursively()
        store = GitStore(File(tmp.root, "klon2.git"))
        ops = RemoteOps(store, SshRemote(addressForTest, factoryForTest))
        ops.cloneShallow(listOf("main", "draft"))
        val d = store.resolve("refs/remotes/origin/draft")!!
        val m = store.resolve("refs/remotes/origin/main")!!
        assertFalse("im flachen Klon fehlt der gemeinsame Vorfahre", ops.hasMergeBase(d, m))
        assertTrue(ops.deepenUntilMergeBase(d, m, listOf("main", "draft")))
        val merger = org.eclipse.jgit.merge.MergeStrategy.RECURSIVE.newMerger(store.repo, true)
        assertTrue(merger.merge(m, d))
    }

    @Test fun abgelehnterPushWirdGemeldet() {
        ops.cloneShallow(listOf("main"))
        val c = store.commit("refs/remotes/origin/main", listOf(TreeChange.Put("content/posts/neu/index.md", "N".toByteArray())), "Neu", me, "refs/heads/draft")!!
        server.rejectReceivePack = true
        val failed = runCatching { ops.push(mapOf("draft" to c)) }
        assertTrue("Push muss fehlschlagen oder abgelehnt werden: $failed", failed.isFailure || failed.getOrNull() is PushOutcome.Rejected)
        // Der Commit bleibt lokal bestehen
        assertEquals(c, store.refId("refs/heads/draft"))
    }
}
