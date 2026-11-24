package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class PickedAttachment(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64: String,
)

object AttachmentUtils {
    fun compressImageToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 960,
        maxBase64Length: Int = 200_000,
    ): PickedAttachment? = runCatching {
        val name = queryDisplayName(context, uri) ?: "image.jpg"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > maxDimension * 2) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val ratio = maxDimension.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else decoded

        var quality = 82
        var encoded: String
        var byteCount: Int
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            byteCount = bytes.size
            encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            quality -= 12
        } while (encoded.length > maxBase64Length && quality >= 30)

        if (encoded.length > maxBase64Length) return null
        PickedAttachment(
            name = name,
            mimeType = "image/jpeg",
            sizeBytes = byteCount.toLong(),
            base64 = encoded
        )
    }.getOrNull()

    fun readPdfToBase64(
        context: Context,
        uri: Uri,
        maxBase64Length: Int = 440_000,
    ): Result<PickedAttachment> = runCatching {
        val name = queryDisplayName(context, uri) ?: "document.pdf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read selected file.")
        if (bytes.isEmpty()) error("Selected file is empty.")
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (encoded.length > maxBase64Length) {
            error("PDF is too large for cloud sync (keep under 320 KB).")
        }
        PickedAttachment(
            name = name,
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            base64 = encoded
        )
    }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun openBase64File(
        context: Context,
        fileName: String,
        mimeType: String,
        base64: String,
    ): Result<Unit> = runCatching {
        val clean = base64.substringAfter("base64,", base64)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val dir = File(context.cacheDir, "shared_docs").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open $fileName"))
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024f * 1024f))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}
