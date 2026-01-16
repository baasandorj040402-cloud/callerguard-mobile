package com.erdene.callerinsight

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class CallerInsightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load saved theme globally for the whole app process
        val sharedPref = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
