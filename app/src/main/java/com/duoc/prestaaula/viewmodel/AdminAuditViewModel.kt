package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.AuditTargets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AuditFilter(val label: String, val target: String?) {
    TODOS("Todo", null),
    EQUIPOS("Equipos", AuditTargets.EQUIPMENT),
    BLOQUEOS("Bloqueos", AuditTargets.BLOCK),
    USUARIOS("Usuarios", AuditTargets.USER),
    REPORTES("Reportes", AuditTargets.REPORT)
}

data class AdminAuditUiState(
    val filter: AuditFilter = AuditFilter.TODOS,
    val query: String = "",
    val entries: List<AuditLogEntity> = emptyList(),
    val totalCount: Int = 0
)

/** Solo lectura: el historial de auditoría no se puede editar ni eliminar. */
class AdminAuditViewModel(adminRepo: AdminRepository) : ViewModel() {
    private val filter = MutableStateFlow(AuditFilter.TODOS)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AdminAuditUiState> = combine(adminRepo.observeAudit(), filter, query) { all, f, q ->
        val term = q.trim()
        val shown = all
            .filter { f.target == null || it.targetType == f.target }
            .filter {
                term.isEmpty() ||
                    it.actorName.contains(term, ignoreCase = true) ||
                    it.targetLabel.contains(term, ignoreCase = true) ||
                    it.action.label.contains(term, ignoreCase = true) ||
                    (it.reason?.contains(term, ignoreCase = true) ?: false)
            }
        AdminAuditUiState(f, q, shown, all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminAuditUiState())

    fun onFilterChange(f: AuditFilter) { filter.value = f }
    fun onQueryChange(value: String) { query.value = value }
}
