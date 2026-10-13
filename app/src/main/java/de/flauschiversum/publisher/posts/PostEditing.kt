package de.flauschiversum.publisher.posts

/** Die Felder des Frontmatters, die die App bearbeitet. */
data class PostProps(
    val title: String = "",
    val date: PostDate? = null,
    val permalink: String? = null,
    val tags: List<String> = emptyList(),
    val author: String? = null,
    val image: String? = null,
    val excerpt: String? = null,
)

/** Ein Fehler zu einem Feld, mit Text für den Nutzer. */
data class FieldError(val field: String, val message: String)

object PostEditing {
    /** Liest die bearbeitbaren Felder aus einer Beitragsdatei. */
    fun read(doc: PostDocument): PostProps = PostProps(
        title = doc.string(Fields.TITLE).orEmpty(),
        date = PostDate.parse(doc.string(Fields.DATE)),
        permalink = doc.string(Fields.PERMALINK),
        tags = doc.tags(),
        author = doc.string(Fields.AUTHOR),
        image = doc.string(Fields.IMAGE),
        excerpt = doc.string(Fields.EXCERPT),
    )

    /**
     * Schreibt geänderte Felder in die Datei. Felder, die sich gegenüber [original] nicht geändert haben, bleiben
     * Zeichen für Zeichen unverändert. Leere optionale Felder werden entfernt.
     */
    fun apply(base: PostDocument?, original: PostProps?, props: PostProps, body: String): PostDocument {
        var doc = (base ?: PostDocument("", body)).withBody(body)
        fun set(key: String, changed: Boolean, rendered: String?) { if (changed || base == null) doc = doc.withField(key, rendered) }
        set(Fields.TITLE, original?.title != props.title, Yaml.quote(props.title.trim()))
        set(Fields.PERMALINK, original?.permalink != props.permalink, props.permalink?.takeIf { it.isNotBlank() }?.let { Yaml.pathOrQuoted(it.trim()) })
        set(Fields.TAGS, original?.tags != props.tags, props.tags.takeIf { it.isNotEmpty() }?.let { Yaml.inlineList(it) })
        set(Fields.AUTHOR, original?.author != props.author, props.author?.trim()?.takeIf { it.isNotEmpty() }?.let { Yaml.plainOrQuoted(it) })
        set(Fields.DATE, original?.date != props.date, props.date?.render())
        set(Fields.IMAGE, original?.image != props.image, props.image?.takeIf { it.isNotBlank() }?.let { Yaml.quote(it) })
        set(Fields.EXCERPT, original?.excerpt != props.excerpt, props.excerpt?.trim()?.takeIf { it.isNotEmpty() }?.let { Yaml.quote(it.replace(Regex("""\s*\n\s*"""), " ")) })
        return doc
    }

    /**
     * Prüft die Felder. [allowedTags] sind die Schlagworte aus `tags.yaml`, [otherPermalinks] die Permalinks aller
     * anderen Beiträge (Titel dazu für die Meldung), [imageFiles] die Bilder im Beitragsverzeichnis.
     */
    fun validate(
        props: PostProps,
        allowedTags: Collection<String>,
        otherPermalinks: Map<String, String> = emptyMap(),
        imageFiles: Collection<String>? = null,
    ): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (props.title.isBlank()) errors += FieldError(Fields.TITLE, "Bitte einen Titel eingeben.")
        if (props.date == null) errors += FieldError(Fields.DATE, "Bitte ein gültiges Datum wählen.")
        props.permalink?.takeIf { it.isNotBlank() }?.let { p ->
            PostNaming.permalinkError(p)?.let { errors += FieldError(Fields.PERMALINK, it) } ?: otherPermalinks[p]?.let {
                errors += FieldError(Fields.PERMALINK, "Diese Adresse nutzt schon der Beitrag „$it“.")
            }
        }
        props.tags.filter { TagsFile.match(it, allowedTags) == null }.forEach {
            errors += FieldError(Fields.TAGS, "Das Schlagwort „$it“ gibt es nicht in der Liste (content/tags.yaml).")
        }
        if (imageFiles != null && !props.image.isNullOrBlank() && props.image !in imageFiles) {
            errors += FieldError(Fields.IMAGE, "Das Titelbild „${props.image}“ gibt es im Beitragsverzeichnis nicht.")
        }
        return errors
    }

    /** Bringt Schlagworte in die Schreibweise der Liste (ohne Beachtung der Groß-/Kleinschreibung gefunden). */
    fun canonicalTags(tags: List<String>, allowed: Collection<String>): List<String> =
        tags.map { TagsFile.match(it, allowed) ?: it }.distinct()
}
