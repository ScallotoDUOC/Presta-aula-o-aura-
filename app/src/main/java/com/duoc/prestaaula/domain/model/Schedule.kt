package com.duoc.prestaaula.domain.model

/** Bloque horario de clases. */
data class TimeBlock(val number: Int, val label: String)

object TimeBlocks {
    // Horario de referencia: ajusta los textos a la jornada real del colegio.
    val all: List<TimeBlock> = listOf(
        TimeBlock(1, "Bloque 1 (08:00 - 08:45)"),
        TimeBlock(2, "Bloque 2 (08:45 - 09:30)"),
        TimeBlock(3, "Bloque 3 (09:45 - 10:30)"),
        TimeBlock(4, "Bloque 4 (10:30 - 11:15)"),
        TimeBlock(5, "Bloque 5 (11:30 - 12:15)"),
        TimeBlock(6, "Bloque 6 (12:15 - 13:00)"),
        TimeBlock(7, "Bloque 7 (14:00 - 14:45)"),
        TimeBlock(8, "Bloque 8 (14:45 - 15:30)")
    )

    fun label(number: Int): String = all.firstOrNull { it.number == number }?.label ?: "Bloque $number"
}

/** Días de la semana usados en la disponibilidad del docente (1 = lunes ... 5 = viernes). */
object WeekDays {
    private val names = mapOf(1 to "Lunes", 2 to "Martes", 3 to "Miércoles", 4 to "Jueves", 5 to "Viernes")
    val numbers: List<Int> = names.keys.toList()
    fun name(day: Int): String = names[day] ?: "Día $day"
}

/** Tipos de equipo disponibles al crear o editar un equipo. */
object EquipmentTypes {
    val all: List<String> = listOf("Notebook", "Tablet", "Proyector", "Cable HDMI", "Parlante", "Otro")
}
