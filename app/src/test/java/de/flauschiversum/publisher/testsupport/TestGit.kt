package de.flauschiversum.publisher.testsupport

import java.io.File
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

/** Schirmt JGit in Tests von der Git-Konfiguration des Rechners ab (z. B. Signierung per SSH). */
object TestGit {
    private val none = File(System.getProperty("java.io.tmpdir"), "keine-gitconfig-${System.nanoTime()}")

    @Synchronized
    fun isolate() {
        val current = SystemReader.getInstance()
        if (current is Isolated) return
        SystemReader.setInstance(Isolated(current))
    }

    private class Isolated(parent: SystemReader) : SystemReader.Delegate(parent) {
        override fun openUserConfig(parent: Config?, fs: FS): FileBasedConfig = FileBasedConfig(parent, none, fs)
        override fun openSystemConfig(parent: Config?, fs: FS): FileBasedConfig = FileBasedConfig(parent, none, fs)
        override fun openJGitConfig(parent: Config?, fs: FS): FileBasedConfig = FileBasedConfig(parent, none, fs)
    }
}
