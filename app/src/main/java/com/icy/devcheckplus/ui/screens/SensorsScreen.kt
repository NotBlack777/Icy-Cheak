package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.icy.devcheckplus.data.SensorLiveMonitor
import com.icy.devcheckplus.model.SensorLiveData
import com.icy.devcheckplus.ui.components.MiniGraph

@Composable
fun SensorsScreen(
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val monitor = remember { SensorLiveMonitor(context) }
    val sensorsMap by monitor.sensorsFlow.collectAsState()

    DisposableEffect(Unit) {
        monitor.startListening()
        onDispose {
            monitor.stopListening()
        }
    }

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
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (searchQuery.isNotBlank()) "No sensors match \"$searchQuery\"" else "No hardware sensors detected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(filteredSensors) { sensor ->
                SensorGraphCard(sensor = sensor)
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SensorGraphCard(sensor: SensorLiveData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vendor: ${sensor.vendor} • Power: ${sensor.power} mA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Values readout
            val valuesStr = sensor.values.joinToString("  |  ") {
                String.format("%.2f", it)
            }
            Text(
                text = "Live Output: [ $valuesStr ]",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time canvas graph
            MiniGraph(
                history = sensor.history,
                lineColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}
