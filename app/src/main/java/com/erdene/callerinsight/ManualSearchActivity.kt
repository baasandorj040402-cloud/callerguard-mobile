package com.erdene.callerinsight

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_search)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnSearch = findViewById(R.id.btnSearch)
        progressBar = findViewById(R.id.progressBar)
        tvResult = findViewById(R.id.tvResult)

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
        btnSearch.isEnabled = false

        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.analyze(number) }

                tvResult.text = buildString {
                    // "Эрсдэл" хэсгийг арилгав
                    append(res.summary)
                    if (!res.confidence.isNullOrBlank()) {
                        append("\n\n(Нарийвчлал: ").append(res.confidence).append(")")
                    }
                }
            } catch (e: Exception) {
                Log.e("ManualSearchActivity", "Хайлт амжилтгүй: ${e.message}", e)
                tvResult.text = "Хайлт амжилтгүй боллоо: ${e.message}"
            } finally {
                progressBar.visibility = View.GONE
                tvResult.visibility = View.VISIBLE
                btnSearch.isEnabled = true
            }
        }
    }
}
