package de.flauschiversum.publisher.testsupport

import java.io.File
import java.nio.file.Files
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent

/** Hilfen zum Anlegen von Bare-Repositories für Tests. */
object TestRepos {
    private val author = PersonIdent("Test", "test@example.org")

    /** Legt ein Bare-Repository mit einem ersten Commit auf [branch] an. */
    fun createBare(dir: File, files: Map<String, String>, branch: String = "main"): File {
        val work = Files.createTempDirectory("work").toFile()
        try {
            Git.init().setDirectory(work).setInitialBranch(branch).call().use { git ->
                files.forEach { (path, content) -> write(work, path, content) }
                git.add().addFilepattern(".").call()
                git.commit().setMessage("Anfang").setAuthor(author).setCommitter(author).setSign(false).call()
                dir.mkdirs()
                Git.init().setBare(true).setDirectory(dir).setInitialBranch(branch).call().close()
                git.push().setRemote(dir.toURI().toString()).setRefSpecs(org.eclipse.jgit.transport.RefSpec("refs/heads/$branch:refs/heads/$branch")).call()
            }
        } finally {
            work.deleteRecursively()
        }
        return dir
    }

    /** Fügt in einem Bare-Repository einen Commit hinzu (über einen temporären Klon). */
    fun commit(bare: File, branch: String, files: Map<String, String?>, message: String) {
        val work = Files.createTempDirectory("work").toFile()
        try {
            Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(work).setBranch(branch).call().use { git ->
                files.forEach { (path, content) ->
                    if (content == null) File(work, path).delete() else write(work, path, content)
                }
                git.add().addFilepattern(".").setUpdate(false).call()
                git.add().addFilepattern(".").setUpdate(true).call()
                git.commit().setMessage(message).setAuthor(author).setCommitter(author).setSign(false).call()
                git.push().setRemote("origin").add(branch).call()
            }
        } finally {
            work.deleteRecursively()
        }
    }

    fun write(root: File, path: String, content: String) {
        val f = File(root, path)
        f.parentFile.mkdirs()
        f.writeText(content)
    }
}
