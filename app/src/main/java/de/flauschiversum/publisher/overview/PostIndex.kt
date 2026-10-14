package de.flauschiversum.publisher.overview

import de.flauschiversum.publisher.git.GitStore
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.PostSummary
import java.io.File

/** Ein Beitrag im Index: Kurzangaben, Suchtext und Titelbild. */
class IndexedPost(
    val summary: PostSummary,
    /** Kleingeschriebener Text aus Titel, Kurzbeschreibung und Schlagworten. */
    val meta: String,
    /** Kleingeschriebener Text des Beitrags. */
    val body: String,
    /** Datei des Titelbildes im Beitragsverzeichnis (Feld `image`, sonst erstes Bild), null: kein Bild. */
    val cover: String?,
    /** Objekt-ID des Beitragsverzeichnisses: ändert sich, sobald sich etwas darin ändert. */
    val treeId: String,
) {
    val dir: String get() = summary.dir
}

/**
 * Index aller Beiträge eines Standes. Einträge werden pro Beitragsverzeichnis anhand seiner Objekt-ID wiederverwendet und
 * auf Platte zwischengespeichert; nach einem Fetch werden nur geänderte Beiträge neu gelesen.
 */
class PostIndex(private val store: GitStore, private val cacheFile: File? = null) {
    private val cache = HashMap<String, IndexedPost>() // Schlüssel: treeId + Verzeichnisname
    private var loaded = false
    /** Wie viele Beiträge zuletzt neu gelesen werden mussten (für Tests und Messung). */
    @Volatile var lastReadCount = 0
        private set

    @Synchronized
    fun build(rev: String): List<IndexedPost> {
        loadCache()
        val reader = PostReader(store)
        var reads = 0
        val result = ArrayList<IndexedPost>()
        for (entry in store.list(rev, PostReader.POSTS_DIR)) {
            if (!entry.isDirectory) continue
            val key = entry.objectId.name + "/" + entry.name
            val hit = cache[key]
            if (hit != null) { result += hit; continue }
            val text = reader.text(rev, entry.name) ?: continue
            reads++
            val files = reader.files(rev, entry.name)
            result += make(entry.name, text, files, entry.objectId.name).also { cache[key] = it }
        }
        lastReadCount = reads
        if (reads > 0) saveCache(result)
        return result
    }

    private fun make(dir: String, text: String, files: List<String>, treeId: String): IndexedPost {
        val summary = PostSummary.from(dir, text)
        val meta = listOfNotNull(summary.title, summary.excerpt, summary.tags.joinToString(" ")).joinToString(" ").lowercase()
        return IndexedPost(summary, meta, PostDocument.parse(text).body.lowercase(), cover(summary.image, files), treeId)
    }

    // ---- Zwischenspeicher auf Platte (eine Zeile pro Beitrag, Felder durch Tabulator getrennt)
    @Synchronized private fun loadCache() {
        if (loaded) return
        loaded = true
        val f = cacheFile ?: return
        if (!f.isFile) return
        runCatching {
            f.useLines(Charsets.UTF_8) { lines ->
                lines.forEach { line ->
                    val p = line.split('\t').map { unescape(it) }
                    if (p.size == FIELDS) {
                        val summary = PostSummary(p[1], p[2], PostDate.parse(p[3]), p[4].split('\u001F').filter { it.isNotEmpty() }, p[5].ifEmpty { null }, p[6].ifEmpty { null }, p[7].ifEmpty { null }, p[8].ifEmpty { null }, p[9].ifEmpty { null })
                        val meta = listOfNotNull(summary.title, summary.excerpt, summary.tags.joinToString(" ")).joinToString(" ").lowercase()
                        cache[p[0] + "/" + p[1]] = IndexedPost(summary, meta, p[11], p[10].ifEmpty { null }, p[0])
                    }
                }
            }
        }
    }

    private fun saveCache(current: List<IndexedPost>) {
        val f = cacheFile ?: return
        runCatching {
            // Nur den aktuellen Stand und die zuletzt bekannten Einträge behalten (begrenzt die Größe)
            val keep = current.associateBy { it.treeId + "/" + it.dir }
            cache.keys.retainAll(keep.keys + cache.keys.take(MAX_EXTRA))
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                for (p in cache.values) {
                    val s = p.summary
                    w.write(listOf(p.treeId, s.dir, s.title, s.date?.render().orEmpty(), s.tags.joinToString("\u001F"), s.author.orEmpty(), s.image.orEmpty(), s.excerpt.orEmpty(), s.permalink.orEmpty(), s.problem.orEmpty(), p.cover.orEmpty(), p.body).joinToString("\t") { escape(it) })
                    w.write("\n")
                }
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { sb.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> s[i + 1] }); i += 2 } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    companion object {
        private const val FIELDS = 12
        private const val MAX_EXTRA = 600
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")

        /** Titelbild: das Feld `image`, wenn die Datei existiert, sonst das erste Bild (nach Namen), sonst keins. */
        fun cover(image: String?, files: List<String>): String? {
            if (!image.isNullOrBlank() && image in files) return image
            return files.filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXT }.minWithOrNull(String.CASE_INSENSITIVE_ORDER)
        }
    }
}
