package de.flauschiversum.publisher

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.flauschiversum.publisher.preview.PreviewRenderer
import de.flauschiversum.publisher.preview.buildPreviewLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Die Vorschauseite kommt als Ressource des Asset-Loaders; sonst scheitert das Hauptdokument mit ERR_HTTP_RESPONSE_CODE_FAILURE. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewLoaderTest {
    private val loader get() = buildPreviewLoader(ApplicationProvider.getApplicationContext(), { "<p>Hallo Wolle</p>" }) { d, f -> if (d == "post" && f == "a b.jpg") byteArrayOf(1, 2) else null }

    @Test fun seiteWirdAlsHtmlAusgeliefert() {
        val r = loader.shouldInterceptRequest(Uri.parse(PreviewRenderer.PAGE_URL))
        assertNotNull(r)
        assertEquals("text/html", r!!.mimeType)
        assertEquals("<p>Hallo Wolle</p>", r.data.readBytes().toString(Charsets.UTF_8))
    }

    @Test fun seiteMitAnkerWirdAuchAusgeliefert() {
        assertNotNull(loader.shouldInterceptRequest(Uri.parse(PreviewRenderer.PAGE_URL + "#bild-1")))
    }

    @Test fun bildDesBeitragsWirdAusgeliefert() {
        val r = loader.shouldInterceptRequest(Uri.parse(PreviewRenderer.imageUrl("post", "a b.jpg")))
        assertNotNull(r); assertEquals("image/jpeg", r!!.mimeType)
    }

    @Test fun fremdeAdressenBleibenUnbeantwortet() {
        assertNull(loader.shouldInterceptRequest(Uri.parse("https://example.org/page")))
        assertNull(loader.shouldInterceptRequest(Uri.parse("https://${PreviewRenderer.HOST}/post/post/fehlt.jpg")))
    }
}
