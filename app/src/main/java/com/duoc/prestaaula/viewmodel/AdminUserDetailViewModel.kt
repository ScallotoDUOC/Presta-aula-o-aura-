package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.local.entity.ReturnCondition
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.AuditTargets
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.domain.model.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserStats(
    val total: Int = 0,
    val active: Int = 0,
    val returned: Int = 0,
    val cancelled: Int = 0,
    val incidents: Int = 0,
    val lastDay: Long? = null
)

data class AdminUserDetailUiState(
    val user: UserEntity? = null,
    val loaded: Boolean = false,
    val loans: List<LoanDetail> = emptyList(),
    val stats: UserStats = UserStats(),
    val changes: List<AuditLogEntity> = emptyList(),
    /** null si el administrador puede suspender/reactivar; si no, explica por qué no. */
    val lockedReason: String? = null,
    val feedback: Feedback? = null
)

class AdminUserDetailViewModel(
    private val adminRepo: AdminRepository,
    private val actor: UserEntity,
    private val userId: Long
) : ViewModel() {
    private val feedback = MutableStateFlow<Feedback?>(null)

    val uiState: StateFlow<AdminUserDetailUiState> = combine(
        adminRepo.observeUser(userId),
        adminRepo.observeLoansOf(userId),
        adminRepo.observeAudit(),
        feedback
    ) { user, loans, audit, fb ->
        val lockedReason = when {
            user == null -> null
            user.id == actor.id -> "No puedes suspender ni reactivar tu propia cuenta."
            Permissions.isAdmin(user.role) -> "Las cuentas de administrador no se pueden modificar desde aquí."
            else -> null
        }
        AdminUserDetailUiState(
            user = user,
            loaded = true,
            loans = loans,
            stats = UserStats(
                total = loans.size,
                active = loans.count { it.loan.status == LoanStatus.RESERVADA || it.loan.status == LoanStatus.RETIRADA },
                returned = loans.count { it.loan.status == LoanStatus.DEVUELTA },
                cancelled = loans.count { it.loan.status == LoanStatus.CANCELADA },
                incidents = loans.count {
                    val c = it.loan.returnCondition
                    c != null && c != ReturnCondition.BUEN_ESTADO
                },
                lastDay = loans.maxOfOrNull { it.loan.dateEpochDay }
            ),
            changes = audit.filter { it.targetType == AuditTargets.USER && it.targetId == userId },
            lockedReason = lockedReason,
            feedback = fb
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUserDetailUiState())

    fun clearFeedback() { feedback.value = null }

    fun suspendAccount(reason: String) = execute("Cuenta suspendida") { adminRepo.suspendUser(actor.id, userId, reason) }
    fun reactivate() = execute("Cuenta reactivada") { adminRepo.reactivateUser(actor.id, userId) }

    private fun execute(success: String, action: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            feedback.value = when (val r = action()) {
                is Outcome.Success -> Feedback(success, false)
                is Outcome.Failure -> Feedback(r.message, true)
            }
        }
    }
}
