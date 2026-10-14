package de.flauschiversum.publisher.preview

import de.flauschiversum.publisher.editor.MarkdownLint
import de.flauschiversum.publisher.posts.PostDate
import java.net.URLEncoder
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer

/** Was die Vorschau anzeigt. */
data class PreviewInput(
    val dir: String, val title: String, val date: PostDate?, val author: String?, val tags: List<String>,
    /** Titelbild (Feld `image`); wird auf Existenz geprüft. */
    val cover: String?, val body: String,
)

class BodyHtml(val html: String, val imageCount: Int, val groupCount: Int, val captionCount: Int, val missing: List<String>)

/**
 * Vorschau eines Beitrags als HTML-Seite. Näherung an die Beitragsseite des Blogs: gleiche Markdown-Erweiterungen, Bildgruppen
 * als Karussell (CSS scroll-snap, ohne Skripte), Bildunterschrift aus dem Alt-Text.
 */
object PreviewRenderer {
    /** Adresse, unter der der Asset-Loader die Bilder des Beitrags ausliefert. */
    const val HOST = "appassets.androidplatform.net"
    val PAGE_URL get() = "https://$HOST/page/"
    fun imageUrl(dir: String, file: String) = "https://$HOST/post/${enc(dir)}/${enc(file)}"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), FootnotesExtension.create())

    fun render(input: PreviewInput, files: Collection<String>, dark: Boolean): String {
        val body = bodyHtml(input.body, input.dir, files)
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"de\"")
        if (dark) sb.append(" data-theme=\"dark\"")
        sb.append("><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        // Keine Skripte, keine Netzwerkzugriffe: nur Stile und Medien der App
        sb.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://$HOST; style-src https://$HOST 'unsafe-inline'; font-src https://$HOST\">")
        sb.append("<link rel=\"stylesheet\" href=\"https://$HOST/assets/preview/preview.css\"><title>${esc(input.title)}</title></head><body><article>")
        sb.append("<header><h1>${esc(input.title.ifBlank { "(ohne Titel)" })}</h1>")
        val meta = listOfNotNull(input.date?.date?.let { "%02d.%02d.%04d".format(it.dayOfMonth, it.monthValue, it.year) }, input.author?.takeIf { it.isNotBlank() })
        if (meta.isNotEmpty()) sb.append("<p class=\"meta\">${esc(meta.joinToString(" · "))}</p>")
        if (input.tags.isNotEmpty()) sb.append("<p class=\"tags\">${input.tags.joinToString("") { "<span>${esc(it)}</span>" }}</p>")
        if (!input.cover.isNullOrBlank() && input.cover !in files) sb.append("<p class=\"missing\">Titelbild fehlt: ${esc(input.cover)}</p>")
        sb.append("</header>").append(body.html).append("</article></body></html>")
        return sb.toString()
    }

    fun bodyHtml(markdown: String, dir: String, files: Collection<String>): BodyHtml {
        val fileSet = files.toSet()
        val doc = MarkdownLint.parse(isolateImageRuns(markdown))
        var images = 0; var groups = 0; var captions = 0
        val missing = mutableListOf<String>()
        var lightbox = 0
        val boxes = StringBuilder()

        fun figure(img: Image, slide: String = ""): String {
            val file = img.destination.replace('\u00A0', ' ')
            val alt = altOf(img)
            images++
            if (file !in fileSet) { missing += file; return "<p class=\"missing\">Bild fehlt: ${esc(file)}</p>" }
            if (alt.isNotBlank()) captions++
            val id = "lb-${++lightbox}"
            val src = imageUrl(dir, file)
            val cap = if (alt.isBlank()) "" else "<figcaption>${esc(alt)}</figcaption>"
            boxes.append("<div class=\"lightbox\" id=\"$id\"><a class=\"close\" href=\"#\">Schließen</a><img src=\"$src\" alt=\"${esc(alt)}\">${if (alt.isBlank()) "" else "<p>${esc(alt)}</p>"}</div>")
            return "<figure class=\"media\">$slide<a href=\"#$id\"><img src=\"$src\" alt=\"${esc(alt)}\"></a>$cap</figure>"
        }

        // Absätze, die nur aus Bildern bestehen, werden zu Einzelbild bzw. Karussell
        val replacements = mutableListOf<Pair<Paragraph, Node>>()
        doc.accept(object : AbstractVisitor() {
            override fun visit(p: Paragraph) {
                val kids = generateSequence(p.firstChild) { it.next }.toList()
                val imgs = kids.filterIsInstance<Image>()
                val onlyImages = imgs.isNotEmpty() && kids.all { it is Image || it is SoftLineBreak || it is HardLineBreak || (it is Text && it.literal.isBlank()) }
                if (!onlyImages) { super.visit(p); return }
                val html = StringBuilder()
                if (imgs.size == 1) html.append(figure(imgs[0]))
                else {
                    groups++
                    html.append("<div class=\"carousel\" data-count=\"${imgs.size}\"><ul class=\"carousel-track\">")
                    imgs.forEachIndexed { n, im ->
                        html.append("<li class=\"carousel-slide\" aria-label=\"Bild ${n + 1} von ${imgs.size}\"><span class=\"page\">${n + 1} / ${imgs.size}</span>")
                        html.append(figure(im)).append("</li>")
                    }
                    html.append("</ul></div>")
                }
                replacements += p to HtmlBlock().also { it.literal = html.toString() + "\n" }
            }
        })
        for ((p, block) in replacements) { p.insertBefore(block); p.unlink() }

        val attrs = AttributeProvider { node, tagName, attributes ->
            if (node is Image && tagName == "img") {
                // Bilder mitten im Satz
                attributes["src"] = imageUrl(dir, node.destination.replace('\u00A0', ' '))
            }
        }
        val html = HtmlRenderer.builder().extensions(extensions).escapeHtml(false).sanitizeUrls(true).attributeProviderFactory { attrs }.build().render(doc)
        return BodyHtml(strip(html) + boxes, images, groups, captions, missing)
    }

    private val IMAGE_ONLY = Regex("""^!\[[^\]]*]\([^)]*\)$""")

    /**
     * Wie der Blog: Zeilen, die nur aus einem Bild bestehen, stehen für sich; aufeinanderfolgende bilden eine Gruppe, auch wenn
     * davor oder danach direkt Text steht. Dafür kommt vor und nach jeder solchen Folge eine Leerzeile (außerhalb von Codeblöcken).
     */
    fun isolateImageRuns(md: String): String {
        val out = StringBuilder(); var inFence = false; var prevImage = false; var prevBlank = true
        for (line in md.split("\n")) {
            val t = line.trim()
            if (t.startsWith("```") || t.startsWith("~~~")) inFence = !inFence
            val isImage = !inFence && IMAGE_ONLY.matches(t)
            if (isImage != prevImage && !prevBlank && out.isNotEmpty()) out.append("\n")
            out.append(line).append("\n")
            prevImage = isImage; prevBlank = t.isEmpty()
        }
        return out.toString()
    }

    private fun altOf(img: Image): String {
        val sb = StringBuilder()
        var n: Node? = img.firstChild
        while (n != null) { if (n is Text) sb.append(n.literal); n = n.next }
        return sb.toString()
    }

    /** Entfernt Skripte und Ereignisattribute aus eingebettetem HTML (zusätzlich zur WebView ohne JavaScript). */
    fun strip(html: String): String = html
        .replace(Regex("(?is)<script\\b.*?</script\\s*>"), "")
        .replace(Regex("(?is)<script\\b[^>]*>"), "")
        .replace(Regex("(?is)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), "")
        .replace(Regex("(?is)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2"), "$1=\"#\"")
        // Bilder aus dem Netz werden nie geladen: eingebettete <img> mit Webadresse werden zum Platzhalter
        .replace(Regex("(?is)<img\\b[^>]*\\bsrc\\s*=\\s*[\"']?\\s*(?:https?:)?//(?!${Regex.escape(HOST)}/post/)[^>]*>"), "<p class=\"missing\">Externes Bild blockiert</p>")
}
