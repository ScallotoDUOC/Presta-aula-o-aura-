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
import com.duoc.prestaaula.data.local.entity.EquipmentStatus
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.validation.Validators
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.viewmodel.AdminEquipmentRow
import com.duoc.prestaaula.viewmodel.AdminEquipmentViewModel
import com.duoc.prestaaula.viewmodel.EquipmentFilter

/** Acción que necesita un motivo escrito por el administrador antes de ejecutarse. */
private data class ReasonRequest(val equipment: EquipmentEntity, val isBlock: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEquipmentScreen(
    user: UserEntity,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSchedule: (Long) -> Unit
) {
    val vm = appViewModel { AdminEquipmentViewModel(it.equipmentRepository, it.loanRepository, user) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var reasonRequest by remember { mutableStateOf<ReasonRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<EquipmentEntity?>(null) }

    LaunchedEffect(ui.feedback) {
        ui.feedback?.let {
            snackbar.showSnackbar(it.text)
            vm.clearFeedback()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Gestión de equipos") },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo equipo") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar por tipo, marca o código") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EquipmentFilter.entries.toList()) { f ->
                        FilterChip(
                            selected = ui.filter == f,
                            onClick = { vm.onFilterChange(f) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            item {
                Text(
                    "${ui.rows.size} de ${ui.totalCount} equipos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ui.rows.isEmpty()) {
                item { EmptyState(Icons.Default.SearchOff, "Sin resultados", "No hay equipos con ese filtro.") }
            }
            items(ui.rows, key = { it.equipment.id }) { row ->
                AdminEquipmentCard(
                    row = row,
                    onOpen = { onOpen(row.equipment.id) },
                    onEdit = { onEdit(row.equipment.id) },
                    onSchedule = { onSchedule(row.equipment.id) },
                    onBlock = { reasonRequest = ReasonRequest(row.equipment, isBlock = true) },
                    onUnblock = { vm.unblock(row.equipment.id) },
                    onRetire = { reasonRequest = ReasonRequest(row.equipment, isBlock = false) },
                    onReactivate = { vm.reactivate(row.equipment.id) },
                    onDelete = { deleteTarget = row.equipment },
                    modifier = Modifier.animateItem()
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    reasonRequest?.let { req ->
        ReasonDialog(
            title = if (req.isBlock) "Bloquear equipo" else "Retirar del catálogo",
            message = if (req.isBlock) {
                "${req.equipment.displayName} (${req.equipment.internalCode}) no admitirá nuevas reservas hasta que lo desbloquees."
            } else {
                "${req.equipment.displayName} (${req.equipment.internalCode}) dejará de estar disponible. Su historial se conserva."
            },
            confirmLabel = if (req.isBlock) "Bloquear" else "Retirar",
            onConfirm = { reason ->
                if (req.isBlock) vm.block(req.equipment.id, reason) else vm.retire(req.equipment.id, reason)
                reasonRequest = null
            },
            onDismiss = { reasonRequest = null }
        )
    }

    deleteTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar equipo?") },
            text = {
                Text(
                    "Se eliminará ${e.displayName} (${e.internalCode}) del inventario. " +
                        "Solo es posible si nunca tuvo préstamos; si los tiene, usa «Retirar del catálogo»."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(e.id)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminEquipmentCard(
    row: AdminEquipmentRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSchedule: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onRetire: () -> Unit,
    onReactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val e = row.equipment
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(equipmentIcon(e.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Código ${e.internalCode} · reservado ${row.totalReservations} ${if (row.totalReservations == 1) "vez" else "veces"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(e.status.label, e.status.color())
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más acciones")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ver detalle e historial") },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = { menu = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() }
                    )
                    if (e.status != EquipmentStatus.RETIRADO) {
                        DropdownMenuItem(
                            text = { Text("Programar bloqueo") },
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                            onClick = { menu = false; onSchedule() }
                        )
                    }
                    if (e.status == EquipmentStatus.DISPONIBLE || e.status == EquipmentStatus.MANTENCION) {
                        DropdownMenuItem(
                            text = { Text("Bloquear equipo") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            onClick = { menu = false; onBlock() }
                        )
                    }
                    if (e.status == EquipmentStatus.BLOQUEADO) {
                        DropdownMenuItem(
                            text = { Text("Desbloquear") },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                            onClick = { menu = false; onUnblock() }
                        )
                    }
                    if (e.status == EquipmentStatus.DISPONIBLE || e.status == EquipmentStatus.MANTENCION || e.status == EquipmentStatus.BLOQUEADO) {
                        DropdownMenuItem(
                            text = { Text("Retirar del catálogo") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                            onClick = { menu = false; onRetire() }
                        )
                    }
                    if (e.status == EquipmentStatus.RETIRADO) {
                        DropdownMenuItem(
                            text = { Text("Reactivar") },
                            leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                            onClick = { menu = false; onReactivate() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
