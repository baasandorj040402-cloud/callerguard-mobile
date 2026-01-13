package com.erdene.callerinsight

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

        try {
            // Show overlay on top of the call screen
            CallOverlayService.show(this, number)
            Log.d(TAG, "✅ Overlay requested")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay: ${e.message}", e)
        }

        // Don’t block the call
        val response = CallResponse.Builder().build()
        respondToCall(callDetails, response)
    }
}
