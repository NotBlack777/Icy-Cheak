package com.icy.icycheak

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.icy.icycheak.data.local.LocalStore
import com.icy.icycheak.data.providers.BatteryHistoryRepository
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.ticker.LiveTicker
import com.icy.icycheak.privilege.PrivilegeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IcyCheakApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        LocalStore.init(this)
        PrivilegeEngine.init(this)
        LiveTicker.init(this)
        startBatteryHistorySampler()
    }

    private fun startBatteryHistorySampler() {
        // Periodic sampler
        appScope.launch {
            recordBatterySample()
            while (true) {
                delay(15 * 60 * 1000L)
                recordBatterySample()
            }
        }
        // Also react to battery changes
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val tempC = if (temp == Int.MIN_VALUE) null else temp / 10f
                if (lvl >= 0) appScope.launch { BatteryHistoryRepository.record(lvl, plugged, tempC) }
            }
        }, filter)
    }

    private suspend fun recordBatterySample() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempC = if (temp == null || temp == Int.MIN_VALUE) null else temp / 10f
        if (lvl >= 0) runCatching { BatteryHistoryRepository.record(lvl, plugged, tempC) }
    }
}