package com.erdene.callerinsight

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateStatus() }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnEnableCallScreening).setOnClickListener {
            requestCallScreeningRole()
        }

        findViewById<Button>(R.id.btnEnableOverlay).setOnClickListener {
            openOverlaySettings()
        }

        findViewById<Button>(R.id.btnEnableUsageAccess).setOnClickListener {
            openUsageAccessSettings()
        }

        ensureNotificationPermission()
        ensureReadPhoneStatePermission()
        ensureCallLogPermission()
        ensureContactsPermission()

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureReadPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun ensureCallLogPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun ensureContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun hasCallScreeningRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
    }

    private fun updateStatus() {
        val status = buildString {
            append("Төлөв:\n")
            append("• Call Screening: ").append(if (hasCallScreeningRole()) "✅" else "❌").append("\n")
            append("• Overlay эрх: ").append(if (hasOverlayPermission()) "✅" else "❌").append("\n")
            append("• Usage Access: ").append(if (hasUsageAccess()) "✅" else "❌").append("\n")
            append("• Phone State: ").append(if (ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) "✅" else "❌").append("\n")
            append("• Call Log эрх: ").append(if (ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) "✅" else "❌").append("\n")
            append("• Contacts эрх: ").append(if (ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) "✅" else "❌")
        }
        tvStatus.text = status
    }
}
