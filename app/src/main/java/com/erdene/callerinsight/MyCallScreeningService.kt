package com.erdene.callerinsight

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class MyCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallerInsight"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: "Unknown"

        Log.d(TAG, "✅ onScreenCall() triggered. number=$number")

        val i = Intent(this, CallInfoActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("phone_number", number)
        }
        startActivity(i)

        try {
            startActivity(i)
            Log.d(TAG, "✅ CallInfoActivity started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start CallInfoActivity: ${e.message}", e)
        }

        val response = CallResponse.Builder().build()
        respondToCall(callDetails, response)
    }
}
