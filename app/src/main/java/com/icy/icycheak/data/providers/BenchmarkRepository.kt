package com.icy.icycheak.data.providers

import android.content.Context
import com.icy.icycheak.data.local.LocalStore
import com.icy.icycheak.model.BenchmarkRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/** Optional, manual-only CPU + storage micro-benchmark with saved history. */
object BenchmarkRepository {
    private const val KEY = "benchmarks"

    fun history(): Flow<List<BenchmarkRun>> = LocalStore.jsonFlow(KEY).map { parse(it) }

    suspend fun runBenchmark(context: Context): BenchmarkRun = withContext(Dispatchers.Default) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        // Single-core compute loop (sum of sqrt) timed in ms.
        val singleStart = System.nanoTime()
        computeWork(4_000_000)
        val singleMs = (System.nanoTime() - singleStart) / 1_000_000

        // Multi-core: split the same total work across N threads.
        val multiStart = System.nanoTime()
        val perThread = 4_000_000 / cores
        val threads = (0 until cores).map {
            Thread { computeWork(perThread) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val multiMs = (System.nanoTime() - multiStart) / 1_000_000

        // Storage: write then read a temp file.
        val dir = context.cacheDir
        val file = File(dir, "icycheak_bench.tmp")
        val sizeBytes = 16L * 1024 * 1024
        val buf = ByteArray(1024 * 1024)
        var written = 0L
        val wStart = System.nanoTime()
        file.outputStream().use { out ->
            while (written < sizeBytes) {
                out.write(buf)
                written += buf.size
            }
        }
        val wMs = (System.nanoTime() - wStart) / 1_000_000
        val readStart = System.nanoTime()
        var read = 0L
        file.inputStream().use { inp ->
            while (inp.read(buf).also { if (it > 0) read += it } > 0) {}
        }
        val rMs = (System.nanoTime() - readStart) / 1_000_000
        file.delete()

        val writeMBps = if (wMs > 0) sizeBytes / 1_000_000.0 / (wMs / 1000.0) else 0.0
        val readMBps = if (rMs > 0) read / 1_000_000.0 / (rMs / 1000.0) else 0.0

        BenchmarkRun(
            timestamp = System.currentTimeMillis(),
            singleCoreMs = singleMs.coerceAtLeast(1),
            multiCoreMs = multiMs.coerceAtLeast(1),
            readMBps = readMBps,
            writeMBps = writeMBps
        )
    }

    private fun computeWork(iterations: Int) {
        var acc = 0.0
        for (i in 0 until iterations) acc += sqrt(i.toDouble() * 1.0001)
        // prevent the JIT from eliminating the loop
        if (acc < 0) println(acc)
    }

    suspend fun save(run: BenchmarkRun) {
        val list = parse(LocalStore.getJson(KEY)).toMutableList()
        list.add(run)
        val arr = JSONArray()
        list.takeLast(50).forEach { r ->
            arr.put(JSONObject().apply {
                put("t", r.timestamp); put("s", r.singleCoreMs); put("m", r.multiCoreMs)
                put("r", r.readMBps); put("w", r.writeMBps)
            })
        }
        LocalStore.putJson(KEY, arr.toString())
    }

    suspend fun clear() = LocalStore.putJson(KEY, JSONArray().toString())

    private fun parse(json: String?): List<BenchmarkRun> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                BenchmarkRun(
                    timestamp = o.optLong("t"),
                    singleCoreMs = o.optLong("s"),
                    multiCoreMs = o.optLong("m"),
                    readMBps = o.optDouble("r"),
                    writeMBps = o.optDouble("w")
                )
            }
        }.getOrDefault(emptyList())
    }
}
