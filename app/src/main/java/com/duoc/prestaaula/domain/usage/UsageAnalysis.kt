package com.duoc.prestaaula.domain.usage

import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.domain.model.TimeBlocks
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Período que se analiza, contado hacia atrás desde hoy. */
enum class UsagePeriod(val label: String, val days: Int?) {
    SEMANA("7 días", 7),
    DIAS_30("30 días", 30),
    DIAS_90("90 días", 90),
    TODO("Todo", null)
}

/** Docente activo (solo id y nombre: no se exponen correos ni claves). */
data class TeacherRef(val id: Long, val name: String)

data class UsageCount(val label: String, val value: Int)

data class EquipmentUsage(val equipment: EquipmentEntity, val total: Int)

data class TeacherUsage(
    val teacherId: Long,
    val name: String,
    val total: Int,
    val returned: Int,
    val cancelled: Int,
    val incidents: Int
)

/** Cálculos de uso de los equipos. Funciones puras: reciben listas y devuelven resultados. */
object UsageAnalysis {

    fun isIncident(d: LoanDetail): Boolean {
        val condition = d.loan.returnCondition
        return condition != null && condition != ReturnCondition.BUEN_ESTADO
    }

    fun inPeriod(epochDay: Long, period: UsagePeriod, today: Long): Boolean {
        val days = period.days ?: return true
        return epochDay in (today - days + 1)..today
    }

    /** Reservas que cuentan como uso: todas menos las canceladas. */
    fun valid(loans: List<LoanDetail>): List<LoanDetail> =
        loans.filter { it.loan.status != LoanStatus.CANCELADA }

    /** Uso por docente. Incluye a los docentes activos que no tienen reservas (con ceros). */
    fun byTeacher(loans: List<LoanDetail>, teachers: List<TeacherRef>): List<TeacherUsage> {
        val grouped = loans.groupBy { it.loan.teacherId }
        val used = grouped.map { entry ->
            val list = entry.value
            TeacherUsage(
                teacherId = entry.key,
                name = list.first().teacherName,
                total = list.count { it.loan.status != LoanStatus.CANCELADA },
                returned = list.count { it.loan.status == LoanStatus.DEVUELTA },
                cancelled = list.count { it.loan.status == LoanStatus.CANCELADA },
                incidents = list.count { isIncident(it) }
            )
        }
        val idle = teachers
            .filter { it.id !in grouped.keys }
            .map { TeacherUsage(it.id, it.name, 0, 0, 0, 0) }
        return (used + idle).sortedWith(compareByDescending<TeacherUsage> { it.total }.thenBy { it.name })
    }

    fun byCourse(validLoans: List<LoanDetail>): List<UsageCount> =
        validLoans.groupBy { it.loan.course.trim().lowercase() }
            .map { entry ->
                val shown = entry.value.first().loan.course.trim()
                UsageCount(if (shown.isEmpty()) "Sin curso" else shown, entry.value.size)
            }
            .sortedByDescending { it.value }

    fun byType(validLoans: List<LoanDetail>): List<UsageCount> =
        validLoans.groupBy { it.equipmentType }
            .map { entry -> UsageCount(entry.key, entry.value.size) }
            .sortedByDescending { it.value }

    /** Una fila por bloque horario, en el orden del día. */
    fun byBlock(validLoans: List<LoanDetail>): List<UsageCount> =
        TimeBlocks.all.map { block ->
            UsageCount(block.label, validLoans.count { it.loan.block == block.number })
        }

    /** Reservas por semana, de la más antigua a la más reciente. */
    fun weekly(validLoans: List<LoanDetail>): List<UsageCount> =
        validLoans.groupBy { weekKey(it.loan.dateEpochDay) }
            .entries
            .sortedBy { it.key.first }
            .map { UsageCount(it.key.second, it.value.size) }

    fun equipmentRanking(validLoans: List<LoanDetail>, equipment: List<EquipmentEntity>): List<EquipmentUsage> {
        val counts = validLoans.groupingBy { it.loan.equipmentId }.eachCount()
        return equipment
            .map { EquipmentUsage(it, counts[it.id] ?: 0) }
            .sortedByDescending { it.total }
    }

    private fun weekKey(epochDay: Long): Pair<String, String> {
        val date = LocalDate.ofEpochDay(epochDay)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val sortKey = "${year.toString().padStart(4, '0')}-${week.toString().padStart(2, '0')}"
        return sortKey to "Semana $week de $year"
    }
}
