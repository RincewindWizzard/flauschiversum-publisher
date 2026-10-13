package de.flauschiversum.publisher.testsupport

import de.flauschiversum.publisher.crypto.CryptoProviders
import de.flauschiversum.publisher.git.AddressParse
import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.GitStore
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.RemoteOps
import de.flauschiversum.publisher.git.RepositoryAddress
import de.flauschiversum.publisher.git.RepositoryAddresses
import de.flauschiversum.publisher.git.Refs
import de.flauschiversum.publisher.git.SshRemote
import de.flauschiversum.publisher.ssh.AccessChecker
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import de.flauschiversum.publisher.ssh.HostKeyStore
import de.flauschiversum.publisher.ssh.SshKeyPair
import de.flauschiversum.publisher.ssh.SshKeyStore
import java.io.File
import org.eclipse.jgit.lib.PersonIdent

/**
 * Ein Test-Blog: SSH-Server mit einem Bare-Repository (mit den echten Beispielbeiträgen), dazu ein flacher Klon
 * der App mit [DraftWorkflow].
 */
class BlogFixture(val root: File, files: Map<String, String> = defaultFiles()) : AutoCloseable {
    val server: TestSshGitServer
    val bare: File
    val key: SshKeyPair
    val keyStore: SshKeyStore
    val hostKeys: HostKeyStore
    val address: RepositoryAddress
    val me = PersonIdent("Tester", "tester@example.org")
    lateinit var store: GitStore
    lateinit var ops: RemoteOps
    lateinit var workflow: DraftWorkflow
    val refs = Refs("main")

    init {
        TestGit.isolate(); CryptoProviders.install(); AppSshSessionFactory.configureJsch()
        server = TestSshGitServer(File(root, "repos"))
        bare = TestRepos.createBare(File(server.root, "blog.git"), files)
        keyStore = SshKeyStore(File(root, "ssh")); key = keyStore.generate("test")
        server.allowWrite(key.publicLine)
        hostKeys = HostKeyStore(File(root, "known_hosts"))
        address = (RepositoryAddresses.parse("ssh://git@127.0.0.1:${server.port}/blog.git") as AddressParse.Valid).address
        val checker = AccessChecker(keyStore, hostKeys, NetworkState { true })
        checker.trust(checker.probeHostKey(address)!!)
    }

    /** Legt einen frischen flachen Klon an (Verzeichnis [name]) und baut den Workflow darauf. */
    fun clone(name: String = "klon.git"): DraftWorkflow {
        if (this::store.isInitialized) store.close()
        store = GitStore(File(root, name))
        ops = RemoteOps(store, SshRemote(address, AppSshSessionFactory(key, hostKeys)))
        ops.cloneShallow(refs.branches.filter { it == "main" } + remoteDraftIfAny())
        workflow = DraftWorkflow(store, refs, { ops }, { me })
        workflow.adoptRemoteDraft()
        return workflow
    }

    private fun remoteDraftIfAny(): List<String> =
        if (org.eclipse.jgit.api.Git.open(bare).use { it.repository.exactRef("refs/heads/draft") != null }) listOf("draft") else emptyList()

    fun serverRef(name: String): String? =
        org.eclipse.jgit.api.Git.open(bare).use { it.repository.exactRef(name)?.objectId?.name }

    fun serverFile(rev: String, path: String): String? =
        org.eclipse.jgit.api.Git.open(bare).use { g ->
            val s = GitStore(bare); try { s.readText(rev, path) } finally { /* nicht schließen: gehört dem Git-Objekt */ }
        }

    override fun close() {
        if (this::store.isInitialized) store.close()
        server.close()
    }

    companion object {
        fun defaultFiles(): Map<String, String> =
            Fixtures.posts().associate { "content/posts/$it/index.md" to Fixtures.post(it) } +
                mapOf("content/tags.yaml" to Fixtures.tagsYaml(), "content/redirects.yaml" to Fixtures.redirectsYaml())
    }
}
