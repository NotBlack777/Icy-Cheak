package com.icy.icycheak.data.providers

import com.icy.icycheak.data.local.LocalStore
import com.icy.icycheak.model.BatterySample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Rolling, device-local battery log (last 7 days) for the drain-rate graph. */
object BatteryHistoryRepository {
    private const val KEY = "battery_history"
    private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    fun samples(): Flow<List<BatterySample>> = LocalStore.jsonFlow(KEY).map { parse(it) }

    suspend fun record(level: Int, plugged: Boolean, temperatureC: Float?) {
        val list = parse(LocalStore.getJson(KEY)).toMutableList()
        val now = System.currentTimeMillis()
        val last = list.lastOrNull()
        // Avoid spamming identical samples; keep at most one per minute.
        if (last != null && last.level == level && last.plugged == plugged && now - last.timestamp < 60_000) {
            return
        }
        list.add(BatterySample(timestamp = now, level = level, plugged = plugged, temperatureC = temperatureC))
        val pruned = list.filter { now - it.timestamp <= RETENTION_MS }
        val arr = JSONArray()
        pruned.forEach { s ->
            arr.put(JSONObject().apply {
                put("t", s.timestamp); put("l", s.level); put("p", s.plugged)
                put("temp", s.temperatureC ?: JSONObject.NULL)
            })
        }
        LocalStore.putJson(KEY, arr.toString())
    }

    suspend fun clear() = LocalStore.putJson(KEY, JSONArray().toString())

    private fun parse(json: String?): List<BatterySample> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                BatterySample(
                    timestamp = o.optLong("t"),
                    level = o.optInt("l"),
                    plugged = o.optBoolean("p"),
                    temperatureC = if (o.has("temp") && !o.isNull("temp")) o.optDouble("temp").toFloat() else null
                )
            }
        }.getOrDefault(emptyList())
    }
}
