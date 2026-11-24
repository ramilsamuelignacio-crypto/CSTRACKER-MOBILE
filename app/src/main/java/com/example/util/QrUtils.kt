package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

sealed interface ParsedQrContent {
    data class StudentPass(val tokenHash: String) : ParsedQrContent
    data class ClassCheckIn(
        val subjectCode: String,
        val classDate: String?,
        val status: String?,
        val sessionId: String?,
        val rawPayload: String,
    ) : ParsedQrContent
}

object QrUtils {
    private val HEX_64 = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
    private val SUBJECT_CODE_RE = Regex("^[A-Z0-9]{2,20}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val JSON_STRING_FIELD_RE = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"")

    fun generateStudentTokenHash(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("cstracker-qr:$uid".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildQrPayload(tokenHash: String): String = "cstracker://student?token=$tokenHash"

    fun buildCheckInQrPayload(
        subjectCode: String,
        classDate: String,
        status: String = "Present",
        sessionId: String? = null,
    ): String {
        fun enc(v: String): String = URLEncoder.encode(v.trim(), "UTF-8")
        val sb = StringBuilder("cstracker://attendance")
            .append("?subject=").append(enc(subjectCode.trim().uppercase()))
            .append("&date=").append(enc(classDate.trim()))
            .append("&status=").append(enc(status.trim()))
        if (!sessionId.isNullOrBlank()) {
            sb.append("&session=").append(enc(sessionId.trim()))
        }
        return sb.toString()
    }

    private fun parseQueryParams(rawUri: String): Map<String, String> {
        val qIdx = rawUri.indexOf('?')
        if (qIdx < 0 || qIdx >= rawUri.lastIndex) return emptyMap()
        val query = rawUri.substring(qIdx + 1).substringBefore('#')
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val key = runCatching { URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8") }.getOrNull() ?: continue
            val value = runCatching { URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8") }.getOrNull().orEmpty()
            out[key] = value
        }
        return out
    }

    private fun parseFlatJsonFields(jsonText: String): Map<String, String> {
        val trimmed = jsonText.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        val out = LinkedHashMap<String, String>()
        JSON_STRING_FIELD_RE.findAll(trimmed).forEach { match ->
            out[match.groupValues[1]] = match.groupValues[2]
        }
        return out
    }

    fun extractTokenHash(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (HEX_64.matches(trimmed)) return trimmed.lowercase()

        if (trimmed.startsWith("cstracker://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val params = parseQueryParams(trimmed)
            val param = params["token"] ?: params["student"]
            if (param != null && HEX_64.matches(param.trim())) return param.trim().lowercase()
        }

        if (trimmed.startsWith("{")) {
            val fields = parseFlatJsonFields(trimmed)
            val hash = fields["studentIdHash"]?.ifBlank { null } ?: fields["token"].orEmpty()
            if (HEX_64.matches(hash.trim())) return hash.trim().lowercase()
        }
        return null
    }

    /**
     * Parses a QR code string into either a [ParsedQrContent.StudentPass] (64-hex token) or a
     * [ParsedQrContent.ClassCheckIn] attendance payload (URI, JSON, delimited, or subject code).
     */
    fun parseQrString(
        raw: String,
        validSubjectCodes: Set<String> = emptySet(),
    ): ParsedQrContent? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 1. Check if this is a student pass token QR
        extractTokenHash(trimmed)?.let { token ->
            return ParsedQrContent.StudentPass(token)
        }

        // 2. URI format: cstracker://attendance?subject=COSC75&date=2026-11-23&status=Present
        if (trimmed.startsWith("cstracker://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val params = parseQueryParams(trimmed)
            val subject = (params["subject"] ?: params["subjectCode"] ?: params["course"])
                ?.trim()
                ?.uppercase()
            if (!subject.isNullOrBlank() && SUBJECT_CODE_RE.matches(subject)) {
                val date = (params["date"] ?: params["classDate"])
                    ?.trim()
                    ?.takeIf { DATE_RE.matches(it) }
                val status = normalizeCheckInStatus(params["status"])
                val session = (params["session"] ?: params["sessionId"])
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                return ParsedQrContent.ClassCheckIn(
                    subjectCode = subject,
                    classDate = date,
                    status = status,
                    sessionId = session,
                    rawPayload = trimmed,
                )
            }
        }

        // 3. JSON format: {"subjectCode":"COSC75","classDate":"2026-11-23","status":"Present"}
        if (trimmed.startsWith("{")) {
            val fields = parseFlatJsonFields(trimmed)
            val subject = (fields["subjectCode"] ?: fields["subject"] ?: fields["course"])
                .orEmpty()
                .trim()
                .uppercase()
            if (SUBJECT_CODE_RE.matches(subject)) {
                val date = (fields["classDate"] ?: fields["date"])
                    .orEmpty()
                    .trim()
                    .takeIf { DATE_RE.matches(it) }
                val status = normalizeCheckInStatus(fields["status"]?.takeIf { it.isNotBlank() })
                val session = (fields["sessionId"] ?: fields["session"])
                    .orEmpty()
                    .trim()
                    .takeIf { it.isNotEmpty() }
                return ParsedQrContent.ClassCheckIn(
                    subjectCode = subject,
                    classDate = date,
                    status = status,
                    sessionId = session,
                    rawPayload = trimmed,
                )
            }
        }

        // 4. Delimited format: COSC75|2026-11-23|Present or CSTRACKER:COSC75:2026-11-23
        if ('|' in trimmed || ':' in trimmed) {
            val parts = trimmed.split('|', ':').map { it.trim() }.filter { it.isNotEmpty() }
            val filtered = if (parts.firstOrNull().equals("CSTRACKER", ignoreCase = true)) {
                parts.drop(1)
            } else {
                parts
            }
            val subject = filtered.firstOrNull()?.uppercase()
            if (!subject.isNullOrBlank() && SUBJECT_CODE_RE.matches(subject)) {
                val date = filtered.firstOrNull { DATE_RE.matches(it) }
                val status = filtered.firstNotNullOfOrNull { normalizeCheckInStatus(it) }
                return ParsedQrContent.ClassCheckIn(
                    subjectCode = subject,
                    classDate = date,
                    status = status,
                    sessionId = null,
                    rawPayload = trimmed,
                )
            }
        }

        // 5. Plain subject code (e.g., "COSC75")
        val upper = trimmed.uppercase()
        if (SUBJECT_CODE_RE.matches(upper) && (validSubjectCodes.isEmpty() || upper in validSubjectCodes)) {
            return ParsedQrContent.ClassCheckIn(
                subjectCode = upper,
                classDate = null,
                status = null,
                sessionId = null,
                rawPayload = trimmed,
            )
        }

        return null
    }

    private fun normalizeCheckInStatus(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "present" -> "Present"
        "late" -> "Late"
        else -> null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val yPlane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null

        val buffer = yPlane.buffer
        buffer.rewind()
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val packed = ByteArray(width * height)

        if (pixelStride == 1 && rowStride == width && buffer.remaining() >= width * height) {
            buffer.get(packed, 0, width * height)
        } else {
            val rowBuffer = ByteArray(rowStride)
            for (row in 0 until height) {
                val remaining = buffer.remaining()
                if (remaining <= 0) break
                val toRead = minOf(remaining, rowStride)
                buffer.get(rowBuffer, 0, toRead)
                val outOffset = row * width
                if (pixelStride == 1) {
                    val copyLen = minOf(width, toRead)
                    System.arraycopy(rowBuffer, 0, packed, outOffset, copyLen)
                } else {
                    var col = 0
                    var srcIdx = 0
                    while (col < width && srcIdx < toRead) {
                        packed[outOffset + col] = rowBuffer[srcIdx]
                        col++
                        srcIdx += pixelStride
                    }
                }
            }
        }
        return decodeFromYuv(packed, width, height)
    }

    fun decodeFromYuv(
        yuvBytes: ByteArray,
        width: Int,
        height: Int,
    ): String? {
        if (width <= 0 || height <= 0 || yuvBytes.size < width * height) return null
        val source: LuminanceSource = PlanarYUVLuminanceSource(
            yuvBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        return decodeLuminanceSource(source)
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeLuminanceSource(source)
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        return runCatching {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            QRCodeReader().decode(bitmap, hints).text
        }.getOrNull()
    }
}
