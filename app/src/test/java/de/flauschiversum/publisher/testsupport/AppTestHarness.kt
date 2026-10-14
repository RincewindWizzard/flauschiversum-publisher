package de.flauschiversum.publisher.testsupport

import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.PublisherApp
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.NetworkInfo
import de.flauschiversum.publisher.git.RemoteOps
import de.flauschiversum.publisher.git.SshRemote
import de.flauschiversum.publisher.setup.InstallOutcome
import de.flauschiversum.publisher.setup.RepositoryInstaller
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import java.io.File
import kotlinx.coroutines.runBlocking

/** Eine Test-App (Robolectric) mit einem Test-Blog auf einem lokalen SSH-Server. */
class AppTestHarness(tmp: File, files: Map<String, String> = BlogFixture.defaultFiles(), binaries: Map<String, ByteArray> = emptyMap()) : AutoCloseable {
    val blog = BlogFixture(File(tmp, "blog"), files, binaries)
    val app: PublisherApp = ApplicationProvider.getApplicationContext()
    val container: AppContainer get() = app.container
    var metered = false
    val scheduler: InlineScheduler get() = container.uploads.scheduler as InlineScheduler

    init {
        container.network.delegate = object : NetworkInfo {
            override fun isOnline() = true
            override fun isMetered() = metered
        }
        container.freeSpace = { 10_000_000_000L }
        container.uploads.scheduler = InlineScheduler { container.uploads }
    }

    /** Adresse des Test-Servers in der Schreibweise der Einstellungen. */
    val address: String get() = blog.address.text

    /** Richtet die App fertig ein: Adresse, Schlüssel, Host-Schlüssel, Klon. */
    fun completeSetup() = runBlocking {
        val c = container
        c.settings.update { it.copy(repositoryAddress = address) }
        c.keyStore.save(blog.key)
        val info = c.accessChecker.probeHostKey(blog.address)!!
        c.accessChecker.trust(info)
        val refs = c.session.refs()
        val installer = RepositoryInstaller(c.session.dir, blog.address, refs, { store ->
            RemoteOps(store, SshRemote(blog.address, AppSshSessionFactory(blog.key, c.hostKeys)))
        }, c.network, freeSpace = { Long.MAX_VALUE })
        check(installer.install() == InstallOutcome.Success)
        val fingerprint = blog.key.fingerprint
        c.setupState.update {
            it.copy(confirmedAddress = address, registeredFor = "$fingerprint@$address", accessVerifiedFor = "$fingerprint@$address", cloneVerifiedFor = "$address#main")
        }
    }

    override fun close() { blog.close() }
}
