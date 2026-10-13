package de.flauschiversum.publisher.testsupport

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.PublicKey
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.git.GitLocationResolver
import org.apache.sshd.git.pack.GitPackCommandFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.Environment
import org.apache.sshd.common.session.SessionContext

/**
 * Ein kleiner SSH-Server für Tests, der Git über SSH (`git-upload-pack`, `git-receive-pack`) für Bare-Repositories
 * unter [root] anbietet. Schlüssel lassen sich als Schreibschlüssel oder nur lesend (wie ein GitHub Deploy Key
 * ohne "Allow write access") zulassen.
 */
class TestSshGitServer(val root: File) : AutoCloseable {
    private val writeKeys = mutableListOf<PublicKey>()
    private val readKeys = mutableListOf<PublicKey>()
    private val sshd: SshServer = SshServer.setUpDefaultServer()

    /** Anzahl der Verbindungen, die eine Anmeldung versucht haben (zum Prüfen von Netzzugriffen). */
    @Volatile var loginAttempts = 0
    @Volatile var rejectReceivePack: Boolean = false

    val port: Int get() = sshd.port
    val hostKeyBlob: ByteArray get() = KeyUtils.getFingerPrint(sshd.keyPairProvider.loadKeys(null).first().public).toByteArray()

    init {
        root.mkdirs()
        sshd.port = 0
        sshd.host = "127.0.0.1"
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(File(root.parentFile ?: root, "hostkey-${root.name}.ser").toPath()).also {
            it.algorithm = "EC"
            it.keySize = 256
        }
        sshd.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { _, key, session ->
            loginAttempts++
            val isWrite = writeKeys.any { KeyUtils.compareKeys(it, key) }
            val isRead = readKeys.any { KeyUtils.compareKeys(it, key) }
            if (isWrite || isRead) session.setAttribute(READ_ONLY, !isWrite)
            isWrite || isRead
        }
        val resolver = GitLocationResolver { _, _, _, _ -> root.toPath() }
        val git = GitPackCommandFactory().withGitLocationResolver(resolver)
        sshd.commandFactory = object : CommandFactory {
            override fun createCommand(channel: ChannelSession, command: String): Command {
                val readOnly = channel.session.getAttribute(READ_ONLY) == true
                if (command.startsWith("git-receive-pack") && (readOnly || rejectReceivePack)) {
                    return FailingCommand("ERROR: The key you are using is read-only.\n")
                }
                return git.createCommand(channel, command)
            }
        }
        sshd.start()
    }

    fun allowWrite(openSshPublicLine: String) { writeKeys += parse(openSshPublicLine) }
    fun allowReadOnly(openSshPublicLine: String) { readKeys += parse(openSshPublicLine) }

    private fun parse(line: String): PublicKey =
        PublicKeyEntry.parsePublicKeyEntry(line).resolvePublicKey(null as SessionContext?, emptyMap(), null)

    /** Host-Schlüssel des Servers als SSH-Block. */
    fun hostPublicKey(): PublicKey = sshd.keyPairProvider.loadKeys(null).first().public

    override fun close() { sshd.stop(true) }

    private class FailingCommand(private val message: String) : Command {
        private var err: OutputStream? = null
        private var exit: ExitCallback? = null
        override fun setInputStream(`in`: InputStream?) = Unit
        override fun setOutputStream(out: OutputStream?) = Unit
        override fun setErrorStream(err: OutputStream?) { this.err = err }
        override fun setExitCallback(callback: ExitCallback?) { exit = callback }
        override fun start(channel: ChannelSession, env: Environment) {
            err?.write(message.toByteArray()); err?.flush()
            exit?.onExit(1, "refused")
        }
        override fun destroy(channel: ChannelSession) = Unit
    }

    companion object {
        private val READ_ONLY = org.apache.sshd.common.AttributeRepository.AttributeKey<Boolean>()
    }
}
