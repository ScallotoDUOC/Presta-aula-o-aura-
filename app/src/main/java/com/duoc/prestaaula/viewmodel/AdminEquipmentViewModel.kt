package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import com.duoc.prestaaula.domain.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mensaje de resultado de una acción administrativa. */
data class Feedback(val text: String, val isError: Boolean)

enum class EquipmentFilter(val label: String, val status: EquipmentStatus?) {
    TODOS("Todos", null),
    DISPONIBLES("Disponibles", EquipmentStatus.DISPONIBLE),
    PRESTADOS("Prestados", EquipmentStatus.PRESTADO),
    MANTENCION("Mantención", EquipmentStatus.MANTENCION),
    BLOQUEADOS("Bloqueados", EquipmentStatus.BLOQUEADO),
    RETIRADOS("Retirados", EquipmentStatus.RETIRADO)
}

data class AdminEquipmentRow(val equipment: EquipmentEntity, val totalReservations: Int)

data class AdminEquipmentUiState(
    val filter: EquipmentFilter = EquipmentFilter.TODOS,
    val query: String = "",
    val rows: List<AdminEquipmentRow> = emptyList(),
    val totalCount: Int = 0,
    val feedback: Feedback? = null
)

class AdminEquipmentViewModel(
    private val equipmentRepo: EquipmentRepository,
    loanRepo: LoanRepository,
    private val actor: UserEntity
) : ViewModel() {
    private val filter = MutableStateFlow(EquipmentFilter.TODOS)
    private val query = MutableStateFlow("")
    private val feedback = MutableStateFlow<Feedback?>(null)

    val uiState: StateFlow<AdminEquipmentUiState> = combine(
        equipmentRepo.observeAll(),
        loanRepo.observeStats(),
        filter,
        query,
        feedback
    ) { equipment, stats, f, q, fb ->
        val totals = stats.associate { it.equipmentId to it.total }
        val term = q.trim()
        val rows = equipment
            .filter { f.status == null || it.status == f.status }
            .filter { e ->
                term.isEmpty() || listOf(e.type, e.brand, e.internalCode).any { it.contains(term, ignoreCase = true) }
            }
            .map { AdminEquipmentRow(it, totals[it.id] ?: 0) }
        AdminEquipmentUiState(f, q, rows, equipment.size, fb)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminEquipmentUiState())

    fun onFilterChange(f: EquipmentFilter) { filter.value = f }
    fun onQueryChange(value: String) { query.value = value }
    fun clearFeedback() { feedback.value = null }

    fun block(id: Long, reason: String) = execute("Equipo bloqueado") { equipmentRepo.block(actor.id, id, reason) }
    fun unblock(id: Long) = execute("Equipo desbloqueado") { equipmentRepo.unblock(actor.id, id) }
    fun retire(id: Long, reason: String) = execute("Equipo retirado del catálogo") { equipmentRepo.retire(actor.id, id, reason) }
    fun reactivate(id: Long) = execute("Equipo reactivado") { equipmentRepo.reactivate(actor.id, id) }
    fun delete(id: Long) = execute("Equipo eliminado") { equipmentRepo.delete(actor.id, id) }

    private fun execute(success: String, action: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            feedback.value = when (val r = action()) {
                is Outcome.Success -> Feedback(success, false)
                is Outcome.Failure -> Feedback(r.message, true)
            }
        }
    }
}
