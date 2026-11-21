package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.BlockDetail
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.AdminRepository
import com.duoc.prestaaula.data.repository.AuditTargets
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BlockFilter(val label: String) {
    VIGENTES("Vigentes"),
    PROGRAMADOS("Programados"),
    HISTORIAL("Historial")
}

/** Equipo bloqueado de forma manual (sin fecha de término) y los datos de quién lo bloqueó. */
data class ManualBlock(
    val equipment: EquipmentEntity,
    val byName: String?,
    val at: Long?,
    val reason: String?
)

data class AdminBlocksUiState(
    val filter: BlockFilter = BlockFilter.VIGENTES,
    val manual: List<ManualBlock> = emptyList(),
    val blocks: List<BlockDetail> = emptyList(),
    val feedback: Feedback? = null
)

class AdminBlocksViewModel(
    private val equipmentRepo: EquipmentRepository,
    adminRepo: AdminRepository,
    private val actor: UserEntity
) : ViewModel() {
    private val filter = MutableStateFlow(BlockFilter.VIGENTES)
    private val feedback = MutableStateFlow<Feedback?>(null)

    val uiState: StateFlow<AdminBlocksUiState> = combine(
        equipmentRepo.observeAll(),
        equipmentRepo.observeBlockDetails(),
        adminRepo.observeAudit(),
        filter,
        feedback
    ) { equipment, details, audit, f, fb ->
        val today = DateUtils.today()
        val manual = equipment
            .filter { it.status == EquipmentStatus.BLOQUEADO }
            .map { e ->
                val entry = audit.firstOrNull {
                    it.action == AuditAction.EQUIPO_BLOQUEADO &&
                        it.targetType == AuditTargets.EQUIPMENT && it.targetId == e.id
                }
                ManualBlock(e, entry?.actorName, entry?.createdAt, entry?.reason)
            }
        val shown = details.filter { d ->
            val b = d.block
            when (f) {
                BlockFilter.VIGENTES -> b.active && today in b.startDay..b.endDay
                BlockFilter.PROGRAMADOS -> b.active && b.startDay > today
                BlockFilter.HISTORIAL -> !b.active || b.endDay < today
            }
        }
        AdminBlocksUiState(f, manual, shown, fb)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminBlocksUiState())

    fun onFilterChange(f: BlockFilter) { filter.value = f }
    fun clearFeedback() { feedback.value = null }

    fun unblock(equipmentId: Long) = execute("Equipo desbloqueado") { equipmentRepo.unblock(actor.id, equipmentId) }
    fun lift(blockId: Long) = execute("Bloqueo levantado") { equipmentRepo.liftBlock(actor.id, blockId) }

    private fun execute(success: String, action: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            feedback.value = when (val r = action()) {
                is Outcome.Success -> Feedback(success, false)
                is Outcome.Failure -> Feedback(r.message, true)
            }
        }
    }
}
