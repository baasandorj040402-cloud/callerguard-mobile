package com.erdene.callerinsight.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CallerInsight(
    val riskLevel: String,
    val summary: String,
    val confidence: String?,
    val webLink: String? = null,
    val webTitle: String? = null
)

class CallerInsightClient(
    private val baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(phoneNumber: String): CallerInsight {
        val json = JSONObject().put("phone_number", phoneNumber).toString()
        val req = Request.Builder()
            .url(baseUrl)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val resp = client.newCall(req).await()
        resp.use {
            if (!it.isSuccessful) throw IOException("Backend error ${it.code}")
            val body = it.body?.string().orEmpty()
            val obj = JSONObject(body)
            return CallerInsight(
                riskLevel = obj.optString("risk_level", "unknown"),
                summary = obj.optString("summary", "No info"),
                confidence = if (obj.isNull("confidence")) null else obj.optString("confidence"),
                webLink = if (obj.isNull("web_link")) null else obj.optString("web_link"),
                webTitle = if (obj.isNull("web_title")) null else obj.optString("web_title")
            )
        }
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
