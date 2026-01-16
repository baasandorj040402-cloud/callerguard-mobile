package com.erdene.callerinsight.data

import android.content.Context
import org.json.JSONArray

data class HistoryItem(
    val number: String,
    val summary: String,
    val timestamp: Long
)

object SearchHistoryManager {
    private const val PREF_NAME = "app_history"
    private const val KEY_RECENT = "recent_calls"

    fun save(context: Context, number: String, summary: String) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val historyJson = sharedPref.getString(KEY_RECENT, "[]") ?: "[]"
        try {
            val array = JSONArray(historyJson)
            val newList = mutableListOf<String>()
            
            // New search to top
            val newItem = "$number|$summary|${System.currentTimeMillis()}"
            newList.add(newItem)

            // Copy others without duplicates
            for (i in 0 until array.length()) {
                val item = array.getString(i)
                if (!item.startsWith("$number|") && newList.size < 20) {
                    newList.add(item)
                }
            }
            sharedPref.edit().putString(KEY_RECENT, JSONArray(newList).toString()).apply()
        } catch (_: Exception) {}
    }

    fun getAll(context: Context): List<HistoryItem> {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val historyJson = sharedPref.getString(KEY_RECENT, "[]") ?: "[]"
        val result = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(historyJson)
            for (i in 0 until array.length()) {
                val parts = array.getString(i).split("|")
                if (parts.size >= 3) {
                    result.add(HistoryItem(parts[0], parts[1], parts[2].toLong()))
                }
            }
        } catch (_: Exception) {}
        return result
    }
}
