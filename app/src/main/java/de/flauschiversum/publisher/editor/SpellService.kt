package de.flauschiversum.publisher.editor

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Rechtschreibung der App: das eingebaute Wörterbuch wird beim ersten Bedarf im Hintergrund geladen. Zusätzlich gelten
 * die Zusatzwörter der App, die Schlagworte des Blogs, das persönliche Wörterbuch und „Ignorieren“ (nur bis zum Neustart).
 */
class SpellService(private val context: Context, private val scope: CoroutineScope, personalFile: File) {
    val personal = PersonalDictionary(personalFile)
    private val ignored: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var tagWords: Set<String> = emptySet()
    @Volatile private var extraWords: Set<String> = emptySet()

    private val engine: Deferred<SpellChecker?> by lazy {
        scope.async(Dispatchers.IO) {
            runCatching {
                extraWords = context.assets.open("dict/extra-words.txt").bufferedReader(Charsets.UTF_8).readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val e = HunspellEngine(context.assets.open("dict/de_DE.aff"), context.assets.open("dict/de_DE.dic"))
                SpellChecker(e) { w -> w in extraWords || w in tagWords || personal.contains(w) || w in ignored }
            }.getOrNull()
        }
    }

    /** Startet das Laden im Hintergrund. */
    fun warmUp() { engine }

    /** Das Wörterbuch ist geladen und lässt sich nutzen. */
    suspend fun checker(): SpellChecker? = engine.await()

    /** Wörter aus den Namen der Schlagworte (`tags.yaml`) gelten als richtig. */
    fun setTags(names: Collection<String>) {
        tagWords = names.flatMap { it.split(Regex("""[\s/&-]+""")) }.map { it.trim() }.filter { it.length > 1 }.toSet() + names.toSet()
    }

    fun ignore(word: String) { ignored += word }
}
