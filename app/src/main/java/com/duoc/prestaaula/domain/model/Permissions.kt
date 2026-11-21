package com.duoc.prestaaula.domain.model

import com.duoc.prestaaula.data.local.entity.Role

/**
 * Permisos por rol. La interfaz los usa para mostrar u ocultar opciones, pero los repositorios
 * vuelven a verificarlos contra la base de datos en cada operación protegida.
 */
object Permissions {
    /** Crear y editar equipos del inventario. */
    fun canEditEquipment(role: Role): Boolean = role == Role.ENCARGADO_CRA || role == Role.JEFE_ADMIN

    /** Funciones administrativas: bloqueos, retiro, eliminación, usuarios, auditoría. */
    fun isAdmin(role: Role): Boolean = role == Role.JEFE_ADMIN

    /** Consultar el uso pedagógico de los equipos (paneles, ocupación, incidencias y reportes). Solo lectura. */
    fun canViewUsage(role: Role): Boolean = role == Role.JEFE_UTP || role == Role.JEFE_ADMIN
}
