package de.flauschiversum.publisher.git

import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.URIish

/** Die Gegenstelle eines Repositorys und die dafür zu verwendende SSH-Fabrik. */
class SshRemote(val address: RepositoryAddress, val factory: AppSshSessionFactory, private val timeoutSeconds: Int = 30) {
    val uri: URIish get() = URIish(address.gitUrl)

    private val callback = TransportConfigCallback { transport ->
        if (transport is SshTransport) transport.sshSessionFactory = factory
    }

    /** Hängt SSH-Fabrik und Zeitlimit an einen JGit-Befehl. */
    fun <C : TransportCommand<C, *>> C.useSsh(): C = setTransportConfigCallback(callback).setTimeout(timeoutSeconds)

    fun openTransport(): SshTransport {
        val t = org.eclipse.jgit.transport.Transport.open(uri) as SshTransport
        t.sshSessionFactory = factory
        t.timeout = timeoutSeconds
        return t
    }
}
