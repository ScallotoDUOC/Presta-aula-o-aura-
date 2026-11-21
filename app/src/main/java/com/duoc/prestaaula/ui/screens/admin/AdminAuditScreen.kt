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

import com.duoc.prestaaula.data.local.entity.AuditAction
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.util.DateUtils
import com.duoc.prestaaula.viewmodel.AdminAuditViewModel
import com.duoc.prestaaula.viewmodel.AuditFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminAuditViewModel(it.adminRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Historial de acciones", onBack = onBack) }) { padding ->
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
                    label = { Text("Buscar por responsable, elemento o motivo") },
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
                    items(AuditFilter.entries.toList()) { f ->
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
                    "${ui.entries.size} de ${ui.totalCount} registros · solo lectura",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ui.entries.isEmpty()) {
                item { EmptyState(Icons.Default.History, "Sin registros", "Aún no hay acciones registradas.") }
            }
            items(ui.entries, key = { it.id }) { e ->
                AuditCard(e, Modifier.animateItem())
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AuditCard(e: AuditLogEntity, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.action.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("${e.targetType}: ${e.targetLabel}", style = MaterialTheme.typography.bodyMedium)
            if (e.previousValue != null || e.newValue != null) {
                val change = when {
                    e.previousValue != null && e.newValue != null -> "${e.previousValue} → ${e.newValue}"
                    e.newValue != null -> e.newValue
                    else -> e.previousValue ?: ""
                }
                Text(change, style = MaterialTheme.typography.bodySmall)
            }
            e.reason?.let {
                val label = if (e.action == AuditAction.REPORTE_EXPORTADO) "Filtros" else "Motivo"
                Text("$label: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${e.actorName} (${e.actorRole}) · ${DateUtils.formatMillis(e.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
