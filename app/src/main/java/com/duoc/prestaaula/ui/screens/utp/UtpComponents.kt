package com.duoc.prestaaula.ui.screens.utp

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duoc.prestaaula.domain.usage.EquipmentUsage
import com.duoc.prestaaula.domain.usage.UsageCount

@Composable
fun UtpKpiCard(label: String, value: Int, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(6.dp))
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun UtpCountTable(rows: List<UsageCount>, unit: String, emptyText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, modifier = Modifier.weight(1f))
                    Text("${r.value} $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun UtpRankTable(rows: List<EquipmentUsage>, emptyText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
            rows.forEachIndexed { index, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Text("${r.equipment.displayName} (${r.equipment.internalCode})", modifier = Modifier.weight(1f))
                    Text(
                        "${r.total} ${if (r.total == 1) "reserva" else "reservas"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
