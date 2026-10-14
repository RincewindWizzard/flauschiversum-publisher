package de.flauschiversum.publisher.posts

import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.TreeChange

/** Zeilenweises Anhängen an `content/redirects.yaml` (`/alt/: /neu/`). */
object RedirectsFile {
    const val PATH = "content/redirects.yaml"

    fun line(from: String, to: String) = "${Yaml.pathOrQuoted(from)}: ${Yaml.pathOrQuoted(to)}"

    /** Hängt die Weiterleitung an; vorhandene Zeilen und Kommentare bleiben unverändert. Gibt es [from] schon, bleibt die Datei gleich. */
    fun append(text: String, from: String, to: String): String {
        if (entries(text).any { it.first == from }) return text
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val base = if (text.isEmpty() || text.endsWith("\n")) text else text + eol
        return base + line(from, to) + eol
    }

    /** Entfernt die Zeile für [from] (für „Verwerfen“); sonst unverändert. */
    fun remove(text: String, from: String): String =
        PostDocument.splitKeepingEol(text).filterNot { l -> parseLine(l)?.first == from }.joinToString("")

    fun entries(text: String): List<Pair<String, String>> = text.lineSequence().mapNotNull { parseLine(it) }.toList()

    private fun parseLine(l: String): Pair<String, String>? {
        val t = l.trim()
        if (t.isEmpty() || t.startsWith("#")) return null
        val m = Regex("""^("(?:[^"\\]|\\.)*"|[^\s:][^\s]*?):\s+(.+)$""").matchEntire(t) ?: return null
        fun un(s: String) = if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\") else s
        return un(m.groupValues[1]) to un(m.groupValues[2].trim())
    }
}

/** Info für den Löschdialog. */
class DeletionInfo(
    val dir: String, val title: String, val oldPath: String,
    /** Beitrag liegt auf dem Zielbranch (sonst nur Entwurf: „Löschen“ = „Verwerfen“). */
    val published: Boolean,
    val referrers: List<String>,
)

/** Beitrag löschen (Spec `beitrag-loeschen`). */
object PostDeletion {
    const val DEFAULT_TARGET = "/beitraege/"

    /** Alte Adresse: `permalink`, sonst die vom Blog aus Datum und Verzeichnisname abgeleitete. */
    fun oldPath(doc: PostDocument, dir: String): String {
        val s = PostSummary.from(dir, doc.text)
        return s.permalink?.takeIf { it.isNotBlank() } ?: s.date?.let { PostNaming.permalink(it.date, dir) } ?: "/$dir/"
    }

    fun info(wf: DraftWorkflow, dir: String): DeletionInfo? {
        val reader = PostReader(wf.store)
        val published = wf.store.exists(wf.refs.target, "${PostReader.POSTS_DIR}/$dir/index.md")
        val rev = if (published) wf.refs.target else wf.baseRev()
        val doc = reader.document(rev, dir) ?: return null
        val old = oldPath(doc, dir)
        val title = PostSummary.from(dir, doc.text).title
        val refs = if (!published) emptyList() else referrers(wf, dir, old)
        return DeletionInfo(dir, title, old, published, refs)
    }

    /** Titel (höchstens zehn) der Beiträge, deren Text einen Link auf [oldPath] enthält. */
    fun referrers(wf: DraftWorkflow, dir: String, oldPath: String, max: Int = 10): List<String> {
        val reader = PostReader(wf.store)
        val rev = wf.baseRev()
        val needle = oldPath.trimEnd('/')
        return reader.dirNames(rev).filter { it != dir }.mapNotNull { d ->
            val text = reader.text(rev, d) ?: return@mapNotNull null
            val body = PostDocument.parse(text).body
            if (Regex("""\(\s*(?:https?://[^/)\s]+)?${Regex.escape(needle)}/?(?:[#?)][^)]*)?\)|href=["'](?:https?://[^/"']+)?${Regex.escape(needle)}/?["'#?]""").containsMatchIn(body)) PostSummary.from(d, text).title else null
        }.distinct().take(max)
    }

    /** Alte Adresse eines anderen Beitrags als Weiterleitungsziel. */
    fun pathOf(wf: DraftWorkflow, dir: String): String? =
        PostReader(wf.store).document(wf.baseRev(), dir)?.let { oldPath(it, dir) }

    /**
     * Löscht [dir] auf `draft`. Existiert der Beitrag nur als Entwurf, wird er verworfen. [redirectTo] (z. B. `/beitraege/`)
     * hängt eine Weiterleitung an; null: keine.
     */
    fun delete(wf: DraftWorkflow, dir: String, redirectTo: String?) {
        val info = info(wf, dir) ?: error("Der Beitrag „$dir“ wurde nicht gefunden.")
        if (!info.published) { wf.discard(dir); return }
        val changes = mutableListOf<TreeChange>(TreeChange.DeleteDir("${PostReader.POSTS_DIR}/$dir"))
        if (redirectTo != null) {
            val current = wf.store.readText(wf.baseRev(), RedirectsFile.PATH).orEmpty()
            val next = RedirectsFile.append(current, info.oldPath, redirectTo)
            if (next != current) changes += TreeChange.Put(RedirectsFile.PATH, next.toByteArray(Charsets.UTF_8))
        }
        wf.commit(changes, "Beitrag gelöscht: ${info.title}")
    }

    /** „Verwerfen“ eines Eintrags: stellt das Verzeichnis her und nimmt eine dafür angelegte Weiterleitung zurück. */
    fun discard(wf: DraftWorkflow, dir: String) {
        val prefix = "${PostReader.POSTS_DIR}/$dir"
        val kindDeleted = wf.overview().posts.firstOrNull { it.dir == dir }?.kind == DraftKind.DELETED
        val changes = mutableListOf<TreeChange>(TreeChange.ReplaceDir(prefix, wf.refs.target))
        if (kindDeleted) {
            val doc = PostReader(wf.store).document(wf.refs.target, dir)
            val draftRedirects = wf.store.readText(wf.baseRev(), RedirectsFile.PATH)
            val targetRedirects = wf.store.readText(wf.refs.target, RedirectsFile.PATH).orEmpty()
            if (doc != null && draftRedirects != null) {
                val from = oldPath(doc, dir)
                val addedByDeletion = RedirectsFile.entries(draftRedirects).any { it.first == from } && RedirectsFile.entries(targetRedirects).none { it.first == from }
                if (addedByDeletion) changes += TreeChange.Put(RedirectsFile.PATH, RedirectsFile.remove(draftRedirects, from).toByteArray(Charsets.UTF_8))
            }
        }
        wf.commit(changes, "Entwurf verworfen: $dir")
    }
}
