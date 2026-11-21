package com.duoc.prestaaula.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.TimeBlock
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminBlockFormViewModel

@Composable
fun AdminBlockFormScreen(user: UserEntity, equipmentId: Long, onBack: () -> Unit) {
    val vm = appViewModel { AdminBlockFormViewModel(it.equipmentRepository, user, equipmentId) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.saved) {
        if (ui.saved) onBack()
    }

    Scaffold(topBar = { AppTopBar("Programar bloqueo", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Mientras dure el bloqueo, el equipo no admitirá nuevas reservas en las fechas y bloques indicados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppDropdown(
                label = "Equipo",
                selectedText = ui.selected?.let { "${it.displayName} (${it.internalCode})" } ?: "",
                options = ui.equipment,
                optionLabel = { e: EquipmentEntity -> "${e.displayName} (${e.internalCode})" },
                onSelected = vm::onEquipmentChange,
                leadingIcon = Icons.Default.Devices,
                error = ui.equipmentError
            )
            DatePickerField(
                label = "Desde",
                epochDay = ui.startDay,
                onDateSelected = vm::onStartDayChange,
                error = ui.dateError,
                minEpochDay = DateUtils.today()
            )
            DatePickerField(
                label = "Hasta",
                epochDay = ui.endDay,
                onDateSelected = vm::onEndDayChange,
                minEpochDay = ui.startDay
            )
            AppDropdown(
                label = "Desde el bloque",
                selectedText = TimeBlocks.label(ui.startBlock),
                options = TimeBlocks.all,
                optionLabel = { b: TimeBlock -> b.label },
                onSelected = { vm.onStartBlockChange(it.number) },
                leadingIcon = Icons.Default.Schedule
            )
            AppDropdown(
                label = "Hasta el bloque",
                selectedText = TimeBlocks.label(ui.endBlock),
                options = TimeBlocks.all,
                optionLabel = { b: TimeBlock -> b.label },
                onSelected = { vm.onEndBlockChange(it.number) },
                leadingIcon = Icons.Default.Schedule,
                error = ui.blockError
            )
            ValidatedTextField(
                value = ui.reason,
                onValueChange = vm::onReasonChange,
                label = "Motivo del bloqueo",
                leadingIcon = Icons.Default.Notes,
                error = ui.reasonError,
                singleLine = false,
                minLines = 2,
                imeAction = ImeAction.Done
            )
            FeedbackBanner(ui.generalError)
            LoadingButton(
                text = "Guardar bloqueo",
                loading = ui.loading,
                onClick = vm::submit,
                icon = Icons.Default.Event
            )
        }
    }
}
