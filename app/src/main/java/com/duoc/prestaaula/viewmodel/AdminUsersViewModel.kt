package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class UserFilter(val label: String) {
    TODOS("Todos"),
    ACTIVOS("Activos"),
    SUSPENDIDOS("Suspendidos")
}

data class UserRow(val user: UserEntity, val totalLoans: Int, val activeLoans: Int)

data class AdminUsersUiState(
    val filter: UserFilter = UserFilter.TODOS,
    val query: String = "",
    val rows: List<UserRow> = emptyList(),
    val totalCount: Int = 0
)

class AdminUsersViewModel(adminRepo: AdminRepository, loanRepo: LoanRepository) : ViewModel() {
    private val filter = MutableStateFlow(UserFilter.TODOS)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AdminUsersUiState> = combine(
        adminRepo.observeUsers(),
        loanRepo.observeAllDetails(),
        filter,
        query
    ) { users, loans, f, q ->
        val byTeacher = loans.groupBy { it.loan.teacherId }
        val term = q.trim().removePrefix("#")
        val rows = users
            .filter {
                when (f) {
                    UserFilter.TODOS -> true
                    UserFilter.ACTIVOS -> it.active
                    UserFilter.SUSPENDIDOS -> !it.active
                }
            }
            .filter {
                term.isEmpty() ||
                    it.name.contains(term, ignoreCase = true) ||
                    it.email.contains(term, ignoreCase = true) ||
                    term.toLongOrNull() == it.id
            }
            .map { u ->
                val mine = byTeacher[u.id].orEmpty()
                UserRow(
                    u,
                    totalLoans = mine.size,
                    activeLoans = mine.count { it.loan.status == LoanStatus.RESERVADA || it.loan.status == LoanStatus.RETIRADA }
                )
            }
        AdminUsersUiState(f, q, rows, users.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUsersUiState())

    fun onFilterChange(f: UserFilter) { filter.value = f }
    fun onQueryChange(value: String) { query.value = value }
}
