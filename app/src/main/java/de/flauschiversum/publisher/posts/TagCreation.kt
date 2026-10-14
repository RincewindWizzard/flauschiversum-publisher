package de.flauschiversum.publisher.posts

import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.TreeChange

sealed interface TagResult {
    /** Das Schlagwort wurde als Commit auf `draft` eingetragen. */
    data class Created(val name: String) : TagResult
    /** Es gibt das Schlagwort schon (in anderer Schreibweise oder inzwischen auf dem Server); `tags.yaml` blieb unverändert. */
    data class Existing(val name: String) : TagResult
    data class Invalid(val message: String) : TagResult
}

/** Neues Schlagwort in `content/tags.yaml` eintragen (Spec `schlagwort-anlegen`). */
object TagCreation {
    /** Schlagworte aus dem Zielbranch und aus dem aktuellen Stand (`draft`, sonst Zielbranch). */
    fun known(wf: DraftWorkflow): List<String> =
        (listOf(wf.refs.target, wf.baseRev()).distinct().flatMap { rev -> wf.store.readText(rev, TagsFile.PATH)?.let { TagsFile.names(it) }.orEmpty() }).distinct()

    fun create(wf: DraftWorkflow, rawName: String): TagResult {
        val name = rawName.trim()
        val existing = known(wf)
        TagsFile.match(name, existing)?.let { return TagResult.Existing(it) }
        TagsFile.validate(name, existing)?.let { return TagResult.Invalid(it) }
        val current = wf.store.readText(wf.baseRev(), TagsFile.PATH) ?: return TagResult.Invalid("Die Datei ${TagsFile.PATH} fehlt im Repository.")
        wf.commit(listOf(TreeChange.Put(TagsFile.PATH, TagsFile.insert(current, name).toByteArray(Charsets.UTF_8))), "Schlagwort: $name")
        return TagResult.Created(name)
    }

    /** Namen, die `draft` gegenüber dem Zielbranch neu hat (für Entwurfsliste und Sicherheitsabfrage). */
    fun newTags(wf: DraftWorkflow): List<String> {
        if (!wf.hasDraft()) return emptyList()
        val target = wf.store.readText(wf.refs.target, TagsFile.PATH)?.let { TagsFile.names(it) }.orEmpty().toSet()
        val draft = wf.store.readText(wf.refs.draft, TagsFile.PATH)?.let { TagsFile.names(it) }.orEmpty()
        return draft.filter { it !in target }
    }
}
