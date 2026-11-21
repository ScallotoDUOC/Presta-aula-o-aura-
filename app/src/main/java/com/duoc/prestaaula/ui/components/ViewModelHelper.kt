package com.duoc.prestaaula.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.duoc.prestaaula.PrestaAulaApp
import com.duoc.prestaaula.di.AppContainer

/**
 * Crea un ViewModel inyectándole el contenedor de dependencias de la aplicación.
 * [key] es opcional: permite que pantallas parametrizadas (p. ej. una reserva por equipo)
 * no compartan la misma instancia de ViewModel.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = (LocalContext.current.applicationContext as PrestaAulaApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
