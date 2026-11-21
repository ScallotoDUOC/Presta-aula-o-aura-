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
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.UtpIncidentsViewModel

private const val ALL_TEACHERS = "Todos los docentes"
private const val ALL_EQUIPMENT = "Todos los equipos"

/** Seguimiento de devoluciones con daños o piezas faltantes. Solo lectura. */
@Composable
fun UtpIncidentsScreen() {
    val vm = appViewModel { UtpIncidentsViewModel(it.loanRepository, it.equipmentRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val f = ui.filters

    Scaffold(topBar = { AppTopBar("Incidencias") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("Filtros") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = f.dateEnabled, onCheckedChange = vm::onDateToggle)
                            Spacer(Modifier.width(8.dp))
                            Text("Filtrar por fechas")
                        }
                        if (f.dateEnabled) {
                            DatePickerField(
                                label = "Fecha inicial",
                                epochDay = f.from,
                                onDateSelected = vm::onFromChange,
                                minEpochDay = DateUtils.today() - 1825
                            )
                            DatePickerField(
                                label = "Fecha final",
                                epochDay = f.to,
                                onDateSelected = vm::onToChange,
                                minEpochDay = f.from
                            )
                        }
                        AppDropdown(
                            label = "Docente",
                            selectedText = f.teacherName ?: ALL_TEACHERS,
                            options = listOf(ALL_TEACHERS) + ui.teacherOptions,
                            optionLabel = { t: String -> t },
                            onSelected = { vm.onTeacherChange(if (it == ALL_TEACHERS) null else it) },
                            leadingIcon = Icons.Default.Person
                        )
                        AppDropdown(
                            label = "Equipo",
                            selectedText = ui.equipmentOptions.firstOrNull { it.id == f.equipmentId }
                                ?.let { "${it.displayName} (${it.internalCode})" } ?: ALL_EQUIPMENT,
                            options = listOf(ALL_EQUIPMENT) + ui.equipmentOptions.map { "${it.displayName} (${it.internalCode})" },
                            optionLabel = { t: String -> t },
                            onSelected = { label ->
                                vm.onEquipmentChange(
                                    ui.equipmentOptions.firstOrNull { "${it.displayName} (${it.internalCode})" == label }?.id
                                )
                            },
                            leadingIcon = Icons.Default.Devices
                        )
                        Text("Tipo de incidencia", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = f.condition == null, onClick = { vm.onConditionChange(null) }, label = { Text("Todas") })
                            }
                            items(listOf(ReturnCondition.CON_DANOS, ReturnCondition.INCOMPLETO)) { c ->
                                FilterChip(selected = f.condition == c, onClick = { vm.onConditionChange(c) }, label = { Text(c.label) })
                            }
                        }
                        TextButton(onClick = vm::clearFilters) { Text("Limpiar filtros") }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    UtpKpiCard("Con daños", ui.totalDamaged, StatusColors.Danger, Icons.Default.BrokenImage, Modifier.weight(1f))
                    UtpKpiCard("Incompletos", ui.totalIncomplete, StatusColors.Warning, Icons.Default.Inventory, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Incidencias (${ui.rows.size})") }
            if (ui.rows.isEmpty()) {
                item { EmptyState(Icons.Default.CheckCircle, "Sin incidencias", "No hay devoluciones con problemas para estos filtros.") }
            }
            items(ui.rows, key = { "i${it.detail.loan.id}" }) { row ->
                val d = row.detail
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${d.equipmentName} (${d.equipmentCode})",
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            d.loan.returnCondition?.let { StatusChip(it.label, StatusColors.Danger) }
                        }
                        Text(
                            "${d.teacherName} · ${d.loan.course} · ${DateUtils.format(d.loan.dateEpochDay)} · ${TimeBlocks.label(d.loan.block)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (d.loan.observations.isNotBlank()) {
                            Text("Observaciones: ${d.loan.observations}", style = MaterialTheme.typography.bodySmall)
                        }
                        row.equipmentStatus?.let {
                            Text(
                                "Estado actual del equipo: ${it.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
