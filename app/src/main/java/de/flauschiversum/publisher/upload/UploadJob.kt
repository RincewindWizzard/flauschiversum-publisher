package de.flauschiversum.publisher.upload

import de.flauschiversum.publisher.photos.PhotoTarget
import java.io.File
import java.io.Reader
import java.util.Properties

enum class JobStatus(val label: String) {
    /** Gesichert, Commit steht aus. */
    QUEUED("Wartet"),
    RUNNING("Läuft"),
    WAITING_NETWORK("Wartet auf Netz"),
    WAITING_WIFI("Wartet auf WLAN"),
    FAILED("Fehlgeschlagen"),
    DONE("Erledigt"),
}

data class JobPhoto(val storedName: String, val displayName: String?, val mimeType: String?, val alt: String)

/** Ein Hintergrundauftrag: Fotos zu einem Beitrag ablegen, committen, hochladen. */
data class UploadJob(
    val id: String,
    val targetDir: String?,
    /** Titel und Autor, wenn ein neuer Beitrag entstehen soll. */
    val newTitle: String?,
    val newAuthor: String?,
    val photos: List<JobPhoto>,
    val status: JobStatus,
    val committed: Boolean,
    val message: String?,
    val createdAt: Long,
    val finishedAt: Long?,
    val attempts: Int,
    /** Beitrag (Verzeichnis) nach dem Commit, für die Anzeige. */
    val resultDir: String? = null,
) {
    val target: PhotoTarget get() = targetDir?.let { PhotoTarget.Existing(it) } ?: PhotoTarget.New(newTitle.orEmpty(), newAuthor.orEmpty())
    val label: String get() = targetDir ?: newTitle ?: "Neuer Beitrag"
}

/** Dauerhafte Ablage der Aufträge: ein Verzeichnis je Auftrag mit `job.properties` und den gesicherten Fotos. */
class JobStore(private val root: File) {
    fun dir(id: String) = File(root, id)
    fun photoFile(id: String, stored: String) = File(dir(id), "photos/$stored")

    @Synchronized fun save(job: UploadJob) {
        val d = dir(job.id); d.mkdirs()
        val p = Properties()
        p["targetDir"] = job.targetDir.orEmpty(); p["newTitle"] = job.newTitle.orEmpty(); p["newAuthor"] = job.newAuthor.orEmpty()
        p["status"] = job.status.name; p["committed"] = job.committed.toString(); p["message"] = job.message.orEmpty()
        p["createdAt"] = job.createdAt.toString(); p["finishedAt"] = job.finishedAt?.toString().orEmpty(); p["attempts"] = job.attempts.toString()
        p["resultDir"] = job.resultDir.orEmpty(); p["photos"] = job.photos.size.toString()
        job.photos.forEachIndexed { i, ph ->
            p["photo.$i.stored"] = ph.storedName; p["photo.$i.name"] = ph.displayName.orEmpty(); p["photo.$i.mime"] = ph.mimeType.orEmpty(); p["photo.$i.alt"] = ph.alt
        }
        val tmp = File(d, "job.properties.tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { p.store(it, null) }
        val target = File(d, "job.properties")
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    @Synchronized fun load(id: String): UploadJob? {
        val f = File(dir(id), "job.properties")
        if (!f.isFile) return null
        return runCatching {
            val p = Properties(); f.bufferedReader(Charsets.UTF_8).use { r: Reader -> p.load(r) }
            fun s(k: String) = p.getProperty(k).orEmpty()
            UploadJob(
                id = id, targetDir = s("targetDir").ifEmpty { null }, newTitle = s("newTitle").ifEmpty { null }, newAuthor = s("newAuthor").ifEmpty { null },
                photos = (0 until s("photos").toInt()).map { i -> JobPhoto(s("photo.$i.stored"), s("photo.$i.name").ifEmpty { null }, s("photo.$i.mime").ifEmpty { null }, s("photo.$i.alt")) },
                status = JobStatus.valueOf(s("status")), committed = s("committed").toBoolean(), message = s("message").ifEmpty { null },
                createdAt = s("createdAt").toLong(), finishedAt = s("finishedAt").toLongOrNull(), attempts = s("attempts").toInt(), resultDir = s("resultDir").ifEmpty { null },
            )
        }.getOrNull()
    }

    @Synchronized fun all(): List<UploadJob> = (root.list() ?: emptyArray()).mapNotNull { load(it) }.sortedByDescending { it.createdAt }

    @Synchronized fun update(id: String, f: (UploadJob) -> UploadJob): UploadJob? = load(id)?.let { f(it).also(::save) }

    /** Löscht die gesicherten Fotos (nach dem Commit oder beim Abbrechen); der Auftrag bleibt als Eintrag. */
    @Synchronized fun deletePhotos(id: String) { File(dir(id), "photos").deleteRecursively() }

    @Synchronized fun remove(id: String) { dir(id).deleteRecursively() }

    fun hasPhotos(id: String) = File(dir(id), "photos").list()?.isNotEmpty() == true
}
