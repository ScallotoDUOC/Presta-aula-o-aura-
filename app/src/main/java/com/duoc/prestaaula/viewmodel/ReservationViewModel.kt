package com.duoc.prestaaula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.repository.EquipmentRepository
import com.duoc.prestaaula.data.repository.LoanRepository
import com.duoc.prestaaula.domain.model.Outcome
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.domain.validation.Validators
import com.duoc.prestaaula.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReservationUiState(
    val equipment: EquipmentEntity? = null,
    val date: Long = DateUtils.today(),
    val block: Int = 1,
    val course: String = "",
    val dateError: String? = null,
    val blockError: String? = null,
    val blockOk: Boolean = false,
    val courseError: String? = null,
    val loading: Boolean = false,
    val generalError: String? = null,
    val completedMessage: String? = null
)

class ReservationViewModel(
    private val equipmentRepo: EquipmentRepository,
    private val loanRepo: LoanRepository,
    private val equipmentId: Long,
    private val teacherId: Long
) : ViewModel() {
    private val _ui = MutableStateFlow(ReservationUiState())
    val ui: StateFlow<ReservationUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(equipment = equipmentRepo.getById(equipmentId)) }
            refreshAvailability()
        }
    }

    fun onDateChange(epochDay: Long) {
        _ui.update { it.copy(date = epochDay, dateError = Validators.reservationDate(epochDay, DateUtils.today())) }
        viewModelScope.launch { refreshAvailability() }
    }

    fun onBlockChange(number: Int) {
        _ui.update { it.copy(block = number) }
        viewModelScope.launch { refreshAvailability() }
    }

    /** Se llama al cerrar el diálogo de confirmación: deja el formulario listo para otra reserva. */
    fun onReservationConsumed() {
        _ui.update { ReservationUiState(equipment = it.equipment) }
        viewModelScope.launch { refreshAvailability() }
    }

    fun onCourseChange(v: String) = _ui.update { it.copy(course = v, courseError = Validators.course(v), generalError = null) }

    private suspend fun refreshAvailability() {
        val s = _ui.value
        val message = loanRepo.checkSlot(equipmentId, teacherId, s.date, s.block)
        _ui.update { it.copy(blockError = message, blockOk = message == null) }
    }

    fun submit() {
        val s = _ui.value
        val dateError = Validators.reservationDate(s.date, DateUtils.today())
        val courseError = Validators.course(s.course)
        if (dateError != null || courseError != null || s.blockError != null) {
            _ui.update { it.copy(dateError = dateError, courseError = courseError) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, generalError = null) }
            when (val r = loanRepo.reserve(equipmentId, teacherId, s.course, s.date, s.block)) {
                is Outcome.Success -> _ui.update {
                    it.copy(
                        loading = false,
                        completedMessage = "${s.equipment?.displayName ?: "Equipo"} (${s.equipment?.internalCode ?: ""}) " +
                            "reservado para el ${DateUtils.format(s.date)}, ${TimeBlocks.label(s.block)}."
                    )
                }
                is Outcome.Failure -> _ui.update { it.copy(loading = false, generalError = r.message) }
            }
        }
    }
}
