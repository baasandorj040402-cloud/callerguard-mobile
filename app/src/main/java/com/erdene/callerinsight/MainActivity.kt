package com.erdene.callerinsight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.erdene.callerinsight.ManualSearchActivity

class MainActivity : AppCompatActivity() {

    private val requestPhoneStatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnManualSearch: Button = findViewById(R.id.btnManualSearch)
        btnManualSearch.setOnClickListener {
            startActivity(Intent(this, ManualSearchActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }
}
