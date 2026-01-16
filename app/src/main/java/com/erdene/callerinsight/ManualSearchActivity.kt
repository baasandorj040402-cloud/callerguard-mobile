package com.erdene.callerinsight

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.erdene.callerinsight.data.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManualSearchActivity : AppCompatActivity() {

    private val repo by lazy { AppGraph.repo }

    private lateinit var etPhoneNumber: EditText
    private lateinit var btnSearch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var layoutManualWebSearch: LinearLayout
    private lateinit var tvManualWebTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_search)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnSearch = findViewById(R.id.btnSearch)
        progressBar = findViewById(R.id.progressBar)
        tvResult = findViewById(R.id.tvResult)
        layoutManualWebSearch = findViewById(R.id.layoutManualWebSearch)
        tvManualWebTitle = findViewById(R.id.tvManualWebTitle)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnSearch.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotBlank()) {
                searchNumber(phoneNumber)
            }
        }
    }

    private fun searchNumber(number: String) {
        progressBar.visibility = View.VISIBLE
        tvResult.visibility = View.GONE
        layoutManualWebSearch.visibility = View.GONE
        btnSearch.isEnabled = false

        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.analyze(number) }

                tvResult.text = buildString {
                    append(res.summary)
                    if (!res.confidence.isNullOrBlank()) {
                        append("\n\n(Нарийвчлал: ").append(res.confidence).append(")")
                    }
                }
                tvResult.visibility = View.VISIBLE

                // Handle Top Search Result
                if (!res.webLink.isNullOrBlank()) {
                    tvManualWebTitle.text = res.webTitle ?: "Үр дүн олдлоо"
                    layoutManualWebSearch.visibility = View.VISIBLE
                    layoutManualWebSearch.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.webLink))
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }

            } catch (e: Exception) {
                Log.e("ManualSearchActivity", "Хайлт амжилтгүй: ${e.message}", e)
                tvResult.text = "Хайлт амжилтгүй боллоо: ${e.message}"
                tvResult.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
                btnSearch.isEnabled = true
            }
        }
    }
}
