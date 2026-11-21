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
import com.duoc.prestaaula.domain.usage.UsagePeriod
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.viewmodel.UtpDashboardViewModel

@Composable
fun UtpDashboardScreen() {
    val vm = appViewModel { UtpDashboardViewModel(it.loanRepository, it.equipmentRepository, it.usageRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Uso de los equipos") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UsagePeriod.entries.toList()) { p ->
                        FilterChip(
                            selected = ui.period == p,
                            onClick = { vm.onPeriodChange(p) },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UtpKpiCard("Reservas", ui.total, StatusColors.Info, Icons.Default.EventAvailable, Modifier.weight(1f))
                    UtpKpiCard("Devueltas", ui.returned, StatusColors.Success, Icons.Default.AssignmentReturn, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UtpKpiCard("Incidencias", ui.incidents, StatusColors.Danger, Icons.Default.ReportProblem, Modifier.weight(1f))
                    UtpKpiCard("Por devolver", ui.pendingReturns, StatusColors.Warning, Icons.Default.Schedule, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Reservas por semana") }
            item { UtpCountTable(ui.weekly, "reservas", "Sin reservas en este período.") }

            item { SectionTitle("Bloques más usados") }
            item { UtpCountTable(ui.byBlock, "reservas", "Sin reservas en este período.") }

            item { SectionTitle("Equipos más usados") }
            item { UtpRankTable(ui.topEquipment, "Sin reservas en este período.") }

            item { SectionTitle("Tipos de equipo más solicitados") }
            item { UtpCountTable(ui.types, "reservas", "Sin reservas en este período.") }

            item { SectionTitle("Uso por curso") }
            item { UtpCountTable(ui.courses, "reservas", "Sin reservas en este período.") }

            item { SectionTitle("Uso por docente") }
            if (ui.teachers.isEmpty()) {
                item { EmptyState(Icons.Default.Groups, "Sin docentes", "Aún no hay docentes registrados.") }
            }
            items(ui.teachers, key = { "t${it.teacherId}" }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Devueltas: ${t.returned} · Canceladas: ${t.cancelled} · Con incidencia: ${t.incidents}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${t.total} ${if (t.total == 1) "reserva" else "reservas"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
