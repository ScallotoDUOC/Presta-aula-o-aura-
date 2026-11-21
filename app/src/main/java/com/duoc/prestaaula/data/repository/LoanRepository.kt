package com.duoc.prestaaula.data.repository

import androidx.room.withTransaction
import com.duoc.prestaaula.data.local.PrestaAulaDatabase
import com.duoc.prestaaula.data.local.dao.EquipmentDao
import com.duoc.prestaaula.data.local.dao.LoanDao
import com.duoc.prestaaula.data.local.entity.EquipmentStats
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanEntity
import com.duoc.prestaaula.data.local.entity.LoanHistoryDetail
import com.duoc.prestaaula.data.local.entity.LoanHistoryEntity
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * Reglas de negocio de reservas y préstamos. Cada movimiento actualiza el inventario
 * en la misma transacción, de modo que la disponibilidad siempre esté al día.
 */
class LoanRepository(
    private val db: PrestaAulaDatabase,
    private val loanDao: LoanDao,
    private val equipmentDao: EquipmentDao
) {
    private val blockDao = db.equipmentBlockDao()
    private val historyDao = db.loanHistoryDao()

    fun observeDetail(id: Long): Flow<LoanDetail?> = loanDao.observeDetail(id)
    fun observeAllDetails(): Flow<List<LoanDetail>> = loanDao.observeAllDetails()
    fun observeByTeacher(teacherId: Long): Flow<List<LoanDetail>> = loanDao.observeByTeacher(teacherId)
    fun observeByEquipment(equipmentId: Long): Flow<List<LoanDetail>> = loanDao.observeByEquipment(equipmentId)
    fun observeActiveByDate(date: Long): Flow<List<LoanDetail>> = loanDao.observeActiveByDate(date)
    fun observeIncidents(): Flow<List<LoanDetail>> = loanDao.observeIncidents()
    fun observeStats(): Flow<List<EquipmentStats>> = loanDao.observeStats()
    fun observeActiveCount(): Flow<Int> = loanDao.observeActiveCount()
    fun observeHistory(loanId: Long): Flow<List<LoanHistoryDetail>> = historyDao.observeByLoan(loanId)

    private suspend fun recordHistory(loanId: Long, from: LoanStatus?, to: LoanStatus, actorId: Long?, note: String = "") {
        historyDao.insert(LoanHistoryEntity(loanId = loanId, fromStatus = from, toStatus = to, actorId = actorId, note = note))
    }

    /**
     * Retorna un mensaje si el bloque no está disponible, o null si está libre.
     * Solo se bloquea cuando ESE equipo ya está reservado: un docente puede reservar
     * varios equipos distintos para el mismo bloque (p. ej. notebook + proyector).
     * Para volver a limitar a una reserva por docente y bloque, restituir la validación
     * con loanDao.countTeacherConflicts(teacherId, date, block).
     */
    suspend fun checkSlot(equipmentId: Long, teacherId: Long, date: Long, block: Int): String? {
        // Estado administrativo del equipo: mantención, bloqueado o retirado no admiten reservas.
        when (equipmentDao.getById(equipmentId)?.status) {
            EquipmentStatus.MANTENCION -> return "El equipo está en mantención y no se puede reservar"
            EquipmentStatus.BLOQUEADO -> return "El equipo está bloqueado por decisión administrativa"
            EquipmentStatus.RETIRADO -> return "El equipo fue retirado del catálogo"
            else -> Unit
        }
        // Bloqueo programado que cubre esa fecha y bloque.
        blockDao.findActiveAt(equipmentId, date, block)?.let {
            return "El equipo está bloqueado en ese bloque: ${it.reason}"
        }
        if (loanDao.countEquipmentConflicts(equipmentId, date, block) > 0) {
            return "Este equipo ya está reservado en ese bloque"
        }
        return null
    }

    suspend fun reserve(
        equipmentId: Long,
        teacherId: Long,
        course: String,
        date: Long,
        block: Int
    ): Outcome<Long> = db.withTransaction {
        val equipment = equipmentDao.getById(equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        if (equipment.status == EquipmentStatus.MANTENCION) {
            return@withTransaction Outcome.Failure("El equipo está en mantención y no se puede reservar")
        }
        if (date < DateUtils.today()) {
            return@withTransaction Outcome.Failure("La fecha no puede ser anterior a hoy")
        }
        val conflict = checkSlot(equipmentId, teacherId, date, block)
        if (conflict != null) return@withTransaction Outcome.Failure(conflict)
        val id = loanDao.insert(
            LoanEntity(
                equipmentId = equipmentId,
                teacherId = teacherId,
                course = course.trim(),
                dateEpochDay = date,
                block = block
            )
        )
        recordHistory(id, null, LoanStatus.RESERVADA, teacherId, "Reserva creada")
        Outcome.Success(id)
    }

    suspend fun pickup(loanId: Long, actorId: Long? = null): Outcome<Unit> = db.withTransaction {
        val loan = loanDao.getById(loanId)
            ?: return@withTransaction Outcome.Failure("Préstamo no encontrado")
        if (loan.status != LoanStatus.RESERVADA) {
            return@withTransaction Outcome.Failure("Solo se pueden retirar reservas activas")
        }
        if (loan.dateEpochDay > DateUtils.today()) {
            return@withTransaction Outcome.Failure(
                "El retiro se habilita desde el día de la reserva (${DateUtils.format(loan.dateEpochDay)})"
            )
        }
        val equipment = equipmentDao.getById(loan.equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        if (equipment.status == EquipmentStatus.PRESTADO) {
            return@withTransaction Outcome.Failure("El equipo aún no ha sido devuelto por el responsable anterior")
        }
        if (equipment.status == EquipmentStatus.MANTENCION) {
            return@withTransaction Outcome.Failure("El equipo está en mantención")
        }
        if (equipment.status == EquipmentStatus.BLOQUEADO || equipment.status == EquipmentStatus.RETIRADO) {
            return@withTransaction Outcome.Failure("El equipo está ${equipment.status.label.lowercase()} y no se puede retirar")
        }
        loanDao.update(loan.copy(status = LoanStatus.RETIRADA, pickedUpAt = System.currentTimeMillis()))
        equipmentDao.update(equipment.copy(status = EquipmentStatus.PRESTADO))
        recordHistory(loanId, LoanStatus.RESERVADA, LoanStatus.RETIRADA, actorId, "Retiro registrado")
        Outcome.Success(Unit)
    }

    /** Retorna Success(true) si el equipo quedó con incidencia (pasa a mantención). */
    suspend fun returnLoan(
        loanId: Long,
        condition: ReturnCondition,
        observations: String,
        photoPath: String?,
        actorId: Long? = null
    ): Outcome<Boolean> = db.withTransaction {
        val loan = loanDao.getById(loanId)
            ?: return@withTransaction Outcome.Failure("Préstamo no encontrado")
        if (loan.status != LoanStatus.RETIRADA) {
            return@withTransaction Outcome.Failure("Solo se pueden devolver equipos que fueron retirados")
        }
        val equipment = equipmentDao.getById(loan.equipmentId)
            ?: return@withTransaction Outcome.Failure("Equipo no encontrado")
        loanDao.update(
            loan.copy(
                status = LoanStatus.DEVUELTA,
                returnedAt = System.currentTimeMillis(),
                returnCondition = condition,
                observations = observations.trim(),
                photoPath = photoPath
            )
        )
        val damaged = condition != ReturnCondition.BUEN_ESTADO
        equipmentDao.update(
            equipment.copy(status = if (damaged) EquipmentStatus.MANTENCION else EquipmentStatus.DISPONIBLE)
        )
        recordHistory(loanId, LoanStatus.RETIRADA, LoanStatus.DEVUELTA, actorId, "Devuelto: ${condition.label}")
        Outcome.Success(damaged)
    }

    suspend fun cancel(loanId: Long, actorId: Long? = null): Outcome<Unit> = db.withTransaction {
        val loan = loanDao.getById(loanId)
            ?: return@withTransaction Outcome.Failure("Reserva no encontrada")
        if (loan.status != LoanStatus.RESERVADA) {
            return@withTransaction Outcome.Failure("Solo se pueden cancelar reservas pendientes de retiro")
        }
        loanDao.update(loan.copy(status = LoanStatus.CANCELADA))
        recordHistory(loanId, LoanStatus.RESERVADA, LoanStatus.CANCELADA, actorId, "Reserva cancelada")
        Outcome.Success(Unit)
    }
}
