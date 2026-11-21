package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.LoanDetail
import com.duoc.prestaaula.data.local.entity.LoanStatus
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EquipmentDetailUiState(
    val equipment: EquipmentEntity? = null,
    val history: List<LoanDetail> = emptyList(),
    val lastResponsible: LoanDetail? = null,
    val loaded: Boolean = false
)

class EquipmentDetailViewModel(
    equipmentRepo: EquipmentRepository,
    loanRepo: LoanRepository,
    equipmentId: Long
) : ViewModel() {
    val uiState: StateFlow<EquipmentDetailUiState> = combine(
        equipmentRepo.observeById(equipmentId),
        loanRepo.observeByEquipment(equipmentId)
    ) { equipment, history ->
        EquipmentDetailUiState(
            equipment = equipment,
            history = history,
            lastResponsible = history.firstOrNull {
                it.loan.status == LoanStatus.RETIRADA || it.loan.status == LoanStatus.DEVUELTA
            },
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentDetailUiState())
}
