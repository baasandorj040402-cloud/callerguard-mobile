package com.erdene.callerinsight

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.erdene.callerinsight.Constants.BACKEND_URL
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class CallOverlayService : Service() {

    companion object {
        private const val TAG = "CallerInsight"
        private const val EXTRA_NUMBER = "phone_number"

        // Emulator: http://10.0.2.2:8000/analyze
        // Real phone: change to your Mac LAN IP like http://192.168.0.50:8000/analyze

        private const val CHECK_MS = 350L
        private const val STREAK_THRESHOLD = 3 // ~1 second

        fun show(context: Context, number: String) {
            val i = Intent(context, CallOverlayService::class.java).apply {
                putExtra(EXTRA_NUMBER, number)
            }
            context.startService(i)
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, CallOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var tm: TelephonyManager

    private var overlayView: View? = null
    private var tvNumber: TextView? = null
    private var tvSummary: TextView? = null

    private val requestToken = AtomicInteger(0)

    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null

    private val handler = Handler(Looper.getMainLooper())

    // Adjust as needed for your device
    private val dialerPackages = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui"
    )

    private var dialerStreak = 0

    private val visibilityWatcher = object : Runnable {
        override fun run() {
            // Only while ringing
            val state = tm.callState
            if (state != TelephonyManager.CALL_STATE_RINGING) {
                stopSelf()
                return
            }

            // If usage access not granted -> HIDE (so you won't see it on YouTube)
            if (!hasUsageAccess()) {
                overlayView?.visibility = View.GONE
                handler.postDelayed(this, CHECK_MS)
                return
            }

            val fg = getTopPackageByUsageStats()
            val isDialerTop = fg != null && fg in dialerPackages

            if (isDialerTop) dialerStreak++ else dialerStreak = 0
            val shouldShow = dialerStreak >= STREAK_THRESHOLD

            overlayView?.visibility = if (shouldShow) View.VISIBLE else View.GONE

            // Debug: see what package it thinks is top
            Log.d(TAG, "Top=$fg dialerStreak=$dialerStreak show=$shouldShow")

            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tm = getSystemService(TelephonyManager::class.java)

        registerCallStateListener()
        handler.post(visibilityWatcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_NUMBER) ?: return START_NOT_STICKY
        showOrUpdate(number)
        return START_STICKY
    }

    private fun showOrUpdate(number: String) {
        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_summary, null)

            tvNumber = overlayView!!.findViewById(R.id.tvOverlayNumber)
            tvSummary = overlayView!!.findViewById(R.id.tvOverlaySummary)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Center/middle
                gravity = Gravity.CENTER
                x = 0
                y = 0
            }

            wm.addView(overlayView, params)
        }

        // Start hidden until dialer is really foreground
        overlayView?.visibility = View.GONE
        dialerStreak = 0

        tvNumber?.text = number
        tvSummary?.text = "Хайж байна…"

        val token = requestToken.incrementAndGet()
        Thread {
            val result = fetchCallerInsight(number)
            if (token != requestToken.get()) return@Thread

            tvSummary?.post {
                if (token == requestToken.get()) {
                    tvSummary?.text = result
                }
            }
        }.start()
    }

    private fun onCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> { /* keep */ }
            TelephonyManager.CALL_STATE_OFFHOOK -> stopSelf()
            TelephonyManager.CALL_STATE_IDLE -> stopSelf()
        }
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    this@CallOverlayService.onCallStateChanged(state)
                }
            }
            telephonyCallback = cb
            tm.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    this@CallOverlayService.onCallStateChanged(state)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun getTopPackageByUsageStats(): String? {
        return try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 10_000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
            if (stats.isNullOrEmpty()) return null
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchCallerInsight(number: String): String {
        return try {
            val url = URL(BACKEND_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().put("phone_number", number).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }

            if (code !in 200..299) return "Backend error ($code)"

            val json = JSONObject(resp)
            val risk = json.optString("risk_level", "unknown")
            val summary = json.optString("summary", "No info")
            val confidence = json.optString("confidence", "")

            if (confidence.isNotBlank()) "Эрсдэл: $risk ($confidence)\n$summary"
            else "Эрсдэл: $risk\n$summary"
        } catch (e: Exception) {
            "Failed to fetch info"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(visibilityWatcher)
        unregisterCallStateListener()

        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        tvNumber = null
        tvSummary = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
