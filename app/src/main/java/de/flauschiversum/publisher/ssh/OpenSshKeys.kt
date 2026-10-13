package de.flauschiversum.publisher.ssh

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil

/** Ein SSH-Schlüsselpaar im OpenSSH-Format. */
class SshKeyPair(
    /** Privater Schlüssel als PEM ("-----BEGIN OPENSSH PRIVATE KEY-----"). */
    val privatePem: String,
    /** Öffentlicher Schlüssel als eine Zeile ("ssh-ed25519 AAAA… Kommentar"). */
    val publicLine: String,
) {
    val publicBlob: ByteArray get() = Base64.getDecoder().decode(publicLine.split(' ')[1])
    val fingerprint: String get() = OpenSshKeys.fingerprint(publicBlob)
}

object OpenSshKeys {
    private const val HEADER = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val FOOTER = "-----END OPENSSH PRIVATE KEY-----"

    fun generate(comment: String): SshKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = generator.generateKeyPair()
        val publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.public as Ed25519PublicKeyParameters)
        val privateBlob = OpenSSHPrivateKeyUtil.encodePrivateKey(pair.private)
        val wrapped = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(privateBlob)
        val pem = "$HEADER\n$wrapped\n$FOOTER\n"
        val line = "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} $comment".trim()
        return SshKeyPair(pem, line)
    }

    /** SHA256-Fingerabdruck wie ihn OpenSSH und GitHub anzeigen: "SHA256:" + Base64 ohne Auffüllung. */
    fun fingerprint(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
