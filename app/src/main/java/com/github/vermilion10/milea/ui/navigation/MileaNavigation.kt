package com.github.vermilion10.milea.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.vermilion10.milea.ui.screens.dashboard.DashboardScreen
import com.github.vermilion10.milea.ui.screens.expense.ExpenseListScreen
import com.github.vermilion10.milea.ui.screens.fillup.FillupListScreen
import com.github.vermilion10.milea.ui.screens.settings.SettingsScreen
import com.github.vermilion10.milea.ui.screens.stats.StatsScreen
import com.github.vermilion10.milea.ui.screens.trip.TripListScreen
import com.github.vermilion10.milea.ui.screens.tripdetail.TripDetailScreen
import com.github.vermilion10.milea.ui.screens.vehicle.VehicleListScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Trips : Screen("trips")
    data object Fuel : Screen("fuel")
    data object Expenses : Screen("expenses")
    data object Stats : Screen("stats")
    data object Vehicles : Screen("vehicles")
    data object Settings : Screen("settings")
    data object TripDetail : Screen("trip/{tripId}") {
        fun createRoute(tripId: Long) = "trip/$tripId"
    }
}

@Composable
fun MileaNavigation(
    navController: NavHostController = rememberNavController()
) {
    val openVehicles = { navController.navigate(Screen.Vehicles.route) { launchSingleTop = true } }
    val openTrip = { id: Long -> navController.navigate(Screen.TripDetail.createRoute(id)) }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController) },
        // Each screen's own Scaffold handles the status bar and IME.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onOpenTrip = openTrip,
                    onOpenTrips = { navController.navigateToTab(Screen.Trips.route) },
                    onOpenFillups = { navController.navigateToTab(Screen.Fuel.route) },
                    onOpenExpenses = { navController.navigateToTab(Screen.Expenses.route) },
                    onOpenStats = { navController.navigateToTab(Screen.Stats.route) },
                    onOpenVehicles = openVehicles,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.Trips.route) {
                TripListScreen(onTripClick = openTrip, onAddVehicle = openVehicles)
            }
            composable(Screen.Fuel.route) {
                FillupListScreen(onAddVehicle = openVehicles)
            }
            composable(Screen.Expenses.route) {
                ExpenseListScreen(onAddVehicle = openVehicles)
            }
            composable(Screen.Stats.route) {
                StatsScreen(onAddVehicle = openVehicles)
            }
            composable(
                Screen.Vehicles.route,
                enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() }
            ) {
                VehicleListScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.Settings.route,
                enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() }
            ) {
                SettingsScreen(onBack = { navController.popBackStack() }, onOpenVehicles = openVehicles)
            }
            composable(
                Screen.TripDetail.route,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() }
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
                TripDetailScreen(tripId = tripId, onBack = { navController.popBackStack() })
            }
        }
    }
}
