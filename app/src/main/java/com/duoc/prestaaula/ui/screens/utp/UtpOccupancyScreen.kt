package com.duoc.prestaaula.ui.screens.utp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.UtpOccupancyViewModel

/** Qué equipos están reservados en cada bloque de un día y quién los reservó. Solo lectura. */
@Composable
fun UtpOccupancyScreen() {
    val vm = appViewModel { UtpOccupancyViewModel(it.loanRepository, it.equipmentRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Ocupación por día") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DatePickerField(
                            label = "Fecha",
                            epochDay = ui.date,
                            onDateSelected = vm::onDateChange,
                            minEpochDay = DateUtils.today() - 1825
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::previousDay) { Text("Día anterior") }
                            OutlinedButton(onClick = vm::goToday) { Text("Hoy") }
                            OutlinedButton(onClick = vm::nextDay) { Text("Día siguiente") }
                        }
                    }
                }
            }
            item { SectionTitle("Reservas activas: ${ui.total}") }

            ui.blocks.forEach { b ->
                item(key = "b${b.block.number}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(b.block.label, fontWeight = FontWeight.Bold)
                            if (b.loans.isEmpty()) {
                                Text(
                                    "Sin reservas en este bloque.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            b.loans.forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${d.equipmentName} (${d.equipmentCode})")
                                        Text(
                                            "${d.teacherName} · ${d.loan.course}",
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
            }

            item { SectionTitle("Equipos no reservables (bloqueados o en mantención)") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ui.unavailable.isEmpty()) {
                            Text("Todos los equipos están operativos.", style = MaterialTheme.typography.bodySmall)
                        }
                        ui.unavailable.forEach {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${it.displayName} (${it.internalCode})", modifier = Modifier.weight(1f))
                                StatusChip(it.status.label, it.status.color())
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
