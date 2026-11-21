package com.duoc.prestaaula.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminDashboardViewModel

private data class Kpi(val label: String, val value: Int, val color: Color, val icon: ImageVector)

@Composable
fun AdminDashboardScreen(onOpenBlocks: () -> Unit, onOpenUsers: () -> Unit, onOpenAudit: () -> Unit) {
    val vm = appViewModel { AdminDashboardViewModel(it.equipmentRepository, it.loanRepository, it.adminRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Panel de administración") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("Equipos") }
            item {
                KpiRow(
                    Kpi("Registrados", ui.totalEquipment, StatusColors.Info, Icons.Default.Inventory2),
                    Kpi("Disponibles", ui.available, StatusColors.Success, Icons.Default.CheckCircle)
                )
            }
            item {
                KpiRow(
                    Kpi("Prestados", ui.lent, StatusColors.Warning, Icons.Default.Output),
                    Kpi("En mantención", ui.maintenance, StatusColors.Danger, Icons.Default.Build)
                )
            }
            item {
                KpiRow(
                    Kpi("Bloqueados", ui.blocked, StatusColors.Danger, Icons.Default.Block),
                    Kpi("Retirados", ui.retired, StatusColors.Neutral, Icons.Default.Archive)
                )
            }

            item { SectionTitle("Reservas") }
            item {
                KpiRow(
                    Kpi("Total realizadas", ui.totalLoans, StatusColors.Info, Icons.Default.EventAvailable),
                    Kpi("Reservadas", ui.reserved, StatusColors.Info, Icons.Default.Schedule)
                )
            }
            item {
                KpiRow(
                    Kpi("Retiradas", ui.pickedUp, StatusColors.Warning, Icons.Default.Output),
                    Kpi("Devueltas", ui.returned, StatusColors.Success, Icons.Default.AssignmentReturn)
                )
            }
            item { KpiRow(Kpi("Canceladas", ui.cancelled, StatusColors.Neutral, Icons.Default.Cancel)) }

            item { SectionTitle("Usuarios") }
            item {
                KpiRow(
                    Kpi("Registrados", ui.totalUsers, StatusColors.Info, Icons.Default.Groups),
                    Kpi("Suspendidos", ui.suspendedUsers, StatusColors.Danger, Icons.Default.Warning)
                )
            }

            item { SectionTitle("Accesos") }
            item {
                OutlinedButton(onClick = onOpenBlocks, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Block, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bloqueos de equipos")
                }
            }
            item {
                OutlinedButton(onClick = onOpenUsers, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Usuarios")
                }
            }
            item {
                OutlinedButton(onClick = onOpenAudit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Historial de acciones (auditoría)")
                }
            }

            item { SectionTitle("Equipos más reservados") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.mostReserved.isEmpty()) {
                            Text("Aún no hay reservas registradas.", style = MaterialTheme.typography.bodySmall)
                        }
                        ui.mostReserved.forEachIndexed { index, m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text("${m.name} (${m.code})", modifier = Modifier.weight(1f))
                                Text(
                                    "${m.total} ${if (m.total == 1) "reserva" else "reservas"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Actividad reciente") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (ui.recent.isEmpty()) {
                            Text("Aún no hay actividad.", style = MaterialTheme.typography.bodySmall)
                        }
                        ui.recent.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${d.equipmentName} (${d.equipmentCode})", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${d.teacherName} · ${DateUtils.format(d.loan.dateEpochDay)} · ${TimeBlocks.label(d.loan.block)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusChip(d.loan.status.label, d.loan.status.color())
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun KpiRow(vararg items: Kpi) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { k -> KpiCard(k, Modifier.weight(1f)) }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun KpiCard(k: Kpi, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = k.color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(14.dp)) {
            Icon(k.icon, contentDescription = null, tint = k.color)
            Spacer(Modifier.height(6.dp))
            Text(k.value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = k.color)
            Text(k.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
