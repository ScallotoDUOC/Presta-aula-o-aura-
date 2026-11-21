package com.duoc.prestaaula.viewmodel

import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.util.DateUtils

/** Convierte los datos de un reporte en filas de texto (la primera fila son los encabezados). */
object ReportCsv {
    private val loanHeader = listOf(
        "Fecha", "Bloque", "Equipo", "Código", "Docente", "Curso", "Estado",
        "Retirado el", "Devuelto el", "Estado al devolver", "Observaciones"
    )

    fun rows(type: ReportType, s: AdminReportsUiState): List<List<String>> = when (type) {
        ReportType.RESERVAS -> listOf(loanHeader) + s.loans.map { loanRow(it) }
        ReportType.CANCELADAS -> listOf(loanHeader) + s.cancelled.map { loanRow(it) }
        ReportType.MAS_RESERVADOS ->
            listOf(listOf("Posición", "Equipo", "Código", "Tipo", "Reservas")) +
                s.ranking.mapIndexed { i, r ->
                    listOf((i + 1).toString(), r.equipment.displayName, r.equipment.internalCode, r.equipment.type, r.total.toString())
                }
        ReportType.USUARIOS ->
            listOf(listOf("Nombre", "Correo", "Rol", "Cuenta", "Reservas", "Activas", "Devueltas", "Canceladas", "Con incidencia")) +
                s.userRows.map {
                    listOf(
                        it.user.name, it.user.email, it.user.role.label,
                        if (it.user.active) "Activa" else "Suspendida",
                        it.total.toString(), it.active.toString(), it.returned.toString(),
                        it.cancelled.toString(), it.incidents.toString()
                    )
                }
        ReportType.INVENTARIO ->
            listOf(listOf("Equipo", "Código", "Tipo", "Marca", "Estado", "Notas")) +
                s.equipmentRows.map {
                    listOf(it.displayName, it.internalCode, it.type, it.brand, it.status.label, it.notes)
                }
        ReportType.TIPOS ->
            listOf(listOf("Tipo de equipo", "Reservas")) + s.byType.map { listOf(it.label, it.value.toString()) }
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

    /** Separador ';' (el que usa Excel en configuración regional chilena) y saltos de línea CRLF. */
    fun toCsv(rows: List<List<String>>): String =
        rows.joinToString("\r\n") { row -> row.joinToString(";") { escape(it) } }

    private fun escape(value: String): String {
        // Evita que Excel interprete texto escrito por usuarios como una fórmula.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        val needsQuotes = safe.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
