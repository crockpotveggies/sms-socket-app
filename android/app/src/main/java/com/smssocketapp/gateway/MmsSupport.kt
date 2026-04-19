package com.smssocketapp.gateway

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.Locale

data class OutboundMmsAttachment(
  val fileName: String,
  val mimeType: String,
  val bytes: ByteArray,
)

object MmsSupport {
  const val MAX_ATTACHMENT_BYTES = 1_000_000
  private const val IMAGE_PREVIEW_BYTES = 240_000
  private val supportedExactMimeTypes = setOf("application/pdf")
  private val supportedPrefixes = listOf("image/", "video/", "audio/")

  fun supportedMimeTypes(): Array<String> =
    arrayOf("image/*", "video/*", "audio/*", "application/pdf")

  fun isSupportedMimeType(mimeType: String): Boolean {
    val normalized = mimeType.trim().lowercase(Locale.US)
    if (normalized.isBlank()) {
      return false
    }

    return normalized in supportedExactMimeTypes ||
      supportedPrefixes.any { prefix -> normalized.startsWith(prefix) }
  }

  fun decodeOutboundAttachment(attachment: JSONObject): OutboundMmsAttachment {
    val fileName = attachment.optString("fileName").trim()
    val mimeType = attachment.optString("mimeType").trim().lowercase(Locale.US)
    val base64 = attachment.optString("base64").trim()

    require(fileName.isNotBlank()) { "attachment.fileName is required." }
    require(isSupportedMimeType(mimeType)) { "Unsupported attachment mimeType: $mimeType" }
    require(base64.isNotBlank()) { "attachment.base64 is required." }

    val decoded =
      try {
        Base64.getDecoder().decode(base64)
      } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("attachment.base64 is invalid.", error)
      }

    val normalizedBytes =
      if (mimeType.startsWith("image/")) {
        compressImageIfNeeded(decoded)
      } else {
        decoded
      }

    require(normalizedBytes.size <= MAX_ATTACHMENT_BYTES) {
      "Attachment exceeds 1000000 bytes after normalization."
    }

    return OutboundMmsAttachment(fileName = fileName, mimeType = mimeType, bytes = normalizedBytes)
  }

  fun pickAttachmentFromUri(context: Context, uri: Uri): JSONObject {
    val contentResolver = context.contentResolver
    val meta = resolveMetadata(contentResolver, uri)
    val fileName = meta.first.ifBlank { "attachment" }
    val mimeType = meta.second.ifBlank { contentResolver.getType(uri).orEmpty() }
      .lowercase(Locale.US)

    require(isSupportedMimeType(mimeType)) {
      "Unsupported attachment type: ${mimeType.ifBlank { "unknown" }}"
    }

    val originalBytes = contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
      ?: throw IllegalArgumentException("Unable to read attachment bytes.")

    val normalizedBytes =
      if (mimeType.startsWith("image/")) {
        compressImageIfNeeded(originalBytes)
      } else {
        originalBytes
      }

    require(normalizedBytes.size <= MAX_ATTACHMENT_BYTES) {
      "Attachment exceeds 1000000 bytes. Pick a smaller file."
    }

    return encodeAttachmentPayload(
      id = uri.toString(),
      fileName = fileName,
      mimeType = mimeType,
      bytes = normalizedBytes,
      includeFullBase64 = true,
    )
  }

  fun encodeAttachmentPayload(
    id: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    includeFullBase64: Boolean,
  ): JSONObject {
    val payload =
      JSONObject()
        .put("id", id)
        .put("fileName", fileName)
        .put("mimeType", mimeType)
        .put("sizeBytes", bytes.size)

    if (includeFullBase64) {
      payload.put("base64", Base64.getEncoder().encodeToString(bytes))
    }

    if (mimeType.startsWith("image/")) {
      payload.put("previewBase64", Base64.getEncoder().encodeToString(createPreview(bytes)))
    }

    return payload
  }

  fun emptyAttachments(): JSONArray = JSONArray()

  private fun resolveMetadata(contentResolver: ContentResolver, uri: Uri): Pair<String, String> {
    var displayName = ""
    var mimeType = contentResolver.getType(uri).orEmpty()

    contentResolver.query(
      uri,
      arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
      null,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0) {
          displayName = cursor.getString(nameIndex).orEmpty()
        }
      }
    }

    if (mimeType.isBlank()) {
      val candidate = displayName.substringAfterLast('.', missingDelimiterValue = "")
      mimeType =
        when (candidate.lowercase(Locale.US)) {
          "jpg", "jpeg" -> "image/jpeg"
          "png" -> "image/png"
          "gif" -> "image/gif"
          "webp" -> "image/webp"
          "mp4", "3gp", "webm" -> "video/mp4"
          "mp3", "wav", "m4a", "aac" -> "audio/mpeg"
          "pdf" -> "application/pdf"
          else -> ""
        }
    }

    return displayName to mimeType
  }

  private fun createPreview(bytes: ByteArray): ByteArray {
    if (bytes.size <= IMAGE_PREVIEW_BYTES) {
      return bytes
    }

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val resized = resizeBitmap(bitmap, 768)
    val output = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 70, output)
    return output.toByteArray()
  }

  private fun compressImageIfNeeded(bytes: ByteArray): ByteArray {
    if (bytes.size <= MAX_ATTACHMENT_BYTES) {
      return bytes
    }

    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    var bitmap = resizeBitmap(decoded, 1600)
    var quality = 82
    var best = bytes

    while (quality >= 38) {
      val output = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
      val candidate = output.toByteArray()
      best = candidate
      if (candidate.size <= MAX_ATTACHMENT_BYTES) {
        return candidate
      }

      bitmap = resizeBitmap(bitmap, (bitmap.width.coerceAtLeast(bitmap.height) * 0.82f).toInt())
      quality -= 8
    }

    return best
  }

  private fun resizeBitmap(source: Bitmap, maxDimension: Int): Bitmap {
    val currentMax = source.width.coerceAtLeast(source.height)
    if (currentMax <= maxDimension) {
      return source
    }

    val scale = maxDimension.toFloat() / currentMax.toFloat()
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
  }
}
