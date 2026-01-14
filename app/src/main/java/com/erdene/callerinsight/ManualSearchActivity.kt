package com.erdene.callerinsight

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.erdene.callerinsight.Constants.BACKEND_URL
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ManualSearchActivity : AppCompatActivity() {

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

        btnSearch.setOnClickListener {
            val phoneNumber = etPhoneNumber.text.toString()
            if (phoneNumber.isNotBlank()) {
                searchNumber(phoneNumber)
            }
        }
    }

    private fun searchNumber(number: String) {
        progressBar.visibility = View.VISIBLE
        tvResult.visibility = View.GONE
        btnSearch.isEnabled = false

        Thread {
            val result = fetchCallerInsight(number)
            runOnUiThread {
                progressBar.visibility = View.GONE
                tvResult.text = result
                tvResult.visibility = View.VISIBLE
                btnSearch.isEnabled = true
            }
        }.start()
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
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code !in 200..299) {
                return "Алдаа ($code): $responseText"
            }

            val json = JSONObject(responseText)
            val risk = json.optString("risk_level", "Тодорхойгүй")
            val summary = json.optString("summary", "Мэдээлэл олдсонгүй")
            val confidence = json.optString("confidence", "")

            buildString {
                append("Эрсдэл: ").append(risk)
                if (confidence.isNotBlank()) append(" (").append(confidence).append(")")
                append("\n\n")
                append(summary)
            }
        } catch (e: Exception) {
            Log.e("ManualSearchActivity", "Хайлт амжилтгүй: ${e.message}", e)
            "Хайлт амжилтгүй боллоо: ${e.message}"
        }
    }
}
