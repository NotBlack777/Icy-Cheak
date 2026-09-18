package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.SensorLiveMonitor
import com.icy.devcheckplus.model.SensorLiveData
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.MiniGraph
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateRowIndex
import com.icy.devcheckplus.ui.components.rememberIsForeground

@Composable
fun SensorsScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val monitor = remember { SensorLiveMonitor(context) }
    // Lifecycle-aware: the sensor flow stops being observed in the background,
    // and the monitor below tears its worker thread down at the same moment.
    val sensorsMap by monitor.sensorsFlow.collectAsStateWithLifecycle(
        initialValue = monitor.sensorsFlow.value
    )
    val foreground = rememberIsForeground()

    // Sampling only happens while this screen is composed AND the app is in the
    // foreground; stopListening() also tears down the sensor worker thread.
    DisposableEffect(foreground) {
        if (foreground) monitor.startListening()
        onDispose { monitor.stopListening() }
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    val sensorList = remember(sensorsMap) {
        sensorsMap.values.toList().sortedBy { it.name }
    }

    val filteredSensors = remember(sensorList, searchQuery) {
        if (searchQuery.isBlank()) sensorList else {
            sensorList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.vendor.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredSensors.isEmpty()) {
        GlassEmptyState(
            icon = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.SensorsOff,
            title = if (searchQuery.isNotBlank()) "Nothing matches \"$searchQuery\"" else "No sensors reporting",
            message = if (searchQuery.isNotBlank()) {
                "Sensors are matched on their name and their vendor."
            } else {
                "This device exposes no sensor the app can read, or the sensor service is " +
                    "unavailable on this build."
            }
        )
    } else {
        LocateMatchEffect(
            listState = listState,
            token = locateToken,
            targetIndex = locateRowIndex(
                items = filteredSensors,
                query = searchQuery,
                headerCount = 1,
                predicate = { sensor, query ->
                    sensor.name.contains(query, true) || sensor.vendor.contains(query, true)
                }
            )
        )
        LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            item(key = "sensors_header") {
                GlassSectionHeader(
                    title = "LIVE SENSORS",
                    icon = Icons.Default.Sensors,
                    supporting = "500 ms sampling"
                )
            }
            items(filteredSensors, key = { it.type }) { sensor ->
                SensorGraphCard(sensor = sensor)
            }
            item(key = "sensors_bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * One sensor card. [SensorLiveData] is [androidx.compose.runtime.Immutable] and
 * keeps its identity unless that specific sensor published a new reading, so
 * untouched cards skip recomposition entirely.
 */
@Composable
private fun SensorGraphCard(sensor: SensorLiveData) {
    val scheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(16.dp),
        frosted = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = "Vendor: ${sensor.vendor} • Power: ${sensor.power} mA",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val valuesStr = sensor.values.joinToString("  |  ") {
            String.format("%.2f", it)
        }
        Text(
            text = "Live output: [ $valuesStr ]",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        MiniGraph(
            history = sensor.history,
            lineColor = scheme.primary
        )
    }
}
