package com.icy.icycheak.data.providers

import com.icy.icycheak.data.local.LocalStore
import com.icy.icycheak.model.ExportRecord
import com.icy.icycheak.model.ShellScript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Saved, reusable Console command snippets. Stored locally — never synced. */
object ShellScriptStore {
    private const val KEY = "shell_scripts"

    fun scripts(): Flow<List<ShellScript>> = LocalStore.jsonFlow(KEY).map { parse(it) }

    suspend fun save(script: ShellScript) {
        val list = parse(LocalStore.getJson(KEY)).toMutableList()
        val idx = list.indexOfFirst { it.name == script.name }
        if (idx >= 0) list[idx] = script else list.add(script)
        write(list)
    }

    suspend fun delete(name: String) {
        write(parse(LocalStore.getJson(KEY)).filter { it.name != name })
    }

    private suspend fun write(list: List<ShellScript>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply { put("name", s.name); put("commands", s.commands) })
        }
        LocalStore.putJson(KEY, arr.toString())
    }

    private fun parse(json: String?): List<ShellScript> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ShellScript(name = o.optString("name"), commands = o.optString("commands"))
            }
        }.getOrDefault(emptyList())
    }
}

/** Persisted device-report exports, used by the "Compare two exports" diff view. */
object ExportStore {
    private const val KEY = "exports"

    fun exports(): Flow<List<ExportRecord>> = LocalStore.jsonFlow(KEY).map { parse(it) }

    suspend fun save(record: ExportRecord) {
        val list = parse(LocalStore.getJson(KEY)).toMutableList()
        val idx = list.indexOfFirst { it.name == record.name }
        if (idx >= 0) list[idx] = record else list.add(record)
        val arr = JSONArray()
        list.takeLast(30).forEach { r ->
            arr.put(JSONObject().apply {
                put("name", r.name); put("path", r.path); put("t", r.timestamp); put("json", r.json)
            })
        }
        LocalStore.putJson(KEY, arr.toString())
    }

    suspend fun delete(name: String) {
        val list = parse(LocalStore.getJson(KEY)).filter { it.name != name }
        val arr = JSONArray()
        list.forEach { r -> arr.put(JSONObject().apply {
            put("name", r.name); put("path", r.path); put("t", r.timestamp); put("json", r.json)
        }) }
        LocalStore.putJson(KEY, arr.toString())
    }

    suspend fun get(name: String): ExportRecord? =
        parse(LocalStore.getJson(KEY)).firstOrNull { it.name == name }

    private fun parse(json: String?): List<ExportRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ExportRecord(
                    name = o.optString("name"),
                    path = o.optString("path"),
                    timestamp = o.optLong("t"),
                    json = o.optString("json")
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Persistent console command history (local only). */
object ConsoleHistoryStore {
    private const val KEY = "console_history"

    fun history(): kotlinx.coroutines.flow.Flow<List<String>> = LocalStore.jsonFlow(KEY).map { parse(it) }

    suspend fun add(command: String) {
        if (command.isBlank()) return
        val list = parse(LocalStore.getJson(KEY)).toMutableList()
        list.remove(command)
        list.add(0, command)
        val arr = JSONArray()
        list.take(50).forEach { arr.put(it) }
        LocalStore.putJson(KEY, arr.toString())
    }

    suspend fun clear() = LocalStore.putJson(KEY, JSONArray().toString())

    private fun parse(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }
}
