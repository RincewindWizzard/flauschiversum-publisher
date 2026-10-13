package de.flauschiversum.publisher

import android.app.Application
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.crypto.CryptoProviders
import de.flauschiversum.publisher.ssh.AppSshSessionFactory

class PublisherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // JGit liest "user.home" (Git-/SSH-Konfiguration); auf Android zeigt es auf den privaten App-Speicher.
        System.setProperty("user.home", filesDir.absolutePath)
        CryptoProviders.install()
        AppSshSessionFactory.configureJsch()
        container = AppContainer(this)
    }
}
