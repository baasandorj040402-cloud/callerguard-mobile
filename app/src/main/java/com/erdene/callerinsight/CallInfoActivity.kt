package com.erdene.callerinsight

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class CallInfoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallerInsight"
        private const val BACKEND_URL = "http://10.0.2.2:8000/analyze" // emulator -> your Mac
    }

    private lateinit var tvNumber: TextView
    private lateinit var tvInfo: TextView

    // Increments for each new call. Late network responses will be ignored.
    private val requestToken = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modern way (Android 8.1+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            // Optional: attempt to dismiss keyguard if possible
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
            // Legacy flags for older Android
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_call_info)

        tvNumber = findViewById(R.id.tvNumber)
        tvInfo = findViewById(R.id.tvInfo)

        handleIntent(intent)
    }

    // This is the key for “next call comes in while Activity already open”
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val number = intent.getStringExtra("phone_number") ?: getString(R.string.unknown_number)

        // Update UI instantly so it doesn’t look stuck
        tvNumber.text = number
        tvInfo.text = getString(R.string.searching)

        // New token for this call
        val token = requestToken.incrementAndGet()
        Log.d(TAG, "handleIntent(): number=$number token=$token")

        Thread {
            val result = fetchCallerInsight(number)

            // If another call arrived while fetching, ignore this result
            if (token != requestToken.get()) {
                Log.d(TAG, "Ignoring stale response token=$token current=${requestToken.get()}")
                return@Thread
            }

            runOnUiThread {
                if (token == requestToken.get()) {
                    tvInfo.text = result
                }
            }
        }.start()
    }

    private fun fetchCallerInsight(number: String): String {
        return try {
            Log.d(TAG, "Calling backend for number=$number")

            val url = URL(BACKEND_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().put("phone_number", number).toString()
            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(TAG, "Backend responseCode=$code body=$responseText")

            if (code !in 200..299) {
                return "${getString(R.string.backend_error)} ($code)\n$responseText"
            }

            val json = JSONObject(responseText)
            val risk = json.optString("risk_level", "unknown")
            val summary = json.optString("summary", getString(R.string.no_info))
            val confidence = json.optString("confidence", "")

            buildString {
                append(getString(R.string.risk_prefix)).append(risk)
                if (confidence.isNotBlank()) append(" (").append(confidence).append(")")
                append("\n")
                append(summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch caller insight: ${e.message}", e)
            "${getString(R.string.failed_fetch)}\n${e.message}"
        }
    }
}
