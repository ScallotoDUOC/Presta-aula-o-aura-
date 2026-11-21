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
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.nativeaccess.ExportHelper
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.LoanFilter
import com.duoc.prestaaula.viewmodel.UtpReportType
import com.duoc.prestaaula.viewmodel.UtpReportsViewModel

private const val ALL_TYPES = "Todos los tipos"
private const val ALL_EQUIPMENT = "Todos los equipos"
private const val MAX_LISTED = 50

/** Reportes de uso con filtros y exportación a Excel (CSV). Cada exportación queda en la auditoría. */
@Composable
fun UtpReportsScreen(user: UserEntity) {
    val vm = appViewModel { UtpReportsViewModel(it.equipmentRepository, it.loanRepository, it.usageRepository, user) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val f = ui.filters

    LaunchedEffect(ui.feedback) {
        ui.feedback?.let {
            snackbar.showSnackbar(it.text)
            vm.clearFeedback()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Reportes de uso") },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
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

            item { SectionTitle("Exportar reporte a Excel") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppDropdown(
                            label = "Reporte",
                            selectedText = ui.reportType.label,
                            options = UtpReportType.entries.toList(),
                            optionLabel = { t: UtpReportType -> t.label },
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

            item { SectionTitle("Uso por docente") }
            items(ui.teachers.take(MAX_LISTED), key = { "d${it.teacherId}" }) { t ->
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
                        Text("${t.total}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item { SectionTitle("Uso por curso") }
            item { UtpCountTable(ui.courses, "reservas", "Sin reservas con estos filtros.") }

            item { SectionTitle("Solicitudes por tipo de equipo") }
            item { UtpCountTable(ui.types, "reservas", "Sin reservas con estos filtros.") }

            item { SectionTitle("Equipos más reservados") }
            item { UtpRankTable(ui.ranking.filter { it.total > 0 }.take(10), "Sin reservas con estos filtros.") }

            item { SectionTitle("Reservas (${ui.loans.size})") }
            if (ui.loans.isEmpty()) {
                item { EmptyState(Icons.Default.Inbox, "Sin resultados", "No hay reservas con estos filtros.") }
            }
            items(ui.loans.take(MAX_LISTED), key = { "r${it.loan.id}" }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.equipmentName} (${d.equipmentCode})", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${d.teacherName} · ${d.loan.course} · ${DateUtils.format(d.loan.dateEpochDay)} · ${TimeBlocks.label(d.loan.block)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(d.loan.status.label, d.loan.status.color())
                    }
                }
            }
            if (ui.loans.size > MAX_LISTED) {
                item {
                    Text(
                        "Se muestran $MAX_LISTED de ${ui.loans.size}. El archivo exportado incluye todas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
