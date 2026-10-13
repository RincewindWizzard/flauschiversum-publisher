package de.flauschiversum.publisher.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Bildverarbeitung mit den Bordmitteln von Android (Bitmap-Decoder, ExifInterface). */
class AndroidImageProcessor(private val tempDir: File) : ImageProcessor {

    override fun process(input: () -> InputStream, originalName: String, mimeType: String?, rights: Rights): ProcessedImage {
        val source = input().use { it.readBytes() }
        val mime = mimeType?.lowercase()
        val ext = ImageNames.extension(originalName)
        // Animierte oder unveränderbare Formate unverändert übernehmen
        if (mime == "image/gif" || ext == "gif") {
            return ProcessedImage(source, ImageNames.sanitize(originalName, ext = "gif"), 0, 0, source.size <= ImageSizing.MAX_BYTES)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(source), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageException("Das Bild „$originalName“ konnte nicht gelesen werden.")
        val orientation = readOrientation(source)
        val upright = ImageSizing.upright(bounds.outWidth, bounds.outHeight, orientation)
        val target = ImageSizing.target(upright.width, upright.height)

        val opts = BitmapFactory.Options().apply { inSampleSize = ImageSizing.sampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxOf(target.width, target.height)) }
        val decoded = BitmapFactory.decodeStream(ByteArrayInputStream(source), null, opts)
            ?: throw ImageException("Das Bild „$originalName“ konnte nicht gelesen werden.")
        val rotated = rotate(decoded, orientation)
        val scaled = if (rotated.width != target.width || rotated.height != target.height) Bitmap.createScaledBitmap(rotated, target.width, target.height, true) else rotated
        if (scaled !== rotated) rotated.recycle()

        val format = formatFor(mime, ext)
        val (bytes, _) = encode(scaled, format)
        scaled.recycle()
        val name = nameFor(originalName, format)
        val withRights = when (format) {
            Format.JPEG -> writeJpegRights(bytes, rights)
            Format.PNG, Format.WEBP -> writeExifRights(bytes, format, rights)
        }
        return ProcessedImage(withRights, name, target.width, target.height, withRights.size <= ImageSizing.MAX_BYTES)
    }

    private enum class Format(val ext: String) { JPEG("jpg"), PNG("png"), WEBP("webp") }

    private fun formatFor(mime: String?, ext: String): Format = when {
        mime == "image/png" || ext == "png" -> Format.PNG
        mime == "image/webp" || ext == "webp" -> Format.WEBP
        else -> Format.JPEG // JPEG und alles, was nur ein Decoder kennt (z. B. HEIC): als JPEG ablegen
    }

    private fun nameFor(original: String, format: Format): String {
        val clean = ImageNames.sanitize(original, ext = format.ext)
        val ext = ImageNames.extension(clean)
        val ok = when (format) { Format.JPEG -> ext in setOf("jpg", "jpeg"); Format.PNG -> ext == "png"; Format.WEBP -> ext == "webp" }
        return if (ok) clean else ImageNames.withExtension(clean, format.ext)
    }

    private fun readOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }

    /** Gibt die Bytes und zurück, ob die Größenvorgabe erreicht wurde. */
    private fun encode(bitmap: Bitmap, format: Format): Pair<ByteArray, Boolean> {
        fun compress(f: Bitmap.CompressFormat, q: Int): ByteArray = ByteArrayOutputStream().also { bitmap.compress(f, q, it) }.toByteArray()
        return when (format) {
            Format.JPEG -> {
                var last = ByteArray(0)
                for (q in ImageSizing.JPEG_QUALITIES) {
                    last = compress(Bitmap.CompressFormat.JPEG, q)
                    if (last.size <= ImageSizing.MAX_BYTES) return last to true
                }
                last to false
            }
            Format.PNG -> compress(Bitmap.CompressFormat.PNG, 100).let { it to (it.size <= ImageSizing.MAX_BYTES) }
            Format.WEBP -> {
                val lossy = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                var last = ByteArray(0)
                for (q in ImageSizing.JPEG_QUALITIES) {
                    last = compress(lossy, q)
                    if (last.size <= ImageSizing.MAX_BYTES) return last to true
                }
                last to false
            }
        }
    }

    private fun writeJpegRights(jpeg: ByteArray, rights: Rights): ByteArray {
        val withExif = writeExifRights(jpeg, Format.JPEG, rights)
        return JpegXmp.insert(withExif, rights)
    }

    /** EXIF „Artist“ und „Copyright“ über ExifInterface in eine Kopie der Datei schreiben. */
    private fun writeExifRights(bytes: ByteArray, format: Format, rights: Rights): ByteArray {
        tempDir.mkdirs()
        val tmp = File.createTempFile("exif", "." + format.ext, tempDir)
        return try {
            tmp.writeBytes(bytes)
            val exif = ExifInterface(tmp)
            exif.setAttribute(ExifInterface.TAG_ARTIST, rights.holder)
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, rights.holder)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, rights.licenseUrl)
            exif.saveAttributes()
            tmp.readBytes()
        } catch (e: Exception) {
            bytes // Format ohne EXIF-Unterstützung: Bild ohne Zusatzangaben ablegen
        } finally {
            tmp.delete()
        }
    }
}
