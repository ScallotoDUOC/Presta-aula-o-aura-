package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import com.duoc.prestaaula.data.repository.UsageRepository
import com.duoc.prestaaula.domain.model.TimeBlock
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.domain.usage.EquipmentUsage
import com.duoc.prestaaula.domain.usage.TeacherUsage
import com.duoc.prestaaula.domain.usage.UsageAnalysis
import com.duoc.prestaaula.domain.usage.UsageCount
import com.duoc.prestaaula.domain.usage.UsagePeriod
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================================================================
// Panel de uso (Jefe UTP) — solo lectura
// =====================================================================================

data class UtpDashboardUiState(
    val period: UsagePeriod = UsagePeriod.DIAS_30,
    val total: Int = 0,
    val returned: Int = 0,
    val incidents: Int = 0,
    /** Equipos retirados que todavía no se devuelven (no depende del período). */
    val pendingReturns: Int = 0,
    val weekly: List<UsageCount> = emptyList(),
    val byBlock: List<UsageCount> = emptyList(),
    val topEquipment: List<EquipmentUsage> = emptyList(),
    val teachers: List<TeacherUsage> = emptyList(),
    val courses: List<UsageCount> = emptyList(),
    val types: List<UsageCount> = emptyList()
)

class UtpDashboardViewModel(
    loanRepo: LoanRepository,
    equipmentRepo: EquipmentRepository,
    usageRepo: UsageRepository
) : ViewModel() {
    private val period = MutableStateFlow(UsagePeriod.DIAS_30)

    val uiState: StateFlow<UtpDashboardUiState> = combine(
        loanRepo.observeAllDetails(),
        equipmentRepo.observeAll(),
        usageRepo.observeTeachers(),
        period
    ) { loans, equipment, teachers, p ->
        val today = DateUtils.today()
        val inPeriod = loans.filter { UsageAnalysis.inPeriod(it.loan.dateEpochDay, p, today) }
        val valid = UsageAnalysis.valid(inPeriod)
        UtpDashboardUiState(
            period = p,
            total = valid.size,
            returned = valid.count { it.loan.status == LoanStatus.DEVUELTA },
            incidents = valid.count { UsageAnalysis.isIncident(it) },
            pendingReturns = loans.count { it.loan.status == LoanStatus.RETIRADA },
            weekly = UsageAnalysis.weekly(valid).takeLast(8),
            byBlock = UsageAnalysis.byBlock(valid),
            topEquipment = UsageAnalysis.equipmentRanking(valid, equipment).filter { it.total > 0 }.take(5),
            teachers = UsageAnalysis.byTeacher(inPeriod, teachers),
            courses = UsageAnalysis.byCourse(valid),
            types = UsageAnalysis.byType(valid)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UtpDashboardUiState())

    fun onPeriodChange(p: UsagePeriod) { period.value = p }
}

// =====================================================================================
// Ocupación por día y bloque
// =====================================================================================

data class OccupancyBlock(val block: TimeBlock, val loans: List<LoanDetail>)

data class UtpOccupancyUiState(
    val date: Long = DateUtils.today(),
    val blocks: List<OccupancyBlock> = emptyList(),
    val total: Int = 0,
    /** Equipos que ese día no se pueden reservar por estar bloqueados o en mantención. */
    val unavailable: List<EquipmentEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class UtpOccupancyViewModel(
    loanRepo: LoanRepository,
    equipmentRepo: EquipmentRepository
) : ViewModel() {
    private val date = MutableStateFlow(DateUtils.today())

    val uiState: StateFlow<UtpOccupancyUiState> = combine(
        date,
        date.flatMapLatest { loanRepo.observeActiveByDate(it) },
        equipmentRepo.observeAll()
    ) { day, loans, equipment ->
        UtpOccupancyUiState(
            date = day,
            blocks = TimeBlocks.all.map { b -> OccupancyBlock(b, loans.filter { it.loan.block == b.number }) },
            total = loans.size,
            unavailable = equipment.filter {
                it.status == EquipmentStatus.BLOQUEADO || it.status == EquipmentStatus.MANTENCION
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UtpOccupancyUiState())

    fun onDateChange(day: Long) { date.value = day }
    fun previousDay() { date.update { it - 1 } }
    fun nextDay() { date.update { it + 1 } }
    fun goToday() { date.value = DateUtils.today() }
}

// =====================================================================================
// Seguimiento de incidencias
// =====================================================================================

data class IncidentRow(val detail: LoanDetail, val equipmentStatus: EquipmentStatus?)

data class IncidentFilters(
    val dateEnabled: Boolean = false,
    val from: Long = DateUtils.today() - 30,
    val to: Long = DateUtils.today(),
    val teacherName: String? = null,
    val equipmentId: Long? = null,
    val condition: ReturnCondition? = null
)

data class UtpIncidentsUiState(
    val filters: IncidentFilters = IncidentFilters(),
    val rows: List<IncidentRow> = emptyList(),
    val teacherOptions: List<String> = emptyList(),
    val equipmentOptions: List<EquipmentEntity> = emptyList(),
    val totalDamaged: Int = 0,
    val totalIncomplete: Int = 0
)

class UtpIncidentsViewModel(
    loanRepo: LoanRepository,
    equipmentRepo: EquipmentRepository
) : ViewModel() {
    private val filters = MutableStateFlow(IncidentFilters())

    val uiState: StateFlow<UtpIncidentsUiState> = combine(
        loanRepo.observeIncidents(),
        equipmentRepo.observeAll(),
        filters
    ) { incidents, equipment, f ->
        val statusById = equipment.associate { it.id to it.status }
        val filtered = incidents
            .filter { UsageAnalysis.isIncident(it) }
            .filter {
                (!f.dateEnabled || it.loan.dateEpochDay in f.from..f.to) &&
                    (f.teacherName == null || it.teacherName == f.teacherName) &&
                    (f.equipmentId == null || it.loan.equipmentId == f.equipmentId) &&
                    (f.condition == null || it.loan.returnCondition == f.condition)
            }
            .sortedByDescending { it.loan.dateEpochDay }
        UtpIncidentsUiState(
            filters = f,
            rows = filtered.map { IncidentRow(it, statusById[it.loan.equipmentId]) },
            teacherOptions = incidents.map { it.teacherName }.distinct().sorted(),
            equipmentOptions = equipment,
            totalDamaged = filtered.count { it.loan.returnCondition == ReturnCondition.CON_DANOS },
            totalIncomplete = filtered.count { it.loan.returnCondition == ReturnCondition.INCOMPLETO }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UtpIncidentsUiState())

    private fun change(block: (IncidentFilters) -> IncidentFilters) = filters.update(block)

    fun onDateToggle(enabled: Boolean) = change { it.copy(dateEnabled = enabled) }
    fun onFromChange(day: Long) = change { it.copy(from = day, to = maxOf(it.to, day)) }
    fun onToChange(day: Long) = change { it.copy(to = day) }
    fun onTeacherChange(name: String?) = change { it.copy(teacherName = name) }
    fun onEquipmentChange(id: Long?) = change { it.copy(equipmentId = id) }
    fun onConditionChange(c: ReturnCondition?) = change { it.copy(condition = c) }
    fun clearFilters() { filters.value = IncidentFilters() }
}

// =====================================================================================
// Reportes de uso con exportación a Excel (CSV)
// =====================================================================================

enum class UtpReportType(val label: String, val fileName: String) {
    RESERVAS("Reservas (listado general)", "utp_reservas"),
    DOCENTES("Uso por docente", "utp_uso_por_docente"),
    CURSOS("Uso por curso", "utp_uso_por_curso"),
    TIPOS("Solicitudes por tipo de equipo", "utp_solicitudes_por_tipo"),
    MAS_RESERVADOS("Equipos más reservados", "utp_equipos_mas_reservados"),
    INCIDENCIAS("Incidencias de devolución", "utp_incidencias")
}

data class UtpReportsUiState(
    val filters: ReportFilters = ReportFilters(),
    val reportType: UtpReportType = UtpReportType.RESERVAS,
    val equipmentOptions: List<EquipmentEntity> = emptyList(),
    val typeOptions: List<String> = emptyList(),
    val loans: List<LoanDetail> = emptyList(),
    val teachers: List<TeacherUsage> = emptyList(),
    val courses: List<UsageCount> = emptyList(),
    val types: List<UsageCount> = emptyList(),
    val ranking: List<EquipmentUsage> = emptyList(),
    val incidents: List<LoanDetail> = emptyList(),
    val feedback: Feedback? = null
)

class UtpReportsViewModel(
    equipmentRepo: EquipmentRepository,
    loanRepo: LoanRepository,
    private val usageRepo: UsageRepository,
    private val actor: UserEntity
) : ViewModel() {
    private val filters = MutableStateFlow(ReportFilters())
    private val reportType = MutableStateFlow(UtpReportType.RESERVAS)
    private val feedback = MutableStateFlow<Feedback?>(null)

    private data class Controls(val filters: ReportFilters, val type: UtpReportType, val feedback: Feedback?)

    private val controls = combine(filters, reportType, feedback) { f, t, fb -> Controls(f, t, fb) }

    val uiState: StateFlow<UtpReportsUiState> = combine(
        equipmentRepo.observeAll(),
        loanRepo.observeAllDetails(),
        usageRepo.observeTeachers(),
        controls
    ) { equipment, loans, teachers, c ->
        val f = c.filters
        val filtered = loans.filter {
            (!f.dateEnabled || it.loan.dateEpochDay in f.from..f.to) &&
                (f.equipmentId == null || it.loan.equipmentId == f.equipmentId) &&
                (f.type == null || it.equipmentType == f.type) &&
                (f.status == null || it.loan.status == f.status)
        }
        val valid = UsageAnalysis.valid(filtered)
        val equipmentRows = equipment.filter {
            (f.equipmentId == null || it.id == f.equipmentId) && (f.type == null || it.type == f.type)
        }
        UtpReportsUiState(
            filters = f,
            reportType = c.type,
            equipmentOptions = equipment,
            typeOptions = equipment.map { it.type }.distinct().sorted(),
            loans = filtered.sortedByDescending { it.loan.dateEpochDay },
            teachers = UsageAnalysis.byTeacher(filtered, teachers),
            courses = UsageAnalysis.byCourse(valid),
            types = UsageAnalysis.byType(valid),
            ranking = UsageAnalysis.equipmentRanking(valid, equipmentRows),
            incidents = filtered.filter { UsageAnalysis.isIncident(it) }.sortedByDescending { it.loan.dateEpochDay },
            feedback = c.feedback
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UtpReportsUiState())

    private fun change(block: (ReportFilters) -> ReportFilters) = filters.update(block)

    fun onDateToggle(enabled: Boolean) = change { it.copy(dateEnabled = enabled) }
    fun onFromChange(day: Long) = change { it.copy(from = day, to = maxOf(it.to, day)) }
    fun onToChange(day: Long) = change { it.copy(to = day) }
    fun onEquipmentChange(id: Long?) = change { it.copy(equipmentId = id) }
    fun onTypeChange(type: String?) = change { it.copy(type = type) }
    fun onStatusChange(status: LoanStatus?) = change { it.copy(status = status) }
    fun clearFilters() { filters.value = ReportFilters() }
    fun onReportTypeChange(t: UtpReportType) { reportType.value = t }

    fun buildExport(): ExportData {
        val s = uiState.value
        val rows = UtpReportCsv.rows(s.reportType, s)
        return ExportData(
            reportName = s.reportType.label,
            fileName = s.reportType.fileName,
            csv = ReportCsv.toCsv(rows),
            rowCount = rows.size - 1,
            filters = describeFilters(s)
        )
    }

    fun onExported(data: ExportData) {
        viewModelScope.launch {
            usageRepo.logExport(actor.id, data.reportName, data.rowCount, data.filters)
            feedback.value = Feedback("Reporte exportado (${data.rowCount} filas)", false)
        }
    }

    fun showMessage(text: String, isError: Boolean) { feedback.value = Feedback(text, isError) }
    fun clearFeedback() { feedback.value = null }

    private fun describeFilters(s: UtpReportsUiState): String {
        val f = s.filters
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

/** Filas del CSV de cada reporte UTP. No incluye correos ni datos de cuentas. */
object UtpReportCsv {
    private val loanHeader = listOf(
        "Fecha", "Bloque", "Equipo", "Código", "Docente", "Curso", "Estado",
        "Retirado el", "Devuelto el", "Estado al devolver", "Observaciones"
    )

    fun rows(type: UtpReportType, s: UtpReportsUiState): List<List<String>> = when (type) {
        UtpReportType.RESERVAS -> listOf(loanHeader) + s.loans.map { loanRow(it) }
        UtpReportType.INCIDENCIAS -> listOf(loanHeader) + s.incidents.map { loanRow(it) }
        UtpReportType.DOCENTES ->
            listOf(listOf("Docente", "Reservas", "Devueltas", "Canceladas", "Con incidencia")) +
                s.teachers.map {
                    listOf(it.name, it.total.toString(), it.returned.toString(), it.cancelled.toString(), it.incidents.toString())
                }
        UtpReportType.CURSOS ->
            listOf(listOf("Curso", "Reservas")) + s.courses.map { listOf(it.label, it.value.toString()) }
        UtpReportType.TIPOS ->
            listOf(listOf("Tipo de equipo", "Reservas")) + s.types.map { listOf(it.label, it.value.toString()) }
        UtpReportType.MAS_RESERVADOS ->
            listOf(listOf("Posición", "Equipo", "Código", "Tipo", "Reservas")) +
                s.ranking.mapIndexed { i, r ->
                    listOf((i + 1).toString(), r.equipment.displayName, r.equipment.internalCode, r.equipment.type, r.total.toString())
                }
    }

    private fun loanRow(d: LoanDetail): List<String> = listOf(
        DateUtils.format(d.loan.dateEpochDay),
        TimeBlocks.label(d.loan.block),
        d.equipmentName,
        d.equipmentCode,
        d.teacherName,
        d.loan.course,
        d.loan.status.label,
        d.loan.pickedUpAt?.let { DateUtils.formatMillis(it) } ?: "",
        d.loan.returnedAt?.let { DateUtils.formatMillis(it) } ?: "",
        d.loan.returnCondition?.label ?: "",
        d.loan.observations
    )
}
