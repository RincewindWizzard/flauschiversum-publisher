package de.flauschiversum.publisher

import de.flauschiversum.publisher.crypto.CryptoProviders
import de.flauschiversum.publisher.git.AddressParse
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.RepositoryAddress
import de.flauschiversum.publisher.git.RepositoryAddresses
import de.flauschiversum.publisher.ssh.AccessChecker
import de.flauschiversum.publisher.ssh.AccessResult
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import de.flauschiversum.publisher.ssh.HostKeyStore
import de.flauschiversum.publisher.ssh.OpenSshKeys
import de.flauschiversum.publisher.ssh.SshKeyStore
import de.flauschiversum.publisher.testsupport.TestGit
import de.flauschiversum.publisher.testsupport.TestRepos
import de.flauschiversum.publisher.testsupport.TestSshGitServer
import java.io.File
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

class SshAccessTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: TestSshGitServer
    private lateinit var keyStore: SshKeyStore
    private lateinit var hostKeys: HostKeyStore
    private var online = true
    private lateinit var checker: AccessChecker

    @Before fun setUp() {
        TestGit.isolate()
        CryptoProviders.install()
        AppSshSessionFactory.configureJsch()
        server = TestSshGitServer(tmp.newFolder("repos"))
        TestRepos.createBare(File(server.root, "blog.git"), mapOf("content/posts/a/index.md" to "---\ntitle: A\ndate: 2020-01-01\n---\nText\n"))
        keyStore = SshKeyStore(tmp.newFolder("ssh"))
        hostKeys = HostKeyStore(File(tmp.newFolder("hk"), "known_hosts"))
        checker = AccessChecker(keyStore, hostKeys, NetworkState { online })
    }

    @After fun tearDown() { server.close() }

    private fun address(repo: String = "blog.git", port: Int = server.port): RepositoryAddress =
        (RepositoryAddresses.parse("ssh://git@127.0.0.1:$port/$repo") as AddressParse.Valid).address

    private fun trustServer() {
        val info = checker.probeHostKey(address())
        assertNotNull(info)
        checker.trust(info!!)
    }

    // ---- 2.1 Schlüssel
    @Test fun schluesselWirdErzeugtGespeichertUndWiederGeladen() {
        val key = keyStore.generate("flauschiversum-publisher")
        assertTrue(key.publicLine.startsWith("ssh-ed25519 "))
        assertTrue(key.publicLine.endsWith(" flauschiversum-publisher"))
        assertTrue(key.privatePem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertTrue(key.fingerprint.startsWith("SHA256:"))
        val loaded = SshKeyStore(File(tmp.root, "ssh")).load()!!
        assertEquals(key.publicLine, loaded.publicLine)
        assertEquals(key.privatePem, loaded.privatePem)
        assertEquals(key.fingerprint, loaded.fingerprint)
    }

    @Test fun neuErzeugenErsetztDenAltenSchluessel() {
        val a = keyStore.generate("x")
        val b = keyStore.generate("x")
        assertFalse(a.publicLine == b.publicLine)
        assertEquals(b.publicLine, keyStore.load()!!.publicLine)
    }

    @Test fun fingerabdruckEntsprichtOpenSshFormat() {
        val key = OpenSshKeys.generate("c")
        // SHA256:-Präfix und 43 Zeichen Base64 ohne Auffüllung
        assertTrue(Regex("^SHA256:[A-Za-z0-9+/]{43}$").matches(key.fingerprint))
    }

    // ---- 2.3 Host-Schlüssel, 2.4 Verbindung testen
    @Test fun ohneSchluesselMeldetKeinSchluessel() {
        assertEquals(AccessResult.NoKey, checker.check(address()))
    }

    @Test fun erstkontaktFragtNachDemHostSchluessel() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        val r = checker.check(address())
        assertTrue("war: $r", r is AccessResult.Failed && r.failure is GitFailure.HostKeyUnknown)
    }

    @Test fun kurzformMitPortVerbindetSichAnDemAngegebenenPort() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        val a = (RepositoryAddresses.parse("git@127.0.0.1:${server.port}:blog.git") as AddressParse.Valid).address
        assertEquals(server.port, a.port)
        checker.trust(checker.probeHostKey(a)!!)
        assertEquals(AccessResult.Ok(writeConfirmed = false), checker.check(a))
        // ohne den Port in der Adresse wäre Port 22 gemeint und niemand antwortet
        val ohnePort = (RepositoryAddresses.parse("git@127.0.0.1:blog.git") as AddressParse.Valid).address
        assertEquals(22, ohnePort.port)
    }

    @Test fun nachBestaetigungGeltenderServerAlsBekannt() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        trustServer()
        val r = checker.check(address())
        assertEquals(AccessResult.Ok(writeConfirmed = false), r)
        assertEquals(1, hostKeys.list().size)
    }

    @Test fun geaenderterHostSchluesselBrichtAb() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        // Für denselben Server ist ein anderer Schlüssel desselben Typs gespeichert
        val other = TestSshGitServer(tmp.newFolder("other")).use { it.hostPublicKey() }
        val fake = de.flauschiversum.publisher.ssh.HostKeyInfo(
            "127.0.0.1", server.port, de.flauschiversum.publisher.ssh.HostKeyType.ECDSA, sshBlob(other),
        )
        hostKeys.trust(fake)
        val r = checker.check(address())
        assertTrue("war: $r", r is AccessResult.Failed && r.failure is GitFailure.HostKeyChanged)
    }

    @Test fun loeschenErzwingtNeueBestaetigung() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        trustServer()
        hostKeys.remove("127.0.0.1", server.port)
        val r = checker.check(address())
        assertTrue(r is AccessResult.Failed && r.failure is GitFailure.HostKeyUnknown)
    }

    @Test fun nichtEingetragenerSchluesselWirdAbgelehnt() {
        keyStore.generate("t")
        trustServer()
        val r = checker.check(address())
        assertEquals(AccessResult.Failed(GitFailure.KeyRejected), r)
    }

    @Test fun nurLeserechtWirdErkannt() {
        val key = keyStore.generate("t"); server.allowReadOnly(key.publicLine)
        trustServer()
        val r = checker.check(address())
        assertEquals(AccessResult.Failed(GitFailure.ReadOnly), r)
    }

    @Test fun unbekanntesRepositoryWirdErkannt() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        trustServer()
        val r = checker.check(address("gibt-es-nicht.git"))
        assertEquals(AccessResult.Failed(GitFailure.RepositoryNotFound), r)
    }

    @Test fun nichtErreichbarerPortWirdErkannt() {
        keyStore.generate("t")
        val dead = address(port = 1)
        val r = checker.check(dead)
        assertEquals(AccessResult.Failed(GitFailure.Unreachable("127.0.0.1", 1)), r)
    }

    @Test fun ohneNetzMeldetKeinNetz() {
        keyStore.generate("t")
        online = false
        val r = checker.check(address(port = 1))
        assertEquals(AccessResult.Failed(GitFailure.NoNetwork), r)
    }

    @Test fun pruefungVeraendertDasRepositoryNicht() {
        val key = keyStore.generate("t"); server.allowWrite(key.publicLine)
        trustServer()
        val before = refs(File(server.root, "blog.git"))
        checker.check(address())
        assertEquals(before, refs(File(server.root, "blog.git")))
    }

    @Test fun probeHostKeyLiefertNullBeiToterGegenstelle() {
        keyStore.generate("t")
        assertNull(checker.probeHostKey(address(port = 1)))
    }

    private fun refs(bare: File): Map<String, String> =
        org.eclipse.jgit.api.Git.open(bare).use { g -> g.repository.refDatabase.refs.associate { it.name to it.objectId.name } }

    private fun sshBlob(key: java.security.PublicKey): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        org.apache.sshd.common.config.keys.KeyUtils.getKeyType(key)
        org.apache.sshd.common.config.keys.PublicKeyEntry.toString(key).let { line ->
            return java.util.Base64.getDecoder().decode(line.split(' ')[1])
        }
    }
}
