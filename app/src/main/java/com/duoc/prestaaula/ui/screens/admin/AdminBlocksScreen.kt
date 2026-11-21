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

import com.duoc.prestaaula.data.local.entity.BlockDetail
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminBlocksViewModel
import com.duoc.prestaaula.viewmodel.BlockFilter
import com.duoc.prestaaula.viewmodel.ManualBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBlocksScreen(user: UserEntity, onBack: () -> Unit, onSchedule: () -> Unit) {
    val vm = appViewModel { AdminBlocksViewModel(it.equipmentRepository, it.adminRepository, user) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val today = DateUtils.today()

    LaunchedEffect(ui.feedback) {
        ui.feedback?.let {
            snackbar.showSnackbar(it.text)
            vm.clearFeedback()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Bloqueos de equipos", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSchedule,
                icon = { Icon(Icons.Default.Event, contentDescription = null) },
                text = { Text("Programar bloqueo") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("Bloqueados manualmente") }
            if (ui.manual.isEmpty()) {
                item {
                    Text(
                        "No hay equipos bloqueados manualmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ui.manual, key = { "m${it.equipment.id}" }) { m ->
                ManualBlockCard(m, onUnblock = { vm.unblock(m.equipment.id) })
            }

            item { SectionTitle("Bloqueos por fecha y bloque") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BlockFilter.entries.toList()) { f ->
                        FilterChip(
                            selected = ui.filter == f,
                            onClick = { vm.onFilterChange(f) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            if (ui.blocks.isEmpty()) {
                item { EmptyState(Icons.Default.EventBusy, "Sin bloqueos", "No hay bloqueos en esta categoría.") }
            }
            items(ui.blocks, key = { "b${it.block.id}" }) { d ->
                BlockCard(d, today, onLift = { vm.lift(d.block.id) }, modifier = Modifier.animateItem())
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ManualBlockCard(m: ManualBlock, onUnblock: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${m.equipment.displayName} (${m.equipment.internalCode})",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip("Bloqueado", StatusColors.Danger)
            }
            Text("Motivo: ${m.reason ?: "sin registro"}", style = MaterialTheme.typography.bodySmall)
            val who = m.byName ?: "sin registro"
            val whenText = m.at?.let { DateUtils.formatMillis(it) } ?: "—"
            Text(
                "Bloqueado por $who · $whenText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onUnblock) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Desbloquear")
            }
        }
    }
}

@Composable
private fun BlockCard(d: BlockDetail, today: Long, onLift: () -> Unit, modifier: Modifier = Modifier) {
    val b = d.block
    val (label, color) = when {
        !b.active -> "Levantado" to StatusColors.Neutral
        b.endDay < today -> "Finalizado" to StatusColors.Neutral
        b.startDay > today -> "Programado" to StatusColors.Info
        else -> "Vigente" to StatusColors.Danger
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.equipmentName} (${d.equipmentCode})",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(label, color)
            }
            Text(
                "${DateUtils.format(b.startDay)} al ${DateUtils.format(b.endDay)} · bloques ${b.startBlock} a ${b.endBlock}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Motivo: ${b.reason}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Creado por ${b.createdByName} · ${DateUtils.formatMillis(b.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!b.active && b.liftedAt != null) {
                Text(
                    "Levantado por ${b.liftedByName ?: "—"} · ${DateUtils.formatMillis(b.liftedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (b.active && b.endDay >= today) {
                OutlinedButton(onClick = onLift) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Levantar bloqueo")
                }
            }
        }
    }
}
