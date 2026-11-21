package com.duoc.prestaaula.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.duoc.prestaaula.domain.validation.Validators
import com.duoc.prestaaula.ui.components.ValidatedTextField

/** Diálogo que exige escribir un motivo antes de ejecutar una acción administrativa. */
@Composable
fun ReasonDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                ValidatedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        error = Validators.blockReason(it)
                    },
                    label = "Motivo",
                    leadingIcon = Icons.Default.Notes,
                    error = error,
                    singleLine = false,
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validation = Validators.blockReason(reason)
                if (validation == null) onConfirm(reason.trim()) else error = validation
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
