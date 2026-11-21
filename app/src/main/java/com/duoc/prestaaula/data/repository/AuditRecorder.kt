package com.duoc.prestaaula.data.repository

import com.duoc.prestaaula.data.local.dao.AuditLogDao
import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.data.local.entity.UserEntity

/** Tipos de elemento afectado que se guardan en el registro de auditoría. */
object AuditTargets {
    const val EQUIPMENT = "Equipo"
    const val BLOCK = "Bloqueo"
    const val USER = "Usuario"
    const val REPORT = "Reporte"
}

/** Registra una acción administrativa. Se invoca dentro de la misma transacción que el cambio. */
internal suspend fun AuditLogDao.record(
    actor: UserEntity,
    action: AuditAction,
    targetType: String,
    targetId: Long?,
    targetLabel: String,
    previousValue: String? = null,
    newValue: String? = null,
    reason: String? = null
) {
    insert(
        AuditLogEntity(
            actorId = actor.id,
            actorName = actor.name,
            actorRole = actor.role.label,
            action = action,
            targetType = targetType,
            targetId = targetId,
            targetLabel = targetLabel,
            previousValue = previousValue,
            newValue = newValue,
            reason = reason
        )
    )
}
