package de.flauschiversum.publisher

import java.io.File
import org.apache.lucene.analysis.hunspell.Dictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.Test

class SpellSpikeTest {
    @Test fun spike() {
        val dir = File("src/main/assets/dict")
        val t0 = System.nanoTime()
        val dict = dir.resolve("de_DE.aff").inputStream().use { aff -> dir.resolve("de_DE.dic").inputStream().use { dic -> Dictionary(ByteBuffersDirectory(), "tmp", aff, dic) } }
        val hs = Hunspell(dict)
        val t1 = System.nanoTime()
        val rt = Runtime.getRuntime(); System.gc()
        println("SPIKE load ms=${(t1 - t0) / 1_000_000} usedMB=${(rt.totalMemory() - rt.freeMemory()) / 1_000_000}")
        for (w in listOf("wundershön", "Wolle", "Strickanleitung", "Häkelmütze", "Amigurumi", "Flauschiversum", "Kuschelkissen", "Wollknäuel", "Spindelwickeln", "häkeln", "Ermeline", "Baumärkte", "Filzen"))
            println("SPIKE $w -> ${hs.spell(w)} ${if (!hs.spell(w)) hs.suggest(w).take(5) else ""}")
        val t2 = System.nanoTime()
        repeat(2000) { hs.spell("Strickanleitung"); hs.spell("wundershön") }
        println("SPIKE 4000 checks ms=${(System.nanoTime() - t2) / 1_000_000}")
    }
}
