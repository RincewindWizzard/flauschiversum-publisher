package de.flauschiversum.publisher.images

import java.io.ByteArrayOutputStream

/** Rechteangaben für abgelegte Bilder (Urheber, Rechteinhaber, Lizenzhinweis). */
data class Rights(val holder: String, val licenseUrl: String)

/** Schreibt XMP-Angaben in JPEG-Dateien (EXIF übernimmt `ExifInterface`). */
object JpegXmp {
    private const val HEADER = "http://ns.adobe.com/xap/1.0/\u0000"

    fun packet(rights: Rights): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return """<?xpacket begin="${'﻿'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:xmpRights="http://ns.adobe.com/xap/1.0/rights/">
   <dc:creator><rdf:Seq><rdf:li>${esc(rights.holder)}</rdf:li></rdf:Seq></dc:creator>
   <dc:rights><rdf:Alt><rdf:li xml:lang="x-default">${esc(rights.holder)}</rdf:li></rdf:Alt></dc:rights>
   <xmpRights:WebStatement>${esc(rights.licenseUrl)}</xmpRights:WebStatement>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
    }

    /** Fügt das XMP-Paket nach SOI und etwaigen APP0/APP1-Segmenten (JFIF, EXIF) ein; ersetzt ein vorhandenes. */
    fun insert(jpeg: ByteArray, rights: Rights): ByteArray {
        require(jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "Keine JPEG-Datei" }
        val payload = (HEADER + packet(rights)).toByteArray(Charsets.UTF_8)
        require(payload.size + 2 <= 65535) { "XMP-Paket zu groß" }
        val out = ByteArrayOutputStream(jpeg.size + payload.size + 8)
        out.write(jpeg, 0, 2)
        var pos = 2
        // vorhandene Kopf-Segmente (APP0, APP1) behalten, außer einem schon vorhandenen XMP-Segment
        while (pos + 4 <= jpeg.size && jpeg[pos] == 0xFF.toByte()) {
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker != 0xE0 && marker != 0xE1) break
            val len = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            val isXmp = marker == 0xE1 && String(jpeg, pos + 4, minOf(29, len - 2), Charsets.ISO_8859_1).startsWith("http://ns.adobe.com/xap/1.0/")
            if (!isXmp) out.write(jpeg, pos, len + 2)
            pos += len + 2
        }
        val segLen = payload.size + 2
        out.write(0xFF); out.write(0xE1); out.write(segLen shr 8); out.write(segLen and 0xFF)
        out.write(payload)
        out.write(jpeg, pos, jpeg.size - pos)
        return out.toByteArray()
    }

    /** Liest das XMP-Paket einer JPEG-Datei (für Tests); null, wenn keines vorhanden ist. */
    fun read(jpeg: ByteArray): String? {
        var pos = 2
        while (pos + 4 <= jpeg.size && jpeg[pos] == 0xFF.toByte()) {
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xDA) break
            val len = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            if (marker == 0xE1) {
                val text = String(jpeg, pos + 4, len - 2, Charsets.UTF_8)
                if (text.startsWith("http://ns.adobe.com/xap/1.0/")) return text.removePrefix(HEADER)
            }
            pos += len + 2
        }
        return null
    }
}
