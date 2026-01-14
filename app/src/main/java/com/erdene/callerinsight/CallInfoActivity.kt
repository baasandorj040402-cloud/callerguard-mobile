package com.erdene.callerinsight

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.erdene.callerinsight.Constants.BACKEND_URL
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class CallInfoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallerInsight"
    }

    private lateinit var tvNumber: TextView
    private lateinit var tvInfo: TextView


    private val requestToken = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val number = intent.getStringExtra("phone_number") ?: getString(R.string.unknown_number)
        tvNumber.text = number
        tvInfo.text = getString(R.string.searching)
        val token = requestToken.incrementAndGet()
        Log.d(TAG, "handleIntent(): number=$number token=$token")

        Thread {
            val result = fetchCallerInsight(number)
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
