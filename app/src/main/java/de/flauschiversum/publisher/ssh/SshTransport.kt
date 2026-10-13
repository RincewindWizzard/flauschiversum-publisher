package de.flauschiversum.publisher.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.nio.ByteBuffer
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig
import org.eclipse.jgit.util.FS

/** Wie [AppSshSessionFactory] mit unbekannten oder geänderten Host-Schlüsseln umgeht. */
enum class HostKeyMode {
    /** Nur bereits bestätigte Server (und GitHub) dürfen verbunden werden. */
    STRICT,
    /** Nur den vorgewiesenen Schlüssel merken, die Verbindung danach abbrechen (für die Rückfrage an den Nutzer). */
    RECORD_ONLY,
}

/**
 * JGit-SSH-Zugang über JSch: Der Schlüssel kommt aus der App, Host-Schlüssel werden nur gegen den
 * [HostKeyStore] geprüft; `~/.ssh` und `known_hosts` des Systems werden nie gelesen.
 */
class AppSshSessionFactory(
    private val key: SshKeyPair,
    private val hostKeys: HostKeyStore,
    private val mode: HostKeyMode = HostKeyMode.STRICT,
) : JschConfigSessionFactory() {

    /** Letzter vom Server vorgewiesener Schlüssel dieser Fabrik (für Rückfrage und Fehlermeldung). */
    @Volatile var lastPresented: HostKeyInfo? = null
        private set
    @Volatile var lastCheck: HostKeyCheck? = null
        private set

    private val repository = object : HostKeyRepository {
        override fun check(host: String?, k: ByteArray?): Int {
            if (host == null || k == null) return HostKeyRepository.NOT_INCLUDED
            val info = parseHostKey(host, k) ?: return HostKeyRepository.NOT_INCLUDED
            lastPresented = info
            val result = hostKeys.check(info)
            lastCheck = result
            if (mode == HostKeyMode.RECORD_ONLY) return HostKeyRepository.NOT_INCLUDED
            return when (result) {
                HostKeyCheck.Trusted -> HostKeyRepository.OK
                HostKeyCheck.Unknown -> HostKeyRepository.NOT_INCLUDED
                is HostKeyCheck.Changed -> HostKeyRepository.CHANGED
            }
        }
        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, k: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "app"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    override fun configure(hc: OpenSshConfig.Host, session: Session) {
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        session.setConfig("kex", KEX)
        session.hostKeyRepository = repository
        session.userInfo = null
    }

    override fun createDefaultJSch(fs: FS): JSch {
        val jsch = JSch()
        jsch.hostKeyRepository = repository
        jsch.addIdentity("app", key.privatePem.toByteArray(), key.publicLine.toByteArray(), null)
        return jsch
    }

    companion object {
        private const val KEX = "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256," +
            "ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256," +
            "diffie-hellman-group16-sha512,diffie-hellman-group14-sha256"

        /**
         * Stellt JSch auf BouncyCastle für Ed25519 und X25519 um. Das ist nötig, weil Android die
         * Java-Klassen für EdDSA/XDH (Java 15+) nicht überall mitbringt und JSch seine Java-15-Fassung
         * nur in einer Multi-Release-Jar liefert, die ART nicht auswertet.
         */
        @Synchronized
        fun configureJsch() {
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("keypairgen_fromprivate.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
            JSch.setConfig("StrictHostKeyChecking", "yes")
        }

        /** Liest Host (und Port aus "[host]:port") sowie Typ aus dem von JSch gelieferten Schlüsselblock. */
        fun parseHostKey(hostSpec: String, blob: ByteArray): HostKeyInfo? {
            val typeName = runCatching {
                val buf = ByteBuffer.wrap(blob)
                val len = buf.int
                String(blob, 4, len, Charsets.US_ASCII)
            }.getOrNull() ?: return null
            val type = HostKeyType.fromSshName(typeName) ?: return null
            val (host, port) = if (hostSpec.startsWith("[")) {
                val end = hostSpec.indexOf(']')
                val h = hostSpec.substring(1, end)
                val p = hostSpec.substring(end + 1).removePrefix(":").toIntOrNull() ?: 22
                h to p
            } else hostSpec to 22
            return HostKeyInfo(host, port, type, blob)
        }

        fun install(factory: SshSessionFactory) = SshSessionFactory.setInstance(factory)
    }
}
