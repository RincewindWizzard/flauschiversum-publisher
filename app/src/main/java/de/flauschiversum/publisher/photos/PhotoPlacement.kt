package de.flauschiversum.publisher.photos

import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.images.ImageNames
import de.flauschiversum.publisher.images.ImageProcessor
import de.flauschiversum.publisher.images.Rights
import de.flauschiversum.publisher.posts.Fields
import de.flauschiversum.publisher.posts.ImageRef
import de.flauschiversum.publisher.posts.PostBody
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.posts.PostNaming
import de.flauschiversum.publisher.posts.PostProps
import de.flauschiversum.publisher.posts.PostReader
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Ein geteiltes Foto, bereits in den App-Speicher kopiert. */
data class SharedPhoto(val file: File, val displayName: String?, val mimeType: String?)

/** Ein Foto mit dem vom Nutzer eingegebenen Alt-Text. */
data class PhotoWithAlt(val photo: SharedPhoto, val alt: String)

sealed interface PhotoTarget {
    /** Vorhandener Beitrag (Verzeichnisname). */
    data class Existing(val dir: String) : PhotoTarget
    /** Neuer Beitrag mit diesem Titel; Datum heute, Autor aus den Einstellungen. */
    data class New(val title: String, val author: String) : PhotoTarget
}

class PhotoResult(val dir: String, val fileNames: List<String>, val warnings: List<String>)

/** Legt geteilte Fotos in einem Beitrag ab: skaliert, im Beitragsverzeichnis, mit Bildzeile unter „Anhänge“, in einem Commit auf `draft`. */
class PhotoPlacement(private val processor: ImageProcessor, private val rights: () -> Rights) {

    fun place(wf: DraftWorkflow, target: PhotoTarget, photos: List<PhotoWithAlt>): PhotoResult {
        require(photos.isNotEmpty()) { "Keine Fotos ausgewählt." }
        check(wf.targetId() != null) { "Das Repository ist noch nicht geholt." }
        val reader = PostReader(wf.store)
        val rev = wf.baseRev()
        val dir: String
        val base: PostDocument?
        val title: String
        when (target) {
            is PhotoTarget.Existing -> {
                dir = target.dir
                base = reader.document(rev, dir) ?: error("Der Beitrag „$dir“ wurde nicht gefunden.")
                check(base.hasFrontmatter) { "Der Beitrag „$dir“ hat kein lesbares Frontmatter." }
                title = base.string(Fields.TITLE) ?: dir
            }
            is PhotoTarget.New -> {
                val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
                val existing = reader.dirNames(wf.refs.target) + (if (wf.hasDraft()) reader.dirNames(wf.refs.draft) else emptyList())
                dir = PostNaming.uniqueDirName(PostNaming.dirNameFromTitle(target.title), existing, today.year)
                title = target.title.trim()
                val props = PostProps(title, PostDate(today, null), PostNaming.permalink(today, dir), emptyList(), target.author.takeIf { it.isNotBlank() })
                base = PostEditing.apply(null, null, props, "")
            }
        }
        val taken = reader.files(rev, dir).toMutableList()
        val changes = mutableListOf<TreeChange>()
        val refs = mutableListOf<ImageRef>()
        val warnings = mutableListOf<String>()
        for (p in photos) {
            val name = ImageNames.sanitize(p.photo.displayName)
            val img = processor.process({ p.photo.file.inputStream() }, name, p.photo.mimeType, rights())
            val finalName = ImageNames.unique(ImageNames.withExtension(name, ImageNames.extension(img.fileName)), taken)
            taken += finalName
            if (!img.withinLimit) warnings += "„$finalName“ ist größer als 1 MB."
            changes += TreeChange.Put("${PostReader.POSTS_DIR}/$dir/$finalName", img.bytes)
            refs += ImageRef(p.alt, finalName)
        }
        val eol = if (base.text.contains("\r\n")) "\r\n" else "\n"
        val newBody = PostBody.appendAttachments(base.body, refs, eol)
        changes += TreeChange.Put("${PostReader.POSTS_DIR}/$dir/index.md", base.withBody(newBody).text.toByteArray(Charsets.UTF_8))
        val msg = if (target is PhotoTarget.New) "Beitrag: $title (mit ${photos.size} Foto${if (photos.size == 1) "" else "s"})"
        else "Fotos hinzugefügt: $title"
        wf.commit(changes, msg)
        return PhotoResult(dir, refs.map { it.file }, warnings)
    }
}
