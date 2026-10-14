package de.flauschiversum.publisher.posts

import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.TreeChange

/** Ein Bild eines Beitrags im Fotobildschirm. */
data class PhotoInfo(
    val file: String,
    /** Alt-Text der ersten Bildzeile, leer ohne Verweis. */
    val alt: String,
    val isCover: Boolean,
    /** Kein Verweis im Markdown. */
    val notInText: Boolean,
    /** Steht unter `## Anhänge` (Reihenfolge änderbar). */
    val inAttachments: Boolean,
    /** Verweise außerhalb von `## Anhänge` (Fließtext). */
    val outsideRefs: Int,
)

/** Fotoverwaltung eines Beitrags: alles landet als Commit auf `draft` (Spec `foto-verwaltung`). */
object PhotoManagement {
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")

    private fun indexPath(dir: String) = "${PostReader.POSTS_DIR}/$dir/index.md"

    fun photos(wf: DraftWorkflow, dir: String): List<PhotoInfo> {
        val reader = PostReader(wf.store)
        val rev = wf.baseRev()
        val doc = reader.document(rev, dir) ?: return emptyList()
        val cover = PostSummary.from(dir, doc.text).image
        val refs = PhotoMarkdown.refs(doc.body)
        val files = reader.files(rev, dir).filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXT }
        val inText = files.filter { f -> refs.any { it.ref.file == f } }
        val ordered = PhotoMarkdown.attachmentFiles(doc.body).filter { it in files }.distinct() +
            files.filter { f -> f !in PhotoMarkdown.attachmentFiles(doc.body) }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return ordered.map { f ->
            val mine = refs.filter { it.ref.file == f }
            PhotoInfo(f, mine.firstOrNull()?.ref?.alt.orEmpty(), f == cover, f !in inText, mine.any { it.inAttachments }, mine.count { !it.inAttachments })
        }
    }

    private fun edit(wf: DraftWorkflow, dir: String, message: String, extra: List<TreeChange> = emptyList(), f: (String) -> String): Boolean {
        val doc = PostReader(wf.store).document(wf.baseRev(), dir) ?: error("Der Beitrag „$dir“ wurde nicht gefunden.")
        val changes = mutableListOf<TreeChange>()
        val next = f(doc.body)
        if (next != doc.body) changes += TreeChange.Put(indexPath(dir), doc.withBody(next).text.toByteArray(Charsets.UTF_8))
        changes += extra
        if (changes.isEmpty()) return false
        return wf.commit(changes, message) != null
    }

    fun setAlt(wf: DraftWorkflow, dir: String, file: String, alt: String) = edit(wf, dir, "Alt-Text geändert: $file") { PhotoMarkdown.setAlt(it, file, alt) }

    fun include(wf: DraftWorkflow, dir: String, file: String, alt: String) = edit(wf, dir, "Foto eingebunden: $file") { PhotoMarkdown.include(it, file, alt) }

    fun move(wf: DraftWorkflow, dir: String, file: String, toIndex: Int) = edit(wf, dir, "Fotos umsortiert") { PhotoMarkdown.move(it, file, toIndex) }

    /** Titelbild setzen oder entfernen (null) — Feld `image` im Frontmatter. */
    fun setCover(wf: DraftWorkflow, dir: String, file: String?): Boolean {
        val doc = PostReader(wf.store).document(wf.baseRev(), dir) ?: error("Der Beitrag „$dir“ wurde nicht gefunden.")
        val props = PostEditing.read(doc)
        val next = PostEditing.apply(doc, props, props.copy(image = file), doc.body)
        if (next.text == doc.text) return false
        return wf.commit(listOf(TreeChange.Put(indexPath(dir), next.text.toByteArray(Charsets.UTF_8))), "Titelbild geändert") != null
    }

    /** Entfernt Bildzeilen und Datei in einem Commit. Das Titelbild muss vorher geändert werden. */
    fun remove(wf: DraftWorkflow, dir: String, file: String): Boolean {
        val info = photos(wf, dir).firstOrNull { it.file == file }
        require(info == null || !info.isCover) { "Das Titelbild muss vorher geändert werden." }
        return edit(wf, dir, "Foto entfernt: $file", listOf(TreeChange.Delete("${PostReader.POSTS_DIR}/$dir/$file"))) { PhotoMarkdown.remove(it, file) }
    }
}
