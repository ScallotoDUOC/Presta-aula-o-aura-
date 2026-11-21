package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.domain.validation.Validators
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminBlockFormUiState(
    val equipment: List<EquipmentEntity> = emptyList(),
    val equipmentId: Long = 0L,
    val startDay: Long = DateUtils.today(),
    val endDay: Long = DateUtils.today(),
    val startBlock: Int = TimeBlocks.all.first().number,
    val endBlock: Int = TimeBlocks.all.last().number,
    val reason: String = "",
    val equipmentError: String? = null,
    val dateError: String? = null,
    val blockError: String? = null,
    val reasonError: String? = null,
    val loading: Boolean = false,
    val generalError: String? = null,
    val saved: Boolean = false
) {
    val selected: EquipmentEntity? get() = equipment.firstOrNull { it.id == equipmentId }
}

class AdminBlockFormViewModel(
    private val repo: EquipmentRepository,
    private val actor: UserEntity,
    preselectedId: Long
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminBlockFormUiState(equipmentId = preselectedId))
    val ui: StateFlow<AdminBlockFormUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { list ->
                _ui.update { it.copy(equipment = list.filter { e -> e.status != EquipmentStatus.RETIRADO }) }
            }
        }
    }

    fun onEquipmentChange(e: EquipmentEntity) =
        _ui.update { it.copy(equipmentId = e.id, equipmentError = null, generalError = null) }

    fun onStartDayChange(day: Long) = _ui.update { s ->
        val end = maxOf(s.endDay, day)
        s.copy(startDay = day, endDay = end, dateError = Validators.blockPeriod(day, end, DateUtils.today()), generalError = null)
    }

    fun onEndDayChange(day: Long) = _ui.update { s ->
        s.copy(endDay = day, dateError = Validators.blockPeriod(s.startDay, day, DateUtils.today()), generalError = null)
    }

    fun onStartBlockChange(n: Int) = _ui.update { s ->
        s.copy(startBlock = n, blockError = Validators.availabilityRange(n, s.endBlock), generalError = null)
    }

    fun onEndBlockChange(n: Int) = _ui.update { s ->
        s.copy(endBlock = n, blockError = Validators.availabilityRange(s.startBlock, n), generalError = null)
    }

    fun onReasonChange(v: String) =
        _ui.update { it.copy(reason = v, reasonError = Validators.blockReason(v), generalError = null) }

    fun submit() {
        val s = _ui.value
        val equipmentError = if (s.equipmentId == 0L || s.selected == null) "Selecciona un equipo" else null
        val dateError = Validators.blockPeriod(s.startDay, s.endDay, DateUtils.today())
        val blockError = Validators.availabilityRange(s.startBlock, s.endBlock)
        val reasonError = Validators.blockReason(s.reason)
        if (listOf(equipmentError, dateError, blockError, reasonError).any { it != null }) {
            _ui.update {
                it.copy(equipmentError = equipmentError, dateError = dateError, blockError = blockError, reasonError = reasonError)
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, generalError = null) }
            val r = repo.createBlock(actor.id, s.equipmentId, s.startDay, s.endDay, s.startBlock, s.endBlock, s.reason)
            when (r) {
                is Outcome.Success -> _ui.update { it.copy(loading = false, saved = true) }
                is Outcome.Failure -> _ui.update { it.copy(loading = false, generalError = r.message) }
            }
        }
    }
}
