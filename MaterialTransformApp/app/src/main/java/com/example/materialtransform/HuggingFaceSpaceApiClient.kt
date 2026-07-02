package com.example.materialtransform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class TransformResult {
    data class Success(val bitmap: Bitmap) : TransformResult()
    data class Failure(val message: String) : TransformResult()
}

/**
 * Client for calling our own free Hugging Face Space (Gradio app) via its
 * REST API. This uses the standard Gradio 5 HTTP protocol:
 *   1. Upload the photo to /gradio_api/upload
 *   2. Submit the job to /gradio_api/call/{api_name}
 *   3. Read the result from the event stream at
 *      /gradio_api/call/{api_name}/{event_id}
 *
 * The Space base URL follows Hugging Face's convention:
 * https://{username}-{spacename in lowercase, spaces as hyphens}.hf.space
 */
class HuggingFaceSpaceApiClient(private val spaceBaseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val apiName = "transform"

    suspend fun transform(sourceBitmap: Bitmap, materialLabel: String): TransformResult =
        withContext(Dispatchers.IO) {
            try {
                val uploadedPath = uploadImage(sourceBitmap)
                    ?: return@withContext TransformResult.Failure("آپلود عکس ناموفق بود.")

                val dataArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("path", uploadedPath)
                        put("meta", JSONObject().put("_type", "gradio.FileData"))
                    })
                    put(materialLabel)
                }
                val submitBody = JSONObject().put("data", dataArray)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val submitRequest = Request.Builder()
                    .url("$spaceBaseUrl/gradio_api/call/$apiName")
                    .post(submitBody)
                    .build()

                val eventId: String
                client.newCall(submitRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext TransformResult.Failure(
                            "خطا در ارسال درخواست (${response.code}): ${body.take(300)}"
                        )
                    }
                    eventId = JSONObject(body).optString("event_id")
                    if (eventId.isEmpty()) {
                        return@withContext TransformResult.Failure(
                            "پاسخ نامعتبر از سرور: ${body.take(300)}"
                        )
                    }
                }

                val resultRequest = Request.Builder()
                    .url("$spaceBaseUrl/gradio_api/call/$apiName/$eventId")
                    .get()
                    .build()

                client.newCall(resultRequest).execute().use { response ->
                    val streamBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext TransformResult.Failure(
                            "خطا در دریافت نتیجه (${response.code}): ${streamBody.take(300)}"
                        )
                    }

                    val imageInfo = extractImageFromEventStream(streamBody)
                        ?: return@withContext TransformResult.Failure(
                            "مدل نتیجه‌ای برنگرداند: ${streamBody.take(500)}"
                        )

                    val imageUrl = imageInfo.optString("url").ifEmpty {
                        val path = imageInfo.optString("path")
                        if (path.isNotEmpty()) "$spaceBaseUrl/gradio_api/file=$path" else ""
                    }

                    if (imageUrl.isEmpty()) {
                        return@withContext TransformResult.Failure("آدرس تصویر نتیجه پیدا نشد.")
                    }

                    val imageBytes = downloadBytes(imageUrl)
                        ?: return@withContext TransformResult.Failure("دانلود تصویر نتیجه ناموفق بود.")

                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ?: return@withContext TransformResult.Failure("پردازش تصویر نتیجه ناموفق بود.")

                    TransformResult.Success(bitmap)
                }
            } catch (e: Exception) {
                TransformResult.Failure("خطای شبکه یا پردازش: ${e.message}")
            }
        }

    private fun uploadImage(bitmap: Bitmap): String? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files",
                "photo.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$spaceBaseUrl/gradio_api/upload")
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val array = JSONArray(body)
            if (array.length() == 0) return null
            return array.getString(0)
        }
    }

    /**
     * Parses a Gradio SSE event stream and returns the JSON object
     * describing the first output file (our transformed image) from the
     * "complete" event's data array.
     */
    private fun extractImageFromEventStream(streamText: String): JSONObject? {
        val lines = streamText.lines()
        var sawComplete = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("event:")) {
                sawComplete = trimmed.substringAfter("event:").trim() == "complete"
            } else if (trimmed.startsWith("data:") && sawComplete) {
                val jsonPart = trimmed.substringAfter("data:").trim()
                return try {
                    val dataArray = JSONArray(jsonPart)
                    if (dataArray.length() > 0) dataArray.optJSONObject(0) else null
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun downloadBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }
}
