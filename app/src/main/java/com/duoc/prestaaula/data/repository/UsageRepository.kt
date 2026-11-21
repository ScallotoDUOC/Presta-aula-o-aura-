package com.duoc.prestaaula.data.repository

import com.duoc.prestaaula.data.local.PrestaAulaDatabase
import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.Role
import com.duoc.prestaaula.domain.model.Permissions
import com.duoc.prestaaula.domain.usage.TeacherRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Datos de apoyo para el perfil Jefe UTP (solo lectura). Entrega únicamente lo necesario:
 * del docente se expone el nombre, nunca el correo ni la clave.
 */
class UsageRepository(private val db: PrestaAulaDatabase) {
    private val userDao = db.userDao()
    private val auditDao = db.auditLogDao()

    /** Docentes con cuenta activa, para detectar a quienes todavía no usan los equipos. */
    fun observeTeachers(): Flow<List<TeacherRef>> =
        userDao.observeAll().map { users ->
            users.filter { it.role == Role.DOCENTE && it.active }
                .map { TeacherRef(it.id, it.name) }
        }

    /** Deja constancia de la exportación. Solo la registra si quien exporta puede ver el uso. */
    suspend fun logExport(actorId: Long, reportName: String, rowCount: Int, filters: String) {
        val actor = userDao.getById(actorId) ?: return
        if (!actor.active || !Permissions.canViewUsage(actor.role)) return
        auditDao.record(
            actor, AuditAction.REPORTE_EXPORTADO, AuditTargets.REPORT, null, reportName,
            newValue = "$rowCount filas", reason = filters
        )
    }
}
