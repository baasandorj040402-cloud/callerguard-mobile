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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    // Багц зөвшөөрөл асуух launcher
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateStatus() }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    private lateinit var btnCallScreening: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnUsageAccess: Button
    private lateinit var btnPhoneState: Button
    private lateinit var btnCallLog: Button
    private lateinit var btnContacts: Button
    private lateinit var btnNotifications: Button
    private lateinit var btnQuickSetup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnQuickSetup = Button(this).apply { text = "Бүх үндсэн эрхийг асуух" } // Энэ товчийг Layout-д нэмнэ
        
        btnCallScreening = findViewById(R.id.btnCallScreening)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnUsageAccess = findViewById(R.id.btnUsageAccess)
        btnPhoneState = findViewById(R.id.btnPhoneState)
        btnCallLog = findViewById(R.id.btnCallLog)
        btnContacts = findViewById(R.id.btnContacts)
        btnNotifications = findViewById(R.id.btnNotifications)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Товчлуур бүр өөрийн үүргийг гүйцэтгэнэ
        btnCallScreening.setOnClickListener { requestCallScreeningRole() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnUsageAccess.setOnClickListener { openUsageAccessSettings() }
        
        // Бусад "Runtime" зөвшөөрлүүдийг нэгтгэж асуух
        val runtimePermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Аль нэг Runtime товч дээр дарахад бүгдийг нь асууна
        val runtimeListener = { requestMultiplePermissions.launch(runtimePermissions.toTypedArray()) }
        
        btnPhoneState.setOnClickListener { runtimeListener() }
        btnCallLog.setOnClickListener { runtimeListener() }
        btnContacts.setOnClickListener { runtimeListener() }
        btnNotifications.setOnClickListener { runtimeListener() }

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
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasCallScreeningRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else false
    }

    private fun updateStatus() {
        updateButtonStyle(btnCallScreening, "Call Screening", hasCallScreeningRole())
        updateButtonStyle(btnOverlay, "Overlay эрх", hasOverlayPermission())
        updateButtonStyle(btnUsageAccess, "Usage Access", hasUsageAccess())
        
        updateButtonStyle(btnPhoneState, "Phone State", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        updateButtonStyle(btnCallLog, "Call Log эрх", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
        updateButtonStyle(btnContacts, "Contacts эрх", ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
        
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        updateButtonStyle(btnNotifications, "Мэдэгдэл эрх", notifGranted)
    }

    private fun updateButtonStyle(button: Button, label: String, isEnabled: Boolean) {
        button.text = "$label: ${if (isEnabled) "Идэвхтэй" else "Унтарсан"}"
        button.setBackgroundResource(if (isEnabled) R.drawable.button_green_grad else R.drawable.button_red_grad)
    }
}
