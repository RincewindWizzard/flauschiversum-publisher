package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.GitStore
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.Refs
import de.flauschiversum.publisher.git.RemoteOps
import de.flauschiversum.publisher.git.SshRemote
import de.flauschiversum.publisher.setup.CloneProgress
import de.flauschiversum.publisher.setup.InstallOutcome
import de.flauschiversum.publisher.setup.RepositoryInstaller
import de.flauschiversum.publisher.setup.SetupFacts
import de.flauschiversum.publisher.setup.SetupFlow
import de.flauschiversum.publisher.setup.SetupStep
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import de.flauschiversum.publisher.testsupport.BlogFixture
import de.flauschiversum.publisher.testsupport.TestRepos
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetupTest {
    @get:Rule val tmp = TemporaryFolder()
    private var blog: BlogFixture? = null
    @After fun tearDown() { blog?.close() }

    // ---- 3b.1 Zustandsautomat
    private val addr = "git@github.com:a/b.git"
    private fun facts(
        confirmed: String? = addr, address: String = addr, key: String? = "SHA256:abc", registered: String? = "SHA256:abc@$addr",
        access: String? = "SHA256:abc@$addr", clone: String? = "$addr#main", branch: String = "main", dir: Boolean = true,
    ) = SetupFacts(confirmed, address, key, registered, access, clone, branch, dir)

    @Test fun ersterStartBeginntBeiSchrittEins() {
        val f = facts(confirmed = null, key = null, registered = null, access = null, clone = null, dir = false)
        assertEquals(SetupStep.REPOSITORY, SetupFlow.currentStep(f))
    }

    @Test fun neustartNachSchrittDreiStartetBeiSchrittDrei() {
        val f = facts(registered = null, access = null, clone = null, dir = false)
        assertEquals(SetupStep.REGISTER, SetupFlow.currentStep(f))
    }

    @Test fun schrittKannNichtUebersprungenWerden() {
        val f = facts(key = null, registered = null, access = null, clone = null, dir = false)
        assertEquals(SetupStep.KEY, SetupFlow.currentStep(f))
        assertFalse(SetupFlow.canOpen(SetupStep.REGISTER, f))
        assertTrue(SetupFlow.canOpen(SetupStep.REPOSITORY, f)); assertTrue(SetupFlow.canOpen(SetupStep.KEY, f))
    }

    @Test fun abgeschlossenHatKeinenOffenenSchritt() {
        assertNull(SetupFlow.currentStep(facts()))
        assertTrue(SetupFlow.canOpen(SetupStep.CHECK, facts()))
    }

    @Test fun andereAdresseOeffnetDieFolgeschritteWieder() {
        val f = facts(address = "git@vps.example:blog.git")
        assertEquals(SetupStep.REPOSITORY, SetupFlow.currentStep(f))
        val confirmed = f.copy(confirmedAddress = "git@vps.example:blog.git")
        assertEquals(SetupStep.REGISTER, SetupFlow.currentStep(confirmed)) // Schlüssel bleibt, Eintrag/Prüfung/Klon gelten für die alte Adresse
    }

    @Test fun neuerSchluesselOeffnetEintragUndPruefungWieder() {
        val f = facts(key = "SHA256:neu")
        assertEquals(SetupStep.REGISTER, SetupFlow.currentStep(f))
    }

    @Test fun anderesZielbranchOeffnetNurDenKlonSchritt() {
        assertEquals(SetupStep.CLONE, SetupFlow.currentStep(facts(branch = "dev")))
    }

    @Test fun unterbrochenerKlonMussEntferntWerden() {
        assertTrue(SetupFlow.needsCloneCleanup(facts(clone = null, dir = true)))
        assertFalse(SetupFlow.needsCloneCleanup(facts()))
        assertFalse(SetupFlow.needsCloneCleanup(facts(clone = null, dir = false)))
    }

    // ---- 3.2 / 3b.5 / 3b.6 Klon
    private fun installer(b: BlogFixture, branch: String = "main", free: Long = 10_000_000_000L, dir: String = "install.git"): Pair<RepositoryInstaller, File> {
        val d = File(b.root, dir)
        val refs = Refs(branch)
        val inst = RepositoryInstaller(d, b.address, refs, { store ->
            RemoteOps(store, SshRemote(b.address, AppSshSessionFactory(b.key, b.hostKeys)))
        }, NetworkState { true }, freeSpace = { free })
        return inst to d
    }

    private fun blogOf(files: Map<String, String>? = null): BlogFixture =
        BlogFixture(tmp.newFolder(), files ?: BlogFixture.defaultFiles()).also { blog = it }

    @Test fun erfolgreicherKlonIstFlachUndGeprueft() {
        val b = blogOf(); val (inst, dir) = installer(b)
        assertEquals(InstallOutcome.Success, inst.install())
        GitStore(dir).use { s ->
            assertNotNull(s.refId("refs/remotes/origin/main"))
            assertNotNull(s.readText("refs/remotes/origin/main", "content/tags.yaml"))
            assertFalse("bare Klon ohne Arbeitsbaum", File(dir, "content").exists())
        }
    }

    @Test fun draftVomServerWirdAlsLokalerBranchUebernommen() {
        val b = blogOf()
        org.eclipse.jgit.api.Git.open(b.bare).use { g ->
            val u = g.repository.updateRef("refs/heads/draft"); u.setNewObjectId(g.repository.resolve("refs/heads/main")); u.update()
        }
        val (inst, dir) = installer(b)
        assertEquals(InstallOutcome.Success, inst.install())
        GitStore(dir).use { assertNotNull(it.refId("refs/heads/draft")) }
    }

    @Test fun zuWenigSpeicherBeginntNichtZuKlonen() {
        val b = blogOf(); val (inst, dir) = installer(b, free = 100_000_000L)
        val r = inst.install()
        assertTrue(r is InstallOutcome.NotEnoughSpace)
        assertEquals(RepositoryInstaller.MIN_FREE_BYTES, (r as InstallOutcome.NotEnoughSpace).neededBytes)
        assertFalse(dir.exists())
    }

    @Test fun abbruchLaesstKeinenKlonZurueck() {
        val b = blogOf(); val (inst, dir) = installer(b)
        lateinit var progress: CloneProgress
        progress = CloneProgress { _, _, _ -> progress.cancelled = true }
        assertEquals(InstallOutcome.Cancelled, inst.install(progress))
        assertFalse(dir.exists())
    }

    @Test fun fehlenderZielbranchWirdMitVorhandenenBranchesGemeldet() {
        val b = blogOf(); val (inst, dir) = installer(b, branch = "gibt-es-nicht")
        val r = inst.install()
        assertTrue("war: $r", r is InstallOutcome.BranchMissing)
        assertTrue((r as InstallOutcome.BranchMissing).available.contains("main"))
        assertFalse(dir.exists())
    }

    @Test fun repositoryOhneBeitraegeIstKeinBlog() {
        val b = blogOf(mapOf("README.md" to "nichts", "content/tags.yaml" to "- name: X\n")); val (inst, dir) = installer(b)
        val r = inst.install()
        assertTrue("war: $r", r is InstallOutcome.WrongRepository && r.reason.contains("content/posts"))
        assertFalse(dir.exists())
    }

    @Test fun repositoryOhneSchlagworteIstKeinBlog() {
        val b = blogOf(mapOf("content/posts/a/index.md" to "---\ntitle: A\n---\n")); val (inst, dir) = installer(b)
        val r = inst.install()
        assertTrue("war: $r", r is InstallOutcome.WrongRepository && r.reason.contains("tags.yaml"))
        assertFalse(dir.exists())
    }

    @Test fun serverNichtErreichbarWirdEingeordnet() {
        val b = blogOf(); val dead = (de.flauschiversum.publisher.git.RepositoryAddresses.parse("ssh://git@127.0.0.1:1/blog.git") as de.flauschiversum.publisher.git.AddressParse.Valid).address
        val d = File(b.root, "dead.git")
        val inst = RepositoryInstaller(d, dead, Refs("main"), { s -> RemoteOps(s, SshRemote(dead, AppSshSessionFactory(b.key, b.hostKeys))) }, NetworkState { true }, freeSpace = { Long.MAX_VALUE })
        val r = inst.install()
        assertTrue("war: $r", r is InstallOutcome.Failed && r.failure is de.flauschiversum.publisher.git.GitFailure.Unreachable)
        assertFalse(d.exists())
    }
}

class KeyInstructionsTest {
    private fun addr(s: String) = (de.flauschiversum.publisher.git.RepositoryAddresses.parse(s) as de.flauschiversum.publisher.git.AddressParse.Valid).address
    private val line = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIexample flauschiversum-publisher"

    @Test fun gitHubFuehrtZurDeployKeySeite() {
        val p = de.flauschiversum.publisher.setup.KeyInstructions.planFor(addr("git@github.com:RincewindWizzard/flauschiversum.git"), line, "Pixel 8")
        p as de.flauschiversum.publisher.setup.KeyInstructions.Plan.GitHub
        assertEquals("https://github.com/RincewindWizzard/flauschiversum/settings/keys/new", p.url)
        assertEquals("Flauschiversum Publisher (Pixel 8)", p.suggestedTitle)
    }

    @Test fun eigenerServerBekommtAuthorizedKeysBefehl() {
        val p = de.flauschiversum.publisher.setup.KeyInstructions.planFor(addr("ssh://git@vps.beispiel.de:2222/srv/git/blog.git"), line, "Pixel 8")
        p as de.flauschiversum.publisher.setup.KeyInstructions.Plan.OwnServer
        assertEquals("git", p.user); assertEquals("vps.beispiel.de", p.host)
        assertTrue(p.command.contains(">> ~/.ssh/authorized_keys"))
        assertTrue(p.command.contains("echo '$line'"))
    }

    @Test fun anfuehrungszeichenImKommentarSindMaskiert() {
        val cmd = de.flauschiversum.publisher.setup.KeyInstructions.authorizedKeysCommand("ssh-ed25519 AAAA mein'schluessel")
        assertTrue(cmd.contains("'ssh-ed25519 AAAA mein'\\''schluessel'"))
    }

    @Test fun titelOhneGeraetenamenHatVorgabe() {
        assertEquals("Flauschiversum Publisher (Handy)", de.flauschiversum.publisher.setup.KeyInstructions.deployKeyTitle("  "))
    }
}
