package com.erdene.callerinsight.data

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class CallerInsightRepository(
    private val client: CallerInsightClient,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private data class CacheEntry(val value: CallerInsight, val savedAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inflight = ConcurrentHashMap<String, Deferred<CallerInsight>>()

    private val ttlMs = 7L * 24 * 60 * 60 * 1000 // 7 days

    fun getCached(numberRaw: String): CallerInsight? {
        val number = normalize(numberRaw)
        val e = cache[number] ?: return null
        if (System.currentTimeMillis() - e.savedAtMs > ttlMs) return null
        return e.value
    }

    suspend fun analyze(numberRaw: String): CallerInsight = coroutineScope {
        val number = normalize(numberRaw)

        getCached(number)?.let { return@coroutineScope it }

        inflight[number]?.let { return@coroutineScope it.await() }

        val job = async(io) {
            val res = client.analyze(number)
            cache[number] = CacheEntry(res, System.currentTimeMillis())
            res
        }
        inflight[number] = job
        try {
            job.await()
        } finally {
            inflight.remove(number)
        }
    }

    private fun normalize(n: String): String =
        n.trim().replace("""[^\d+]""".toRegex(), "")
}
