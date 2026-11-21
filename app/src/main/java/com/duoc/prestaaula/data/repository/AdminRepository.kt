package com.duoc.prestaaula.data.repository

import androidx.room.withTransaction
import com.duoc.prestaaula.data.local.PrestaAulaDatabase
import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.domain.model.Permissions
import com.duoc.prestaaula.domain.validation.Validators
import kotlinx.coroutines.flow.Flow

/** Consultas y operaciones del panel de administración (usuarios, reportes y registro de auditoría). */
class AdminRepository(private val db: PrestaAulaDatabase) {
    private val userDao = db.userDao()
    private val loanDao = db.loanDao()
    private val auditDao = db.auditLogDao()

    fun observeUsers(): Flow<List<UserEntity>> = userDao.observeAll()
    fun observeUser(id: Long): Flow<UserEntity?> = userDao.observeById(id)
    fun observeLoansOf(userId: Long): Flow<List<LoanDetail>> = loanDao.observeByTeacher(userId)

    /** Más recientes primero. Solo lectura: el registro no se puede modificar ni borrar. */
    fun observeAudit(): Flow<List<AuditLogEntity>> = auditDao.observeAll()

    suspend fun suspendUser(actorId: Long, userId: Long, reason: String): Outcome<Unit> {
        Validators.blockReason(reason)?.let { return Outcome.Failure(it) }
        return changeActive(actorId, userId, active = false, reason = reason.trim())
    }

    suspend fun reactivateUser(actorId: Long, userId: Long): Outcome<Unit> =
        changeActive(actorId, userId, active = true, reason = null)

    /** Deja constancia de que un administrador exportó un reporte (con los filtros usados). */
    suspend fun logExport(actorId: Long, reportName: String, rowCount: Int, filters: String) {
        val actor = userDao.getById(actorId) ?: return
        if (!actor.active || !Permissions.isAdmin(actor.role)) return
        auditDao.record(
            actor, AuditAction.REPORTE_EXPORTADO, AuditTargets.REPORT, null, reportName,
            newValue = "$rowCount filas", reason = filters
        )
    }

    private suspend fun changeActive(actorId: Long, userId: Long, active: Boolean, reason: String?): Outcome<Unit> =
        db.withTransaction {
            val actor = userDao.getById(actorId)
            if (actor == null || !actor.active || !Permissions.isAdmin(actor.role)) {
                return@withTransaction Outcome.Failure("No tienes permisos para realizar esta acción")
            }
            if (actor.id == userId) {
                return@withTransaction Outcome.Failure("No puedes suspender ni reactivar tu propia cuenta")
            }
            val target = userDao.getById(userId)
                ?: return@withTransaction Outcome.Failure("Usuario no encontrado")
            if (Permissions.isAdmin(target.role)) {
                return@withTransaction Outcome.Failure("No se pueden modificar cuentas de administrador")
            }
            if (target.active == active) {
                return@withTransaction Outcome.Failure(
                    if (active) "La cuenta ya está activa" else "La cuenta ya está suspendida"
                )
            }
            userDao.update(target.copy(active = active))
            auditDao.record(
                actor,
                if (active) AuditAction.USUARIO_REACTIVADO else AuditAction.USUARIO_SUSPENDIDO,
                AuditTargets.USER, target.id, "${target.name} (${target.email})",
                previousValue = if (active) "Suspendida" else "Activa",
                newValue = if (active) "Activa" else "Suspendida",
                reason = reason
            )
            Outcome.Success(Unit)
        }
}
