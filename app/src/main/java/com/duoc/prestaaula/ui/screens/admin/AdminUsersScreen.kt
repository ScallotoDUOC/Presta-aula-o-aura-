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

import com.duoc.prestaaula.ui.components.*
import com.duoc.prestaaula.ui.theme.StatusColors
import com.duoc.prestaaula.viewmodel.AdminUsersViewModel
import com.duoc.prestaaula.viewmodel.UserFilter
import com.duoc.prestaaula.viewmodel.UserRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { AdminUsersViewModel(it.adminRepository, it.loanRepository) }
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Usuarios", onBack = onBack) }) { padding ->
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
                    label = { Text("Buscar por nombre, correo o identificador") },
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
                    items(UserFilter.entries.toList()) { f ->
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
                    "${ui.rows.size} de ${ui.totalCount} usuarios",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ui.rows.isEmpty()) {
                item { EmptyState(Icons.Default.SearchOff, "Sin resultados", "No hay usuarios que coincidan.") }
            }
            items(ui.rows, key = { it.user.id }) { row ->
                UserCard(row, onClick = { onOpen(row.user.id) }, modifier = Modifier.animateItem())
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserCard(row: UserRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val u = row.user
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        u.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(u.name, fontWeight = FontWeight.SemiBold)
                Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${u.role.label} · ${row.totalLoans} reservas (${row.activeLoans} activas) · ID ${u.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (u.active) StatusChip("Activa", StatusColors.Success) else StatusChip("Suspendida", StatusColors.Danger)
        }
    }
}
