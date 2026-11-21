package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MostReserved(val name: String, val code: String, val total: Int)

data class AdminDashboardUiState(
    val totalEquipment: Int = 0,
    val available: Int = 0,
    val lent: Int = 0,
    val maintenance: Int = 0,
    val blocked: Int = 0,
    val retired: Int = 0,
    val totalLoans: Int = 0,
    val reserved: Int = 0,
    val pickedUp: Int = 0,
    val returned: Int = 0,
    val cancelled: Int = 0,
    val totalUsers: Int = 0,
    val suspendedUsers: Int = 0,
    val mostReserved: List<MostReserved> = emptyList(),
    val recent: List<LoanDetail> = emptyList()
)

/** Todos los indicadores se calculan en vivo a partir de la base de datos. */
class AdminDashboardViewModel(
    equipmentRepo: EquipmentRepository,
    loanRepo: LoanRepository,
    adminRepo: AdminRepository
) : ViewModel() {
    val uiState: StateFlow<AdminDashboardUiState> = combine(
        equipmentRepo.observeAll(),
        loanRepo.observeAllDetails(),
        adminRepo.observeUsers()
    ) { equipment, loans, users ->
        val valid = loans.filter { it.loan.status != LoanStatus.CANCELADA }
        AdminDashboardUiState(
            totalEquipment = equipment.size,
            available = equipment.count { it.status == EquipmentStatus.DISPONIBLE },
            lent = equipment.count { it.status == EquipmentStatus.PRESTADO },
            maintenance = equipment.count { it.status == EquipmentStatus.MANTENCION },
            blocked = equipment.count { it.status == EquipmentStatus.BLOQUEADO },
            retired = equipment.count { it.status == EquipmentStatus.RETIRADO },
            totalLoans = loans.size,
            reserved = loans.count { it.loan.status == LoanStatus.RESERVADA },
            pickedUp = loans.count { it.loan.status == LoanStatus.RETIRADA },
            returned = loans.count { it.loan.status == LoanStatus.DEVUELTA },
            cancelled = loans.count { it.loan.status == LoanStatus.CANCELADA },
            totalUsers = users.size,
            suspendedUsers = users.count { !it.active },
            mostReserved = valid
                .groupBy { it.loan.equipmentId }
                .map { (_, list) -> MostReserved(list.first().equipmentName, list.first().equipmentCode, list.size) }
                .sortedByDescending { it.total }
                .take(5),
            recent = loans.sortedByDescending { it.loan.createdAt }.take(8)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminDashboardUiState())
}
