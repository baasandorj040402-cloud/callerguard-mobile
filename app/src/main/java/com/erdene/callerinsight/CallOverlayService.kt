package com.erdene.callerinsight

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.erdene.callerinsight.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

class CallOverlayService : Service() {

    private val repo by lazy { AppGraph.repo }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "CallerInsight"
        private const val EXTRA_NUMBER = "phone_number"
        private const val AUTO_DISMISS_MS = 60_000L

        fun show(context: Context, number: String) {
            val i = Intent(context, CallOverlayService::class.java).apply {
                putExtra(EXTRA_NUMBER, number)
            }
            try {
                context.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallOverlayService: ${e.message}", e)
            }
        }
    }

    private lateinit var wm: WindowManager
    private var overlayView: View? = null
    private var tvNumber: TextView? = null
    private var tvSummary: TextView? = null

    private val requestToken = AtomicInteger(0)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: Any? = null

    private val autoDismissRunnable = Runnable {
        Log.d(TAG, "Auto dismiss overlay")
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        setupPhoneStateListener()
    }

    private fun setupPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        stopSelf()
                    }
                }
            }
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            telephonyCallback = callback
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        stopSelf()
                    }
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            telephonyCallback = listener
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_NUMBER) ?: return START_NOT_STICKY

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        showOrUpdate(number)
        return START_NOT_STICKY
    }

    private fun getContactName(number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            var contactName: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                cursor.close()
            }
            contactName
        } catch (_: Exception) {
            null
        }
    }

    private fun getCarrierName(number: String): String {
        val cleanNumber = number.replace("+976", "").replace(" ", "").replace("-", "")
        if (cleanNumber.length < 2) return ""
        val prefix = if (cleanNumber.startsWith("0")) cleanNumber.substring(1, 3) else cleanNumber.substring(0, 2)
        return when (prefix) {
            "99", "95", "94", "85", "75" -> "Mobicom"
            "88", "89", "86", "80", "70" -> "Unitel"
            "91", "90", "96", "66" -> "Skytel"
            "98", "97", "93", "83" -> "G-Mobile"
            else -> ""
        }
    }

    private fun getCallHistorySummary(number: String): String {
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE),
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number),
                null
            )
            if (cursor == null) return ""
            var total = 0; var missed = 0; var answered = 0
            while (cursor.moveToNext()) {
                total++
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                when (type) {
                    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> missed++
                    CallLog.Calls.INCOMING_TYPE, CallLog.Calls.OUTGOING_TYPE -> answered++
                }
            }
            cursor.close()
            return if (total == 0) getString(R.string.new_number_history)
            else getString(R.string.call_history_summary, total, answered, missed)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun saveToHistory(number: String, summary: String) {
        val sharedPref = getSharedPreferences("app_history", MODE_PRIVATE)
        val historyJson = sharedPref.getString("recent_calls", "[]") ?: "[]"
        try {
            val array = JSONArray(historyJson)
            val newList = mutableListOf<String>()
            val newItem = "$number|$summary|${System.currentTimeMillis()}"
            newList.add(newItem)
            for (i in 0 until array.length()) {
                val item = array.getString(i)
                if (!item.startsWith("$number|") && newList.size < 20) {
                    newList.add(item)
                }
            }
            sharedPref.edit().putString("recent_calls", JSONArray(newList).toString()).apply()
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdate(number: String) {
        if (overlayView == null) {
            val sharedPref = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val isDarkMode = sharedPref.getBoolean("dark_mode", false)
            val config = Configuration(resources.configuration)
            config.uiMode = if (isDarkMode) (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            else (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO

            val themedContext = createConfigurationContext(config)
            
            @Suppress("InflateParams")
            overlayView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_summary, null)

            val cardView = overlayView!!.findViewById<View>(R.id.card)
            tvNumber = overlayView!!.findViewById(R.id.tvOverlayNumber)
            tvSummary = overlayView!!.findViewById(R.id.tvOverlaySummary)
            val btnClose = overlayView!!.findViewById<ImageButton>(R.id.btnClose)

            if (isDarkMode) {
                cardView.backgroundTintList = ColorStateList.valueOf("#E61E1E1E".toColorInt())
                tvNumber?.setTextColor(Color.WHITE)
                tvSummary?.setTextColor("#EEEEEE".toColorInt())
                btnClose.setColorFilter(Color.WHITE)
            } else {
                cardView.backgroundTintList = ColorStateList.valueOf("#F2FFFFFF".toColorInt())
                tvNumber?.setTextColor("#111111".toColorInt())
                tvSummary?.setTextColor("#222222".toColorInt())
                btnClose.setColorFilter("#111111".toColorInt())
            }

            btnClose.setOnClickListener { stopSelf() }

            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            overlayView!!.setOnTouchListener(object : View.OnTouchListener {
                private var initialY: Int = 0
                private var initialTouchY: Float = 0f
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialY = params.y
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            wm.updateViewLayout(overlayView, params)
                            return true
                        }
                    }
                    return false
                }
            })

            try { wm.addView(overlayView, params) } catch (_: Exception) { stopSelf(); return }
        }

        handler.removeCallbacks(autoDismissRunnable)
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)

        val contactName = getContactName(number)
        val carrier = getCarrierName(number)
        val displayName = if (contactName != null) "$contactName ($number)" else if (carrier.isNotEmpty()) "$number ($carrier)" else number
        tvNumber?.text = displayName
        
        val historyText = getCallHistorySummary(number)
        tvSummary?.text = getString(R.string.searching_with_history, historyText)

        val token = requestToken.incrementAndGet()
        serviceScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.analyze(number) }
                if (token != requestToken.get()) return@launch
                saveToHistory(number, res.summary)
                tvSummary?.text = if (!res.confidence.isNullOrBlank()) getString(R.string.summary_with_confidence, res.summary, historyText, res.confidence)
                else getString(R.string.summary_basic, res.summary, historyText)
            } catch (_: Exception) {
                if (token == requestToken.get()) tvSummary?.text = getString(R.string.error_with_history, historyText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoDismissRunnable)
        serviceScope.cancel()
        unregisterTelephony()
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    @Suppress("DEPRECATION")
    private fun unregisterTelephony() {
        val callback = telephonyCallback ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback is TelephonyCallback) telephonyManager.unregisterTelephonyCallback(callback)
        else if (callback is android.telephony.PhoneStateListener) telephonyManager.listen(callback, android.telephony.PhoneStateListener.LISTEN_NONE)
        telephonyCallback = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
