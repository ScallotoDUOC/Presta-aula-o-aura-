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

import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.nativeaccess.ExportHelper
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminReportsViewModel
import com.duoc.prestaaula.viewmodel.CountRow
import com.duoc.prestaaula.viewmodel.Granularity
import com.duoc.prestaaula.viewmodel.LoanFilter
import com.duoc.prestaaula.viewmodel.RankedEquipment
import com.duoc.prestaaula.viewmodel.ReportType

private const val ALL_TYPES = "Todos los tipos"
private const val ALL_EQUIPMENT = "Todos los equipos"
private const val MAX_LISTED_LOANS = 50

private fun equipmentLabel(e: EquipmentEntity) = "${e.displayName} (${e.internalCode})"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsScreen(user: UserEntity) {
    val vm = appViewModel { AdminReportsViewModel(it.equipmentRepository, it.loanRepository, it.adminRepository, user) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val f = ui.controls.filters

    LaunchedEffect(ui.feedback) {
        ui.feedback?.let {
            snackbar.showSnackbar(it.text)
            vm.clearFeedback()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Reportes y estadísticas") },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ------------------------------ Filtros ------------------------------
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
                            label = "Tipo de equipo",
                            selectedText = f.type ?: ALL_TYPES,
                            options = listOf(ALL_TYPES) + ui.typeOptions,
                            optionLabel = { t: String -> t },
                            onSelected = { vm.onTypeChange(if (it == ALL_TYPES) null else it) },
                            leadingIcon = Icons.Default.Category
                        )
                        AppDropdown(
                            label = "Equipo",
                            selectedText = ui.equipmentOptions.firstOrNull { it.id == f.equipmentId }
                                ?.let { equipmentLabel(it) } ?: ALL_EQUIPMENT,
                            options = listOf(ALL_EQUIPMENT) + ui.equipmentOptions.map { equipmentLabel(it) },
                            optionLabel = { t: String -> t },
                            onSelected = { label ->
                                vm.onEquipmentChange(ui.equipmentOptions.firstOrNull { equipmentLabel(it) == label }?.id)
                            },
                            leadingIcon = Icons.Default.Devices
                        )
                        Text("Estado de la reserva", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LoanFilter.entries.toList()) { lf ->
                                FilterChip(
                                    selected = f.status == lf.status,
                                    onClick = { vm.onStatusChange(lf.status) },
                                    label = { Text(lf.label) }
                                )
                            }
                        }
                        TextButton(onClick = vm::clearFilters) { Text("Limpiar filtros") }
                    }
                }
            }

            // ------------------------------ Exportar ------------------------------
            item { SectionTitle("Exportar reporte a Excel") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppDropdown(
                            label = "Reporte",
                            selectedText = ui.controls.reportType.label,
                            options = ReportType.entries.toList(),
                            optionLabel = { t: ReportType -> t.label },
                            onSelected = vm::onReportTypeChange,
                            leadingIcon = Icons.Default.Assessment
                        )
                        Text(
                            "Se genera un archivo CSV con los filtros aplicados. Excel lo abre directamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LoadingButton(
                            text = "Exportar a Excel (CSV)",
                            loading = false,
                            onClick = {
                                val data = vm.buildExport()
                                if (data.rowCount <= 0) {
                                    vm.showMessage("No hay datos para exportar con los filtros actuales", true)
                                } else {
                                    try {
                                        val file = ExportHelper.writeCsv(context, data.fileName, data.csv)
                                        ExportHelper.share(context, file)
                                        vm.onExported(data)
                                    } catch (e: Exception) {
                                        vm.showMessage("No se pudo exportar el reporte", true)
                                    }
                                }
                            },
                            icon = Icons.Default.FileDownload
                        )
                    }
                }
            }

            // ------------------------------ Resumen ------------------------------
            item { SectionTitle("Resumen de reservas") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard("Total", ui.total, StatusColors.Info, Icons.Default.EventAvailable, Modifier.weight(1f))
                    KpiCard("Reservadas", ui.reserved, StatusColors.Info, Icons.Default.Schedule, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard("Retiradas", ui.pickedUp, StatusColors.Warning, Icons.Default.Output, Modifier.weight(1f))
                    KpiCard("Devueltas", ui.returned, StatusColors.Success, Icons.Default.AssignmentReturn, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard("Canceladas", ui.cancelledCount, StatusColors.Neutral, Icons.Default.Cancel, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }

            // ------------------------------ Estadísticas ------------------------------
            item { SectionTitle("Equipos más reservados") }
            item { RankTable(ui.ranking.filter { it.total > 0 }.take(10), "Sin reservas con estos filtros.") }

            item { SectionTitle("Equipos menos reservados") }
            item { RankTable(ui.leastReserved, "Sin reservas con estos filtros.") }

            item { SectionTitle("Equipos sin reservas") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ui.neverReserved.isEmpty()) {
                            Text("Todos los equipos tienen reservas con estos filtros.", style = MaterialTheme.typography.bodySmall)
                        }
                        ui.neverReserved.forEach { Text(equipmentLabel(it)) }
                    }
                }
            }

            item { SectionTitle("Docentes con más reservas") }
            item { CountTable(ui.topTeachers, "reservas", "Sin reservas con estos filtros.") }

            item { SectionTitle("Tipos de equipo más solicitados") }
            item { CountTable(ui.byType, "reservas", "Sin reservas con estos filtros.") }

            item { SectionTitle("Evolución de las reservas") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Granularity.entries.toList()) { g ->
                        FilterChip(
                            selected = ui.controls.granularity == g,
                            onClick = { vm.onGranularityChange(g) },
                            label = { Text(g.label) }
                        )
                    }
                }
            }
            item { CountTable(ui.evolution.takeLast(30), "reservas", "Sin reservas con estos filtros.") }

            // ------------------------------ Listado ------------------------------
            item { SectionTitle("Reservas (${ui.loans.size})") }
            if (ui.loans.isEmpty()) {
                item { EmptyState(Icons.Default.Inbox, "Sin resultados", "No hay reservas con estos filtros.") }
            }
            items(ui.loans.take(MAX_LISTED_LOANS), key = { "r${it.loan.id}" }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (ui.loans.size > MAX_LISTED_LOANS) {
                item {
                    Text(
                        "Se muestran $MAX_LISTED_LOANS de ${ui.loans.size}. El archivo exportado incluye todas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: Int, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
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
private fun RankTable(rows: List<RankedEquipment>, emptyText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall)
            rows.forEachIndexed { index, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Text(equipmentLabel(r.equipment), modifier = Modifier.weight(1f))
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

@Composable
private fun CountTable(rows: List<CountRow>, unit: String, emptyText: String) {
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
