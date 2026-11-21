package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.Role
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.LoanRepository
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class LoanFilter(val label: String, val status: LoanStatus?) {
    TODAS("Todas", null),
    RESERVADAS("Reservadas", LoanStatus.RESERVADA),
    RETIRADAS("Retiradas", LoanStatus.RETIRADA),
    DEVUELTAS("Devueltas", LoanStatus.DEVUELTA),
    CANCELADAS("Canceladas", LoanStatus.CANCELADA)
}

/** Rango de fechas opcional para filtrar el listado. */
data class DateRangeFilter(
    val enabled: Boolean = false,
    val from: Long = DateUtils.today() - 30,
    val to: Long = DateUtils.today() + 30
)

data class LoansUiState(
    val filter: LoanFilter = LoanFilter.TODAS,
    val query: String = "",
    val range: DateRangeFilter = DateRangeFilter(),
    val loans: List<LoanDetail> = emptyList()
)

class LoansViewModel(repo: LoanRepository, user: UserEntity) : ViewModel() {
    private val filter = MutableStateFlow(LoanFilter.TODAS)
    private val query = MutableStateFlow("")
    private val range = MutableStateFlow(DateRangeFilter())

    private val source = if (user.role == Role.DOCENTE) repo.observeByTeacher(user.id) else repo.observeAllDetails()

    val uiState: StateFlow<LoansUiState> = combine(source, filter, query, range) { loans, f, q, r ->
        val term = q.trim()
        val shown = loans.filter { d ->
            (f.status == null || d.loan.status == f.status) &&
                (term.isEmpty() ||
                    d.teacherName.contains(term, ignoreCase = true) ||
                    d.equipmentName.contains(term, ignoreCase = true) ||
                    d.equipmentCode.contains(term, ignoreCase = true)) &&
                (!r.enabled || d.loan.dateEpochDay in r.from..r.to)
        }
        LoansUiState(f, q, r, shown)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansUiState())

    fun onFilterChange(f: LoanFilter) { filter.value = f }
    fun onQueryChange(value: String) { query.value = value }
    fun onDateFilterToggle(enabled: Boolean) { range.update { it.copy(enabled = enabled) } }
    fun onFromChange(day: Long) { range.update { it.copy(from = day, to = maxOf(it.to, day)) } }
    fun onToChange(day: Long) { range.update { it.copy(to = day) } }
}
