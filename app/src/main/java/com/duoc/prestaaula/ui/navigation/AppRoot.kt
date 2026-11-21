package com.duoc.prestaaula.ui.navigation

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.duoc.prestaaula.data.local.entity.Role
import com.duoc.prestaaula.data.local.entity.UserEntity
import com.duoc.prestaaula.domain.model.Permissions
import com.duoc.prestaaula.nativeaccess.NotificationHelper
import com.duoc.prestaaula.ui.components.appViewModel
import com.duoc.prestaaula.ui.screens.admin.AdminAuditScreen
import com.duoc.prestaaula.ui.screens.admin.AdminBlockFormScreen
import com.duoc.prestaaula.ui.screens.admin.AdminBlocksScreen
import com.duoc.prestaaula.ui.screens.admin.AdminDashboardScreen
import com.duoc.prestaaula.ui.screens.admin.AdminEquipmentScreen
import com.duoc.prestaaula.ui.screens.admin.AdminReportsScreen
import com.duoc.prestaaula.ui.screens.admin.AdminUserDetailScreen
import com.duoc.prestaaula.ui.screens.admin.AdminUsersScreen
import com.duoc.prestaaula.ui.screens.auth.LoginScreen
import com.duoc.prestaaula.ui.screens.auth.RegisterScreen
import com.duoc.prestaaula.ui.screens.equipment.EquipmentDetailScreen
import com.duoc.prestaaula.ui.screens.equipment.EquipmentFormScreen
import com.duoc.prestaaula.ui.screens.equipment.EquipmentListScreen
import com.duoc.prestaaula.ui.screens.loans.LoanDetailScreen
import com.duoc.prestaaula.ui.screens.loans.LoansScreen
import com.duoc.prestaaula.ui.screens.loans.ReturnScreen
import com.duoc.prestaaula.ui.screens.profile.ProfileScreen
import com.duoc.prestaaula.ui.screens.reports.ReportsScreen
import com.duoc.prestaaula.ui.screens.reservation.ReservationScreen
import com.duoc.prestaaula.ui.screens.utp.UtpDashboardScreen
import com.duoc.prestaaula.ui.screens.utp.UtpIncidentsScreen
import com.duoc.prestaaula.ui.screens.utp.UtpOccupancyScreen
import com.duoc.prestaaula.ui.screens.utp.UtpReportsScreen
import com.duoc.prestaaula.viewmodel.SessionState
import com.duoc.prestaaula.viewmodel.SessionViewModel

@Composable
fun AppRoot() {
    val sessionVm = appViewModel { SessionViewModel(it.userRepository, it.sessionManager) }
    val state by sessionVm.state.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "root"
    ) { s ->
        when (s) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            SessionState.LoggedOut -> AuthNavHost()
            is SessionState.LoggedIn -> MainScaffold(user = s.user, onLogout = sessionVm::logout)
        }
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "login",
        enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) }
    ) {
        composable("login") { LoginScreen(onRegister = { nav.navigate("register") }) }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

/** Barrera de seguridad en la navegación: las pantallas administrativas solo se muestran al administrador. */
@Composable
private fun AdminOnly(user: UserEntity, content: @Composable () -> Unit) {
    if (user.role == Role.JEFE_ADMIN) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tienes permisos para ver esta sección")
        }
    }
}

/** Barrera de seguridad: el perfil de uso (solo lectura) es para el Jefe de UTP y el administrador. */
@Composable
private fun UsageOnly(user: UserEntity, content: @Composable () -> Unit) {
    if (Permissions.canViewUsage(user.role)) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tienes permisos para ver esta sección")
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private fun bottomItemsFor(role: Role): List<BottomItem> {
    val equipment = BottomItem(Routes.EQUIPMENT, "Equipos", Icons.Default.Inventory2)
    val profile = BottomItem(Routes.PROFILE, "Perfil", Icons.Default.AccountCircle)
    return when (role) {
        Role.DOCENTE -> listOf(
            equipment,
            BottomItem(Routes.LOANS, "Mis reservas", Icons.Default.EventAvailable),
            profile
        )
        Role.JEFE_ADMIN -> listOf(
            BottomItem(Routes.ADMIN, "Panel", Icons.Default.Dashboard),
            equipment,
            BottomItem(Routes.LOANS, "Préstamos", Icons.AutoMirrored.Filled.Assignment),
            BottomItem(Routes.REPORTS, "Reportes", Icons.Default.BarChart),
            profile
        )
        Role.JEFE_UTP -> listOf(
            BottomItem(Routes.UTP, "Panel", Icons.Default.Dashboard),
            BottomItem(Routes.UTP_OCCUPANCY, "Ocupación", Icons.Default.CalendarMonth),
            BottomItem(Routes.UTP_INCIDENTS, "Incidencias", Icons.Default.ReportProblem),
            BottomItem(Routes.REPORTS, "Reportes", Icons.Default.BarChart),
            profile
        )
        else -> listOf(
            equipment,
            BottomItem(Routes.LOANS, "Préstamos", Icons.AutoMirrored.Filled.Assignment),
            BottomItem(Routes.REPORTS, "Reportes", Icons.Default.BarChart),
            profile
        )
    }
}

@Composable
private fun MainScaffold(user: UserEntity, onLogout: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val items = remember(user.role) { bottomItemsFor(user.role) }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = items.any { it.route == currentRoute }

    // Recurso nativo: permiso de notificaciones (Android 13+), se solicita al ingresar.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = when (user.role) {
                Role.JEFE_ADMIN -> Routes.ADMIN
                Role.JEFE_UTP -> Routes.UTP
                else -> Routes.EQUIPMENT
            },
            modifier = Modifier
                .padding(inner)
                .consumeWindowInsets(inner),
            enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 6 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(250)) { it / 6 } }
        ) {
            composable(Routes.EQUIPMENT) {
                if (user.role == Role.JEFE_ADMIN) {
                    AdminEquipmentScreen(
                        user = user,
                        onOpen = { nav.navigate(Routes.equipmentDetail(it)) },
                        onAdd = { nav.navigate(Routes.equipmentForm(0L)) },
                        onEdit = { nav.navigate(Routes.equipmentForm(it)) },
                        onSchedule = { nav.navigate(Routes.adminBlockForm(it)) }
                    )
                } else {
                    EquipmentListScreen(
                        user = user,
                        onOpen = { nav.navigate(Routes.equipmentDetail(it)) },
                        onAdd = { nav.navigate(Routes.equipmentForm(0L)) }
                    )
                }
            }
            composable(Routes.LOANS) {
                LoansScreen(user = user, onOpen = { nav.navigate(Routes.loanDetail(it)) })
            }
            composable(Routes.REPORTS) {
                when (user.role) {
                    Role.JEFE_ADMIN -> AdminReportsScreen(user = user)
                    Role.JEFE_UTP -> UtpReportsScreen(user = user)
                    else -> ReportsScreen()
                }
            }
            composable(Routes.UTP) { UsageOnly(user) { UtpDashboardScreen() } }
            composable(Routes.UTP_OCCUPANCY) { UsageOnly(user) { UtpOccupancyScreen() } }
            composable(Routes.UTP_INCIDENTS) { UsageOnly(user) { UtpIncidentsScreen() } }
            composable(Routes.ADMIN_AUDIT) {
                AdminOnly(user) { AdminAuditScreen(onBack = { nav.popBackStack() }) }
            }
            composable(Routes.PROFILE) { ProfileScreen(user = user, onLogout = onLogout) }

            composable(Routes.ADMIN) {
                AdminOnly(user) { AdminDashboardScreen(
                        onOpenBlocks = { nav.navigate(Routes.ADMIN_BLOCKS) },
                        onOpenUsers = { nav.navigate(Routes.ADMIN_USERS) },
                        onOpenAudit = { nav.navigate(Routes.ADMIN_AUDIT) }
                    ) }
            }
            composable(Routes.ADMIN_USERS) {
                AdminOnly(user) {
                    AdminUsersScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(Routes.adminUser(it)) }
                    )
                }
            }
            composable(
                Routes.ADMIN_USER_DETAIL,
                arguments = listOf(navArgument("userId") { type = NavType.LongType })
            ) { entry ->
                AdminOnly(user) {
                    AdminUserDetailScreen(
                        actor = user,
                        userId = entry.arguments?.getLong("userId") ?: 0L,
                        onBack = { nav.popBackStack() }
                    )
                }
            }
            composable(Routes.ADMIN_BLOCKS) {
                AdminOnly(user) {
                    AdminBlocksScreen(
                        user = user,
                        onBack = { nav.popBackStack() },
                        onSchedule = { nav.navigate(Routes.adminBlockForm(0L)) }
                    )
                }
            }
            composable(
                Routes.ADMIN_BLOCK_FORM,
                arguments = listOf(navArgument("equipmentId") { type = NavType.LongType })
            ) { entry ->
                AdminOnly(user) {
                    AdminBlockFormScreen(
                        user = user,
                        equipmentId = entry.arguments?.getLong("equipmentId") ?: 0L,
                        onBack = { nav.popBackStack() }
                    )
                }
            }

            composable(
                Routes.EQUIPMENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                EquipmentDetailScreen(
                    user = user,
                    equipmentId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onReserve = { nav.navigate(Routes.reserve(it)) },
                    onEdit = { nav.navigate(Routes.equipmentForm(it)) }
                )
            }
            composable(
                Routes.EQUIPMENT_FORM,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                EquipmentFormScreen(
                    user = user,
                    equipmentId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.RESERVE,
                arguments = listOf(navArgument("equipmentId") { type = NavType.LongType })
            ) { entry ->
                ReservationScreen(
                    user = user,
                    equipmentId = entry.arguments?.getLong("equipmentId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onDone = {
                        // Al terminar la reserva, la pantalla se destruye (sin guardar su estado)
                        // para que la próxima reserva empiece con el formulario limpio.
                        nav.navigate(Routes.LOANS) {
                            popUpTo(nav.graph.findStartDestination().id) {
                                inclusive = false
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                )
            }
            composable(
                Routes.LOAN_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                LoanDetailScreen(
                    user = user,
                    loanId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onReturn = { nav.navigate(Routes.returnLoan(it)) }
                )
            }
            composable(
                Routes.RETURN,
                arguments = listOf(navArgument("loanId") { type = NavType.LongType })
            ) { entry ->
                ReturnScreen(
                    actorId = user.id,
                    loanId = entry.arguments?.getLong("loanId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onFinished = { nav.popBackStack(Routes.LOANS, inclusive = false) }
                )
            }
        }
    }
}
