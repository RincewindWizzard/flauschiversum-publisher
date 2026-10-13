package de.flauschiversum.publisher.testsupport

import java.io.File

/** Echte Beiträge und Dateien des Blogs (Auszug) aus `src/test/resources/blog`. */
object Fixtures {
    private val root: File by lazy {
        val url = Fixtures::class.java.classLoader!!.getResource("blog/tags.yaml")!!
        File(url.toURI()).parentFile
    }

    fun posts(): List<String> = root.listFiles()!!.filter { File(it, "index.md").isFile }.map { it.name }.sorted()
    fun post(dir: String): String = File(root, "$dir/index.md").readText()
    fun tagsYaml(): String = File(root, "tags.yaml").readText()
    fun redirectsYaml(): String = File(root, "redirects.yaml").readText()
}
