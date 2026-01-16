package com.erdene.callerinsight

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.erdene.callerinsight.data.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class CallInfoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallerInsight"
        private const val EXTRA_NUMBER = "phone_number"
    }

    private val repo = AppGraph.repo

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
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: getString(R.string.unknown_number)

        tvNumber.text = number
        tvInfo.text = getString(R.string.searching)

        val token = requestToken.incrementAndGet()
        Log.d(TAG, "handleIntent(): number=$number token=$token")

        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.analyze(number) }

                if (token != requestToken.get()) {
                    Log.d(TAG, "Ignoring stale response token=$token current=${requestToken.get()}")
                    return@launch
                }

                tvInfo.text = buildString {
                    append(getString(R.string.risk_prefix)).append(res.riskLevel)
                    if (!res.confidence.isNullOrBlank()) append(" (").append(res.confidence).append(")")
                    append("\n")
                    append(res.summary.ifBlank { getString(R.string.no_info) })
                }
            } catch (e: Exception) {
                if (token != requestToken.get()) return@launch
                Log.e(TAG, "Failed to fetch caller insight: ${e.message}", e)
                tvInfo.text = "${getString(R.string.failed_fetch)}\n${e.message}"
            }
        }
    }
}
