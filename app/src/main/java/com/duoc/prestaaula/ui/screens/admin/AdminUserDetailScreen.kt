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

import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.TimeBlocks
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminUserDetailViewModel

@Composable
fun AdminUserDetailScreen(actor: UserEntity, userId: Long, onBack: () -> Unit) {
    val vm = appViewModel { AdminUserDetailViewModel(it.adminRepository, actor, userId) }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var suspendDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ui.feedback) {
        ui.feedback?.let {
            snackbar.showSnackbar(it.text)
            vm.clearFeedback()
        }
    }

    Scaffold(
        topBar = { AppTopBar(ui.user?.name ?: "Usuario", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val u = ui.user
        if (u == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (ui.loaded) Text("Usuario no encontrado") else CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (u.active) StatusChip("Activa", StatusColors.Success) else StatusChip("Suspendida", StatusColors.Danger)
                        }
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Icons.Default.Email, "Correo", u.email)
                        InfoRow(Icons.Default.Person, "Rol", u.role.label)
                        InfoRow(Icons.Default.Tag, "Identificador", u.id.toString())
                    }
                }
            }

            item { SectionTitle("Actividad de la cuenta") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow(Icons.Default.EventAvailable, "Reservas realizadas", ui.stats.total.toString())
                        InfoRow(Icons.Default.Schedule, "Reservas activas", ui.stats.active.toString())
                        InfoRow(Icons.Default.AssignmentReturn, "Devueltas", ui.stats.returned.toString())
                        InfoRow(Icons.Default.Cancel, "Canceladas", ui.stats.cancelled.toString())
                        InfoRow(Icons.Default.Warning, "Devoluciones con incidencia", ui.stats.incidents.toString())
                        InfoRow(
                            Icons.Default.Event,
                            "Última reserva",
                            ui.stats.lastDay?.let { DateUtils.format(it) } ?: "Sin reservas"
                        )
                    }
                }
            }

            item { SectionTitle("Estado de la cuenta") }
            item {
                val locked = ui.lockedReason
                if (locked != null) {
                    Text(locked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (u.active) {
                    OutlinedButton(onClick = { suspendDialog = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Suspender cuenta")
                    }
                } else {
                    LoadingButton("Reactivar cuenta", false, vm::reactivate, icon = Icons.Default.LockOpen)
                }
            }

            item { SectionTitle("Historial de la cuenta") }
            if (ui.changes.isEmpty()) {
                item { Text("Sin suspensiones ni reactivaciones.", style = MaterialTheme.typography.bodySmall) }
            }
            items(ui.changes, key = { "c${it.id}" }) { c ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.action.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${c.actorName} · ${DateUtils.formatMillis(c.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        c.reason?.let { Text("Motivo: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            item { SectionTitle("Historial de reservas") }
            if (ui.loans.isEmpty()) {
                item { EmptyState(Icons.Default.History, "Sin reservas", "Este usuario aún no registra reservas.") }
            }
            items(ui.loans, key = { "l${it.loan.id}" }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.equipmentName} (${d.equipmentCode})", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${DateUtils.format(d.loan.dateEpochDay)} · ${TimeBlocks.label(d.loan.block)} · ${d.loan.course}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(d.loan.status.label, d.loan.status.color())
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (suspendDialog) {
        ReasonDialog(
            title = "Suspender cuenta",
            message = "El usuario no podrá iniciar sesión y, si tiene la sesión abierta, se cerrará. Sus reservas se conservan.",
            confirmLabel = "Suspender",
            onConfirm = { reason ->
                vm.suspendAccount(reason)
                suspendDialog = false
            },
            onDismiss = { suspendDialog = false }
        )
    }
}
