package de.flauschiversum.publisher.git

import java.io.File
import java.io.InputStream
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.NullOutputStream

/** Eine Änderung an einem Pfad für einen neuen Commit. */
sealed interface TreeChange {
    val path: String
    class Put(override val path: String, val source: () -> InputStream, val length: Long) : TreeChange {
        constructor(path: String, bytes: ByteArray) : this(path, { bytes.inputStream() }, bytes.size.toLong())
    }
    class Delete(override val path: String) : TreeChange
    /** Entfernt ein ganzes Verzeichnis. */
    class DeleteDir(override val path: String) : TreeChange
}

/** Ein Eintrag eines Verzeichnisses im Repository. */
data class TreeEntry(val name: String, val isDirectory: Boolean, val objectId: ObjectId)

/** Unterschied eines Pfades zwischen zwei Ständen. */
data class PathDiff(val path: String, val type: DiffEntry.ChangeType)

/**
 * Zugriff auf das lokale (bare, flache) Repository ohne Arbeitsbaum: Lesen aus beliebigen Ständen und Schreiben
 * von Commits direkt in der Objektdatenbank. Dadurch braucht der Klon keinen Platz für einen Arbeitsbaum, und
 * Branches lassen sich ohne Auschecken lesen und ändern.
 */
class GitStore(val dir: File) : AutoCloseable {
    private val repoLazy = lazy {
        FileRepositoryBuilder().setGitDir(dir).setBare().build()
    }
    val repo: Repository by repoLazy

    fun exists(): Boolean = File(dir, "HEAD").isFile && File(dir, "objects").isDirectory

    fun resolve(rev: String): ObjectId? = repo.resolve(rev)

    fun refId(name: String): ObjectId? = repo.exactRef(name)?.objectId

    /** Liest eine Datei aus einem Stand (Branch, Commit); null, wenn sie nicht existiert. */
    fun readBytes(rev: String, path: String): ByteArray? = withBlob(rev, path) { it.bytes }

    fun readText(rev: String, path: String): String? = readBytes(rev, path)?.toString(Charsets.UTF_8)

    /** Öffnet eine Datei als Strom (für große Bilder). */
    fun <T> useStream(rev: String, path: String, block: (InputStream) -> T): T? =
        withBlob(rev, path) { loader -> loader.openStream().use(block) }

    fun size(rev: String, path: String): Long? = withBlob(rev, path) { it.size }

    fun exists(rev: String, path: String): Boolean = withBlob(rev, path) { true } == true

    private fun <T> withBlob(rev: String, path: String, block: (org.eclipse.jgit.lib.ObjectLoader) -> T): T? {
        val id = repo.resolve("$rev^{tree}") ?: return null
        repo.newObjectReader().use { reader ->
            val tw = TreeWalk.forPath(repo, reader, path, id) ?: return null
            if (tw.isSubtree) return null
            return block(reader.open(tw.getObjectId(0)))
        }
    }

    /** Listet die Einträge eines Verzeichnisses (leer, wenn es nicht existiert). */
    fun list(rev: String, dirPath: String): List<TreeEntry> {
        val id = repo.resolve("$rev^{tree}") ?: return emptyList()
        repo.newObjectReader().use { reader ->
            val tw = if (dirPath.isEmpty()) TreeWalk(repo, reader).also { it.addTree(id); it.isRecursive = false }
            else TreeWalk.forPath(repo, reader, dirPath, id)?.takeIf { it.isSubtree }?.let { sub ->
                TreeWalk(repo, reader).also { it.addTree(sub.getObjectId(0)); it.isRecursive = false }
            } ?: return emptyList()
            val out = mutableListOf<TreeEntry>()
            while (tw.next()) out += TreeEntry(tw.nameString, tw.isSubtree, tw.getObjectId(0))
            return out
        }
    }

    /** Alle geänderten Pfade zwischen zwei Ständen (unter [prefix], rekursiv). */
    fun diff(oldRev: String?, newRev: String?, prefix: String = ""): List<PathDiff> {
        repo.newObjectReader().use { reader ->
            val oldTree = oldRev?.let { repo.resolve("$it^{tree}") }
            val newTree = newRev?.let { repo.resolve("$it^{tree}") }
            val oldIter = oldTree?.let { org.eclipse.jgit.treewalk.CanonicalTreeParser(null, reader, it) } ?: org.eclipse.jgit.treewalk.EmptyTreeIterator()
            val newIter = newTree?.let { org.eclipse.jgit.treewalk.CanonicalTreeParser(null, reader, it) } ?: org.eclipse.jgit.treewalk.EmptyTreeIterator()
            DiffFormatter(NullOutputStream.INSTANCE).use { df ->
                df.setRepository(repo)
                df.setDetectRenames(false)
                if (prefix.isNotEmpty()) df.pathFilter = PathFilter.create(prefix.trim('/'))
                return df.scan(oldIter, newIter).map { e ->
                    PathDiff(if (e.changeType == DiffEntry.ChangeType.DELETE) e.oldPath else e.newPath, e.changeType)
                }
            }
        }
    }

    /**
     * Legt einen Commit an, dessen Baum der Baum von [parentRev] mit den [changes] ist, und setzt [refName]
     * darauf. Nichts im Arbeitsverzeichnis wird angefasst. Gibt null zurück, wenn sich der Baum nicht ändert.
     */
    fun commit(
        parentRev: String?,
        changes: List<TreeChange>,
        message: String,
        author: PersonIdent,
        refName: String,
        expectedOld: ObjectId? = null,
    ): ObjectId? {
        val parent = parentRev?.let { repo.resolve(it) }
        repo.newObjectInserter().use { inserter ->
            val cache = DirCache.newInCore()
            if (parent != null) {
                repo.newObjectReader().use { reader ->
                    val rw = RevWalk(reader)
                    val tree = rw.parseCommit(parent).tree
                    val builder = cache.builder()
                    builder.addTree(byteArrayOf(), 0, reader, tree)
                    builder.finish()
                }
            }
            val editor = cache.editor()
            for (c in changes) {
                when (c) {
                    is TreeChange.Put -> {
                        val blob = c.source().use { inserter.insert(Constants.OBJ_BLOB, c.length, it) }
                        editor.add(object : DirCacheEditor.PathEdit(c.path) {
                            override fun apply(ent: org.eclipse.jgit.dircache.DirCacheEntry) {
                                ent.fileMode = FileMode.REGULAR_FILE
                                ent.setObjectId(blob)
                            }
                        })
                    }
                    is TreeChange.Delete -> editor.add(DirCacheEditor.DeletePath(c.path))
                    is TreeChange.DeleteDir -> editor.add(DirCacheEditor.DeleteTree(c.path.trimEnd('/')))
                }
            }
            editor.finish()
            val treeId = cache.writeTree(inserter)
            val parentTree = parent?.let { RevWalk(repo).use { rw -> rw.parseCommit(it).tree.id } }
            if (parentTree == treeId) return null
            val cb = CommitBuilder()
            cb.setTreeId(treeId)
            if (parent != null) cb.setParentId(parent)
            cb.author = author
            cb.committer = author
            cb.message = message
            val commitId = inserter.insert(cb)
            inserter.flush()
            updateRef(refName, commitId, expectedOld ?: repo.exactRef(refName)?.objectId ?: ObjectId.zeroId())
            return commitId
        }
    }

    /**
     * Setzt [refName] auf [target]. Mit [expectedOld] wird geprüft, dass der Branch noch dort steht
     * (null = nicht prüfen, [ObjectId.zeroId] = darf noch nicht existieren); mit [force] auch rückwärts.
     */
    fun updateRef(refName: String, target: ObjectId, expectedOld: ObjectId? = null, force: Boolean = false) {
        val update = repo.updateRef(refName)
        update.setNewObjectId(target)
        update.setForceUpdate(force)
        if (expectedOld != null) update.setExpectedOldObjectId(expectedOld)
        val result = update.update()
        check(result in setOf(RefUpdate.Result.NEW, RefUpdate.Result.FAST_FORWARD, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE)) {
            "Branch $refName konnte nicht gesetzt werden: $result"
        }
    }

    fun deleteRef(refName: String) {
        val update = repo.updateRef(refName)
        update.isForceUpdate = true
        update.delete()
    }

    fun isAncestor(ancestor: ObjectId, descendant: ObjectId): Boolean =
        RevWalk(repo).use { rw -> rw.isMergedInto(rw.parseCommit(ancestor), rw.parseCommit(descendant)) }

    fun parseCommit(id: ObjectId): RevCommit = RevWalk(repo).use { it.parseCommit(id) }

    override fun close() {
        if (repoLazy.isInitialized()) repo.close()
    }
}
