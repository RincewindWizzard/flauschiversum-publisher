package de.flauschiversum.publisher.crypto

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Android liefert nur einen abgespeckten "BC"-Provider ohne Ed25519. Für SSH (JSch) und die
 * Schlüsselerzeugung wird deshalb der vollständige BouncyCastle-Provider an erster Stelle eingesetzt.
 */
object CryptoProviders {
    @Synchronized
    fun install() {
        val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (current is BouncyCastleProvider) return
        if (current != null) Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
