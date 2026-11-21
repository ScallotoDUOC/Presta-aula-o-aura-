package com.duoc.prestaaula.data.repository

import androidx.room.withTransaction
import com.duoc.prestaaula.data.local.PrestaAulaDatabase
import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.BlockDetail
import com.duoc.prestaaula.data.local.entity.EquipmentBlockEntity
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.domain.model.Permissions
import com.duoc.prestaaula.domain.validation.Validators
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.Flow

private const val NO_PERMISSION = "No tienes permisos para realizar esta acción"
private const val PENDING_LOANS =
    "El equipo tiene reservas pendientes o préstamos sin devolver. Cancélalas o espera la devolución primero."

/**
 * Inventario de equipos. Toda operación que modifica datos recibe el id de quien la ejecuta,
 * verifica en la base de datos que tenga el rol necesario y deja constancia en la auditoría
 * dentro de la misma transacción.
 */
class EquipmentRepository(private val db: PrestaAulaDatabase) {
    private val dao = db.equipmentDao()
    private val userDao = db.userDao()
    private val loanDao = db.loanDao()
    private val blockDao = db.equipmentBlockDao()
    private val auditDao = db.auditLogDao()

    fun observeAll(): Flow<List<EquipmentEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<EquipmentEntity?> = dao.observeById(id)
    suspend fun getById(id: Long): EquipmentEntity? = dao.getById(id)

    fun observeActiveBlocks(): Flow<List<EquipmentBlockEntity>> = blockDao.observeActive()
    fun observeBlockDetails(): Flow<List<BlockDetail>> = blockDao.observeAllDetails()

    /** Devuelve al usuario si existe, está activo y tiene el permiso pedido; null en otro caso. */
    private suspend fun authorize(actorId: Long, adminOnly: Boolean): UserEntity? {
        val user = userDao.getById(actorId) ?: return null
        if (!user.active) return null
        val allowed = if (adminOnly) Permissions.isAdmin(user.role) else Permissions.canEditEquipment(user.role)
        return if (allowed) user else null
    }

    private fun label(e: EquipmentEntity) = "${e.displayName} (${e.internalCode})"

    /** Crea (id = 0) o actualiza un equipo del inventario. */
    suspend fun save(
        actorId: Long,
        id: Long,
        type: String,
        brand: String,
        code: String,
        status: EquipmentStatus,
        notes: String
    ): Outcome<Long> = db.withTransaction {
        val actor = authorize(actorId, adminOnly = false)
            ?: return@withTransaction Outcome.Failure(NO_PERMISSION)
        val normalizedCode = code.trim().uppercase()
        val existing = dao.findByCode(normalizedCode)
        if (existing != null && existing.id != id) {
            return@withTransaction Outcome.Failure("El código $normalizedCode ya existe en el inventario")
        }
        if (id == 0L) {
            val newId = dao.insert(
                EquipmentEntity(
                    type = type.trim(),
                    brand = brand.trim(),
                    internalCode = normalizedCode,
                    status = EquipmentStatus.DISPONIBLE,
                    notes = notes.trim()
                )
            )
            auditDao.record(
                actor, AuditAction.EQUIPO_CREADO, AuditTargets.EQUIPMENT, newId,
                "${type.trim()} ${brand.trim()} ($normalizedCode)",
                newValue = EquipmentStatus.DISPONIBLE.label
            )
            return@withTransaction Outcome.Success(newId)
        }
        val current = dao.getById(id) ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        val newStatus = if (current.status == EquipmentStatus.PRESTADO) current.status else status
        if (newStatus != current.status) {
            val isAdmin = Permissions.isAdmin(actor.role)
            val restricted = setOf(EquipmentStatus.RETIRADO, EquipmentStatus.BLOQUEADO)
            if (!isAdmin && (newStatus in restricted || current.status in restricted)) {
                return@withTransaction Outcome.Failure("Solo el administrador puede bloquear, retirar o reactivar equipos")
            }
            if (newStatus in restricted && loanDao.countPendingForEquipment(id, DateUtils.today()) > 0) {
                return@withTransaction Outcome.Failure(PENDING_LOANS)
            }
        }
        val updated = current.copy(
            type = type.trim(),
            brand = brand.trim(),
            internalCode = normalizedCode,
            status = newStatus,
            notes = notes.trim()
        )
        dao.update(updated)
        if (newStatus != current.status) {
            auditDao.record(
                actor, AuditAction.EQUIPO_ESTADO, AuditTargets.EQUIPMENT, id, label(updated),
                previousValue = current.status.label, newValue = newStatus.label
            )
        } else if (updated != current) {
            auditDao.record(actor, AuditAction.EQUIPO_EDITADO, AuditTargets.EQUIPMENT, id, label(updated))
        }
        Outcome.Success(id)
    }

    // ------------------------------------------------------------------
    // Estados administrativos (solo administrador)
    // ------------------------------------------------------------------

    private suspend fun transition(
        actorId: Long,
        equipmentId: Long,
        action: AuditAction,
        target: EquipmentStatus,
        allowedFrom: Set<EquipmentStatus>,
        notAllowedMessage: String,
        requireNoPending: Boolean,
        reason: String?
    ): Outcome<Unit> = db.withTransaction {
        val actor = authorize(actorId, adminOnly = true)
            ?: return@withTransaction Outcome.Failure(NO_PERMISSION)
        val equipment = dao.getById(equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        if (equipment.status !in allowedFrom) {
            return@withTransaction Outcome.Failure(notAllowedMessage)
        }
        if (requireNoPending && loanDao.countPendingForEquipment(equipmentId, DateUtils.today()) > 0) {
            return@withTransaction Outcome.Failure(PENDING_LOANS)
        }
        dao.update(equipment.copy(status = target))
        auditDao.record(
            actor, action, AuditTargets.EQUIPMENT, equipment.id, label(equipment),
            previousValue = equipment.status.label, newValue = target.label, reason = reason
        )
        Outcome.Success(Unit)
    }

    /** Bloqueo manual e indefinido: el equipo no admite nuevas reservas hasta desbloquearlo. */
    suspend fun block(actorId: Long, equipmentId: Long, reason: String): Outcome<Unit> {
        Validators.blockReason(reason)?.let { return Outcome.Failure(it) }
        return transition(
            actorId, equipmentId, AuditAction.EQUIPO_BLOQUEADO, EquipmentStatus.BLOQUEADO,
            setOf(EquipmentStatus.DISPONIBLE, EquipmentStatus.MANTENCION),
            "Solo se puede bloquear un equipo disponible o en mantención",
            requireNoPending = true, reason = reason.trim()
        )
    }

    suspend fun unblock(actorId: Long, equipmentId: Long): Outcome<Unit> = transition(
        actorId, equipmentId, AuditAction.EQUIPO_DESBLOQUEADO, EquipmentStatus.DISPONIBLE,
        setOf(EquipmentStatus.BLOQUEADO), "El equipo no está bloqueado",
        requireNoPending = false, reason = null
    )

    /** Retira el equipo del catálogo conservando todo su historial. */
    suspend fun retire(actorId: Long, equipmentId: Long, reason: String): Outcome<Unit> {
        Validators.blockReason(reason)?.let { return Outcome.Failure(it) }
        return transition(
            actorId, equipmentId, AuditAction.EQUIPO_RETIRADO, EquipmentStatus.RETIRADO,
            setOf(EquipmentStatus.DISPONIBLE, EquipmentStatus.MANTENCION, EquipmentStatus.BLOQUEADO),
            "Un equipo prestado debe devolverse antes de retirarlo",
            requireNoPending = true, reason = reason.trim()
        )
    }

    suspend fun reactivate(actorId: Long, equipmentId: Long): Outcome<Unit> = transition(
        actorId, equipmentId, AuditAction.EQUIPO_REACTIVADO, EquipmentStatus.DISPONIBLE,
        setOf(EquipmentStatus.RETIRADO), "El equipo no está retirado",
        requireNoPending = false, reason = null
    )

    /** Elimina el equipo solo si nunca tuvo préstamos; si tiene historial hay que retirarlo. */
    suspend fun delete(actorId: Long, equipmentId: Long): Outcome<Unit> = db.withTransaction {
        val actor = authorize(actorId, adminOnly = true)
            ?: return@withTransaction Outcome.Failure(NO_PERMISSION)
        val equipment = dao.getById(equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        if (loanDao.countByEquipment(equipmentId) > 0) {
            return@withTransaction Outcome.Failure(
                "El equipo tiene historial de préstamos y no se puede eliminar. Usa «Retirar del catálogo» para conservarlo."
            )
        }
        dao.delete(equipment)
        auditDao.record(
            actor, AuditAction.EQUIPO_ELIMINADO, AuditTargets.EQUIPMENT, equipment.id, label(equipment),
            previousValue = equipment.status.label
        )
        Outcome.Success(Unit)
    }

    // ------------------------------------------------------------------
    // Bloqueos programados por fecha y bloque (solo administrador)
    // ------------------------------------------------------------------

    suspend fun createBlock(
        actorId: Long,
        equipmentId: Long,
        startDay: Long,
        endDay: Long,
        startBlock: Int,
        endBlock: Int,
        reason: String
    ): Outcome<Long> = db.withTransaction {
        val actor = authorize(actorId, adminOnly = true)
            ?: return@withTransaction Outcome.Failure(NO_PERMISSION)
        Validators.blockReason(reason)?.let { return@withTransaction Outcome.Failure(it) }
        Validators.blockPeriod(startDay, endDay, DateUtils.today())?.let { return@withTransaction Outcome.Failure(it) }
        Validators.availabilityRange(startBlock, endBlock)?.let { return@withTransaction Outcome.Failure(it) }
        val equipment = dao.getById(equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        if (equipment.status == EquipmentStatus.RETIRADO) {
            return@withTransaction Outcome.Failure("El equipo está retirado del catálogo")
        }
        val conflicts = loanDao.countActiveInRange(equipmentId, startDay, endDay, startBlock, endBlock)
        if (conflicts > 0) {
            return@withTransaction Outcome.Failure(
                "Hay $conflicts reserva(s) activas en ese período. Cancélalas antes de bloquear el equipo."
            )
        }
        val id = blockDao.insert(
            EquipmentBlockEntity(
                equipmentId = equipmentId,
                startDay = startDay,
                endDay = endDay,
                startBlock = startBlock,
                endBlock = endBlock,
                reason = reason.trim(),
                createdById = actor.id,
                createdByName = actor.name
            )
        )
        auditDao.record(
            actor, AuditAction.BLOQUEO_PROGRAMADO, AuditTargets.BLOCK, id, label(equipment),
            newValue = "${DateUtils.format(startDay)} al ${DateUtils.format(endDay)}, bloques $startBlock a $endBlock",
            reason = reason.trim()
        )
        Outcome.Success(id)
    }

    suspend fun liftBlock(actorId: Long, blockId: Long): Outcome<Unit> = db.withTransaction {
        val actor = authorize(actorId, adminOnly = true)
            ?: return@withTransaction Outcome.Failure(NO_PERMISSION)
        val block = blockDao.getById(blockId)
            ?: return@withTransaction Outcome.Failure("Bloqueo no encontrado")
        if (!block.active) {
            return@withTransaction Outcome.Failure("Este bloqueo ya fue levantado")
        }
        blockDao.update(block.copy(active = false, liftedAt = System.currentTimeMillis(), liftedByName = actor.name))
        val equipment = dao.getById(block.equipmentId)
        auditDao.record(
            actor, AuditAction.BLOQUEO_LEVANTADO, AuditTargets.BLOCK, block.id,
            equipment?.let { label(it) } ?: "Equipo #${block.equipmentId}",
            previousValue = "${DateUtils.format(block.startDay)} al ${DateUtils.format(block.endDay)}, bloques ${block.startBlock} a ${block.endBlock}",
            reason = block.reason
        )
        Outcome.Success(Unit)
    }
}
