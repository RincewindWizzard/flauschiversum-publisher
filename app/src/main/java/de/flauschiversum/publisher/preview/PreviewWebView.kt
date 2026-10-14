package de.flauschiversum.publisher.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/** Regeln der Vorschau, getrennt von der WebView testbar. */
object PreviewPolicy {
    /** Ob [url] zur Vorschau gehört (Asset-Loader-Host der App). */
    fun isInternal(url: Uri): Boolean = url.host == PreviewRenderer.HOST
    /** Externe Links (Web, Mail) werden erst nach Bestätigung geöffnet. */
    fun needsConfirmation(url: Uri): Boolean = !isInternal(url) && url.scheme in setOf("http", "https", "mailto")
}

/**
 * Zeigt [html] in einer WebView ohne JavaScript. Bilder liefert [imageBytes] aus dem lokalen Klon (Pfad `/post/<dir>/<datei>`),
 * Stile und Schriften kommen aus den Assets der App; jede andere Anfrage wird abgewiesen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewWebView(html: String, imageBytes: (dir: String, file: String) -> ByteArray?, onLink: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lastHtml by remember { mutableStateOf<String?>(null) }
    var currentHtml by remember { mutableStateOf(html) }
    currentHtml = html
    val loader = remember { buildPreviewLoader(context, { currentHtml }, imageBytes) }
    AndroidView(
        modifier = modifier.testTag("preview-web"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                            ?: WebResourceResponse("text/plain", "utf-8", 403, "Blockiert", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (PreviewPolicy.isInternal(url) && !url.fragment.isNullOrEmpty()) return false // Vollbild öffnen/schließen
                        if (PreviewPolicy.needsConfirmation(url)) onLink(url)
                        return true
                    }
                }
            }
        },
        update = { web ->
            if (html != lastHtml) { lastHtml = html; web.loadUrl(PreviewRenderer.PAGE_URL) }
        },
    )
}

/**
 * Liefert die Vorschau aus dem Asset-Loader: die Seite selbst (`/page`), Stile und Schriften (`/assets/`) und die Bilder
 * des Beitrags (`/post/`). Die Seite kommt als echte Ressource und nicht über `loadDataWithBaseURL`, denn dessen
 * Hauptdokument läuft in neueren WebViews ebenfalls durch `shouldInterceptRequest` und würde dort abgewiesen.
 */
internal fun buildPreviewLoader(context: android.content.Context, html: () -> String, imageBytes: (dir: String, file: String) -> ByteArray?): WebViewAssetLoader =
    WebViewAssetLoader.Builder().setDomain(PreviewRenderer.HOST)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/page/") { WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html().toByteArray(Charsets.UTF_8))) }
        .addPathHandler("/post/") { path ->
            val parts = path.split('/', limit = 2)
            val bytes = if (parts.size == 2) imageBytes(Uri.decode(parts[0]), Uri.decode(parts[1])) else null
            if (bytes == null) null else WebResourceResponse(mimeOf(parts[1]), null, ByteArrayInputStream(bytes))
        }.build()

private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"; else -> "image/jpeg"
}

/** Öffnet [url] im externen Browser. */
fun openExternally(context: android.content.Context, url: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
