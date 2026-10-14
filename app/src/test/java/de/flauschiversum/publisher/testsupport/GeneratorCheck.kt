package de.flauschiversum.publisher.testsupport

import de.flauschiversum.publisher.git.DraftWorkflow
import java.io.File
import java.util.concurrent.TimeUnit
import org.eclipse.jgit.diff.DiffEntry
import org.junit.Assume

/**
 * Prüft die Ergebnisse der App mit dem echten Generator des Blogs (`flauschiversum check`).
 *
 * Voraussetzung: Umgebungsvariable `BLOG_GENERATOR_DIR` zeigt auf eine Kopie des Blogs, in der
 * `cargo build --release` gelaufen ist. Ohne sie werden die Tests übersprungen (nicht grün gemeldet).
 */
object GeneratorCheck {
    class Result(val exitCode: Int, val output: String) {
        val warnings: List<String> get() = output.lineSequence().filter { it.startsWith("Warnung:") }.toList()
        val errors: List<String> get() = output.lineSequence().filter { it.startsWith("Fehler") }.toList()
    }

    fun generatorDir(): File {
        val dir = System.getenv("BLOG_GENERATOR_DIR")
        Assume.assumeTrue("BLOG_GENERATOR_DIR nicht gesetzt: Generator-Prüfung übersprungen", dir != null && File(dir, "target/release/flauschiversum").canExecute())
        return File(dir!!)
    }

    /** Legt `work` als Kopie des Blogs an (Inhalte per Hardlink, daher schnell) und legt die eigenen Änderungen von `draft` darüber. */
    fun prepare(work: File, wf: DraftWorkflow): File {
        val gen = generatorDir()
        work.mkdirs()
        for (name in listOf("content", "templates", "assets", "Cargo.toml", "Cargo.lock")) {
            run(listOf("cp", "-al", File(gen, name).absolutePath, File(work, name).absolutePath), gen)
        }
        for (d in wf.ownChanges()) {
            val f = File(work, d.path)
            if (d.type == DiffEntry.ChangeType.DELETE) { f.delete(); continue }
            f.parentFile.mkdirs()
            f.delete() // Hardlink lösen, damit das Original unverändert bleibt
            f.writeBytes(wf.store.readBytes(wf.refs.draft, d.path)!!)
        }
        return work
    }

    fun check(work: File): Result {
        val bin = File(generatorDir(), "target/release/flauschiversum").absolutePath
        val p = ProcessBuilder(bin, "check").directory(work).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(600, TimeUnit.SECONDS)) { p.destroyForcibly(); error("Generator-Prüfung dauert zu lange") }
        return Result(p.exitValue(), out)
    }

    /** Baut die Seite in `work/dist` (Bild-Cache gemeinsam für alle Läufe); gibt Ausgabe und Ausgabeverzeichnis zurück. */
    fun build(work: File): Pair<Result, File> {
        val gen = generatorDir()
        val bin = File(gen, "target/release/flauschiversum").absolutePath
        val cache = File(gen, ".publisher-cache").also { it.mkdirs() }
        val p = ProcessBuilder(bin, "build", "--cache", cache.absolutePath).directory(work).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(900, TimeUnit.SECONDS)) { p.destroyForcibly(); error("Generator-Bau dauert zu lange") }
        return Result(p.exitValue(), out) to File(work, "dist")
    }

    private fun run(cmd: List<String>, dir: File) {
        val p = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "${cmd.joinToString(" ")} fehlgeschlagen: $out" }
    }
}
