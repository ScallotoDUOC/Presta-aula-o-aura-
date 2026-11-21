package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import com.duoc.prestaaula.util.DateUtils
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Granularity(val label: String) {
    DIA("Día"), SEMANA("Semana"), MES("Mes"), ANIO("Año")
}

enum class ReportType(val label: String, val fileName: String) {
    RESERVAS("Reservas (listado general)", "reservas"),
    MAS_RESERVADOS("Equipos más reservados", "equipos_mas_reservados"),
    USUARIOS("Usuarios y su actividad", "usuarios_actividad"),
    INVENTARIO("Estado del inventario", "inventario"),
    CANCELADAS("Reservas canceladas", "reservas_canceladas"),
    TIPOS("Solicitudes por tipo de equipo", "solicitudes_por_tipo")
}

data class ReportFilters(
    val dateEnabled: Boolean = false,
    val from: Long = DateUtils.today() - 30,
    val to: Long = DateUtils.today(),
    val equipmentId: Long? = null,
    val type: String? = null,
    val status: LoanStatus? = null
)

data class ReportControls(
    val filters: ReportFilters = ReportFilters(),
    val granularity: Granularity = Granularity.MES,
    val reportType: ReportType = ReportType.RESERVAS
)

data class CountRow(val label: String, val value: Int)
data class RankedEquipment(val equipment: EquipmentEntity, val total: Int)
data class UserActivity(
    val user: UserEntity,
    val total: Int,
    val active: Int,
    val returned: Int,
    val cancelled: Int,
    val incidents: Int
)

data class AdminReportsUiState(
    val controls: ReportControls = ReportControls(),
    val equipmentOptions: List<EquipmentEntity> = emptyList(),
    val typeOptions: List<String> = emptyList(),
    /** Reservas que cumplen todos los filtros. */
    val loans: List<LoanDetail> = emptyList(),
    /** Reservas canceladas que cumplen los filtros (sin considerar el filtro de estado). */
    val cancelled: List<LoanDetail> = emptyList(),
    val total: Int = 0,
    val reserved: Int = 0,
    val pickedUp: Int = 0,
    val returned: Int = 0,
    val cancelledCount: Int = 0,
    val ranking: List<RankedEquipment> = emptyList(),
    val leastReserved: List<RankedEquipment> = emptyList(),
    val neverReserved: List<EquipmentEntity> = emptyList(),
    val topTeachers: List<CountRow> = emptyList(),
    val byType: List<CountRow> = emptyList(),
    val evolution: List<CountRow> = emptyList(),
    val equipmentRows: List<EquipmentEntity> = emptyList(),
    val userRows: List<UserActivity> = emptyList(),
    val feedback: Feedback? = null
)

data class ExportData(
    val reportName: String,
    val fileName: String,
    val csv: String,
    val rowCount: Int,
    val filters: String
)

class AdminReportsViewModel(
    equipmentRepo: EquipmentRepository,
    loanRepo: LoanRepository,
    private val adminRepo: AdminRepository,
    private val actor: UserEntity
) : ViewModel() {
    private val controls = MutableStateFlow(ReportControls())
    private val feedback = MutableStateFlow<Feedback?>(null)

    val uiState: StateFlow<AdminReportsUiState> = combine(
        equipmentRepo.observeAll(),
        loanRepo.observeAllDetails(),
        adminRepo.observeUsers(),
        controls,
        feedback
    ) { equipment, loans, users, c, fb ->
        val f = c.filters
        fun matchesBase(d: LoanDetail): Boolean =
            (!f.dateEnabled || d.loan.dateEpochDay in f.from..f.to) &&
                (f.equipmentId == null || d.loan.equipmentId == f.equipmentId) &&
                (f.type == null || d.equipmentType == f.type)

        val base = loans.filter { matchesBase(it) }
        val filtered = base.filter { f.status == null || it.loan.status == f.status }
        val equipmentRows = equipment.filter {
            (f.equipmentId == null || it.id == f.equipmentId) && (f.type == null || it.type == f.type)
        }
        val counts = filtered.groupingBy { it.loan.equipmentId }.eachCount()
        val ranking = equipmentRows
            .map { RankedEquipment(it, counts[it.id] ?: 0) }
            .sortedByDescending { it.total }

        AdminReportsUiState(
            controls = c,
            equipmentOptions = equipment,
            typeOptions = equipment.map { it.type }.distinct().sorted(),
            loans = filtered.sortedByDescending { it.loan.dateEpochDay },
            cancelled = base.filter { it.loan.status == LoanStatus.CANCELADA }.sortedByDescending { it.loan.dateEpochDay },
            total = filtered.size,
            reserved = filtered.count { it.loan.status == LoanStatus.RESERVADA },
            pickedUp = filtered.count { it.loan.status == LoanStatus.RETIRADA },
            returned = filtered.count { it.loan.status == LoanStatus.DEVUELTA },
            cancelledCount = filtered.count { it.loan.status == LoanStatus.CANCELADA },
            ranking = ranking,
            leastReserved = ranking.filter { it.total > 0 }.sortedBy { it.total }.take(5),
            neverReserved = ranking.filter { it.total == 0 }.map { it.equipment },
            topTeachers = filtered.groupBy { it.teacherName }
                .map { CountRow(it.key, it.value.size) }
                .sortedByDescending { it.value }
                .take(10),
            byType = filtered.groupBy { it.equipmentType }
                .map { CountRow(it.key, it.value.size) }
                .sortedByDescending { it.value },
            evolution = evolution(filtered, c.granularity),
            equipmentRows = equipmentRows,
            userRows = users.map { u ->
                val mine = filtered.filter { it.loan.teacherId == u.id }
                UserActivity(
                    user = u,
                    total = mine.size,
                    active = mine.count { it.loan.status == LoanStatus.RESERVADA || it.loan.status == LoanStatus.RETIRADA },
                    returned = mine.count { it.loan.status == LoanStatus.DEVUELTA },
                    cancelled = mine.count { it.loan.status == LoanStatus.CANCELADA },
                    incidents = mine.count {
                        val cond = it.loan.returnCondition
                        cond != null && cond != ReturnCondition.BUEN_ESTADO
                    }
                )
            }.sortedByDescending { it.total },
            feedback = fb
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminReportsUiState())

    /** Agrupa las reservas por día, semana, mes o año (orden cronológico). */
    private fun evolution(loans: List<LoanDetail>, g: Granularity): List<CountRow> =
        loans.groupBy { periodKey(it.loan.dateEpochDay, g) }
            .entries
            .sortedBy { it.key.first }
            .map { CountRow(it.key.second, it.value.size) }

    private fun periodKey(epochDay: Long, g: Granularity): Pair<String, String> {
        val d = LocalDate.ofEpochDay(epochDay)
        return when (g) {
            Granularity.DIA -> d.toString() to DateUtils.format(epochDay)
            Granularity.SEMANA -> {
                val year = d.get(IsoFields.WEEK_BASED_YEAR)
                val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                "${year.toString().padStart(4, '0')}-${week.toString().padStart(2, '0')}" to "Semana $week de $year"
            }
            Granularity.MES -> {
                val month = d.monthValue.toString().padStart(2, '0')
                "${d.year.toString().padStart(4, '0')}-$month" to "$month/${d.year}"
            }
            Granularity.ANIO -> d.year.toString().padStart(4, '0') to d.year.toString()
        }
    }

    // ---- Filtros ----
    private fun updateFilters(change: (ReportFilters) -> ReportFilters) =
        controls.update { it.copy(filters = change(it.filters)) }

    fun onDateToggle(enabled: Boolean) = updateFilters { it.copy(dateEnabled = enabled) }
    fun onFromChange(day: Long) = updateFilters { it.copy(from = day, to = maxOf(it.to, day)) }
    fun onToChange(day: Long) = updateFilters { it.copy(to = day) }
    fun onEquipmentChange(id: Long?) = updateFilters { it.copy(equipmentId = id) }
    fun onTypeChange(type: String?) = updateFilters { it.copy(type = type) }
    fun onStatusChange(status: LoanStatus?) = updateFilters { it.copy(status = status) }
    fun clearFilters() = controls.update { it.copy(filters = ReportFilters()) }
    fun onGranularityChange(g: Granularity) = controls.update { it.copy(granularity = g) }
    fun onReportTypeChange(t: ReportType) = controls.update { it.copy(reportType = t) }

    // ---- Exportación ----
    fun buildExport(): ExportData {
        val s = uiState.value
        val type = s.controls.reportType
        val rows = ReportCsv.rows(type, s)
        return ExportData(
            reportName = type.label,
            fileName = type.fileName,
            csv = ReportCsv.toCsv(rows),
            rowCount = rows.size - 1, // sin la fila de encabezados
            filters = describeFilters(s)
        )
    }

    fun onExported(data: ExportData) {
        viewModelScope.launch {
            adminRepo.logExport(actor.id, data.reportName, data.rowCount, data.filters)
            feedback.value = Feedback("Reporte exportado (${data.rowCount} filas)", false)
        }
    }

    fun showMessage(text: String, isError: Boolean) { feedback.value = Feedback(text, isError) }
    fun clearFeedback() { feedback.value = null }

    private fun describeFilters(s: AdminReportsUiState): String {
        val f = s.controls.filters
        val parts = mutableListOf<String>()
        if (f.dateEnabled) parts += "fechas ${DateUtils.format(f.from)} a ${DateUtils.format(f.to)}"
        f.type?.let { parts += "tipo $it" }
        f.equipmentId?.let { id ->
            s.equipmentOptions.firstOrNull { it.id == id }?.let { parts += "equipo ${it.internalCode}" }
        }
        f.status?.let { parts += "estado ${it.label}" }
        return if (parts.isEmpty()) "sin filtros" else parts.joinToString(", ")
    }
}
