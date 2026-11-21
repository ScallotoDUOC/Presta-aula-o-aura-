package com.duoc.prestaaula.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Role(val label: String) {
    DOCENTE("Docente"),
    ENCARGADO_CRA("Encargado CRA"),
    JEFE_UTP("Jefe de UTP"),
    JEFE_ADMIN("Jefe de Administración y Finanzas")
}

enum class EquipmentStatus(val label: String) {
    DISPONIBLE("Disponible"),
    PRESTADO("Prestado"),
    MANTENCION("En mantención"),
    BLOQUEADO("Bloqueado"),
    RETIRADO("Retirado")
}

enum class LoanStatus(val label: String) {
    RESERVADA("Reservada"),
    RETIRADA("Retirada"),
    DEVUELTA("Devuelta"),
    CANCELADA("Cancelada")
}

enum class ReturnCondition(val label: String) {
    BUEN_ESTADO("Buen estado"),
    CON_DANOS("Con daños"),
    INCOMPLETO("Incompleto (falta accesorio)")
}

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: Role,
    val active: Boolean = true
)

@Entity(tableName = "equipment", indices = [Index(value = ["internalCode"], unique = true)])
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val brand: String,
    val internalCode: String,
    val status: EquipmentStatus = EquipmentStatus.DISPONIBLE,
    val notes: String = ""
) {
    val displayName: String get() = "$type $brand"
}

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"]),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["teacherId"])
    ],
    indices = [Index("equipmentId"), Index("teacherId")]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentId: Long,
    val teacherId: Long,
    val course: String,
    val dateEpochDay: Long,
    val block: Int,
    val status: LoanStatus = LoanStatus.RESERVADA,
    val createdAt: Long = System.currentTimeMillis(),
    val pickedUpAt: Long? = null,
    val returnedAt: Long? = null,
    val returnCondition: ReturnCondition? = null,
    val observations: String = "",
    val photoPath: String? = null
)

@Entity(
    tableName = "availability",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["teacherId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("teacherId")]
)
data class AvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teacherId: Long,
    val dayOfWeek: Int,
    val startBlock: Int,
    val endBlock: Int
)

/**
 * Bloqueo programado: el equipo no se puede reservar entre las fechas y bloques indicados.
 * Guarda quién lo creó y cuándo; al levantarlo se conserva como historial (active = false).
 */
@Entity(
    tableName = "equipment_blocks",
    foreignKeys = [ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("equipmentId")]
)
data class EquipmentBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentId: Long,
    val startDay: Long,
    val endDay: Long,
    val startBlock: Int,
    val endBlock: Int,
    val reason: String,
    val createdById: Long,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val liftedAt: Long? = null,
    val liftedByName: String? = null
)

enum class AuditAction(val label: String) {
    EQUIPO_CREADO("Equipo creado"),
    EQUIPO_EDITADO("Equipo editado"),
    EQUIPO_ESTADO("Cambio de estado"),
    EQUIPO_RETIRADO("Equipo retirado del catálogo"),
    EQUIPO_REACTIVADO("Equipo reactivado"),
    EQUIPO_BLOQUEADO("Equipo bloqueado"),
    EQUIPO_DESBLOQUEADO("Equipo desbloqueado"),
    EQUIPO_ELIMINADO("Equipo eliminado"),
    BLOQUEO_PROGRAMADO("Bloqueo programado"),
    BLOQUEO_LEVANTADO("Bloqueo levantado"),
    USUARIO_SUSPENDIDO("Cuenta suspendida"),
    USUARIO_REACTIVADO("Cuenta reactivada"),
    REPORTE_EXPORTADO("Reporte exportado")
}

/** Registro de auditoría: solo se inserta y se consulta; no existe operación para editarlo ni borrarlo. */
@Entity(tableName = "audit_log", indices = [Index("createdAt")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorId: Long,
    val actorName: String,
    val actorRole: String,
    val action: AuditAction,
    val targetType: String,
    val targetId: Long?,
    val targetLabel: String,
    val previousValue: String? = null,
    val newValue: String? = null,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Historial de cambios de estado de cada reserva. */
@Entity(
    tableName = "loan_history",
    foreignKeys = [ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("loanId")]
)
data class LoanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val fromStatus: LoanStatus?,
    val toStatus: LoanStatus,
    val actorId: Long?,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
