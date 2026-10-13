package de.flauschiversum.publisher.photos

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/** Geteilte Fotos, die auf die Zuordnung warten. Die Dateien liegen im privaten App-Speicher, damit der Zugriff nicht verfällt. */
class IncomingPhotos(private val context: Context) {
    private val dir = File(context.filesDir, "incoming")
    val pending = MutableStateFlow<List<SharedPhoto>>(emptyList())

    /** Ob [intent] Bilder zum Teilen enthält. */
    fun isShare(intent: Intent?): Boolean =
        intent != null && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) &&
            (intent.type?.startsWith("image/") == true) && uris(intent).isNotEmpty()

    @Suppress("DEPRECATION")
    private fun uris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
        else -> emptyList()
    }

    /** Kopiert die geteilten Bilder in den App-Speicher und merkt sie vor. Einzelne unlesbare Bilder werden übersprungen. */
    fun receive(intent: Intent): Int {
        clear()
        dir.mkdirs()
        val resolver = context.contentResolver
        val copied = uris(intent).mapIndexedNotNull { i, uri -> copy(resolver, uri, i) }
        pending.value = copied
        return copied.size
    }

    private fun copy(resolver: ContentResolver, uri: Uri, index: Int): SharedPhoto? = runCatching {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
        val target = File(dir, "$index-${System.nanoTime()}")
        resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        SharedPhoto(target, name, resolver.getType(uri))
    }.getOrNull()

    /** Verwirft die vorgemerkten Bilder samt Kopien. */
    fun clear() {
        pending.value = emptyList()
        dir.listFiles()?.forEach { it.delete() }
    }
}
