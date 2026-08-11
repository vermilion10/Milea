package com.github.vermilion10.milea.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.vermilion10.milea.ui.screens.dashboard.DashboardScreen
import com.github.vermilion10.milea.ui.screens.expense.ExpenseListScreen
import com.github.vermilion10.milea.ui.screens.fillup.FillupListScreen
import com.github.vermilion10.milea.ui.screens.settings.SettingsScreen
import com.github.vermilion10.milea.ui.screens.stats.StatsScreen
import com.github.vermilion10.milea.ui.screens.trip.TripListScreen
import com.github.vermilion10.milea.ui.screens.tripdetail.TripDetailScreen
import com.github.vermilion10.milea.ui.screens.vehicle.VehicleListScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object VehicleList : Screen("vehicles")
    object VehicleDetail : Screen("vehicle/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "vehicle/$vehicleId"
    }
    object TripList : Screen("trips/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "trips/$vehicleId"
    }
    object TripDetail : Screen("trip/{tripId}") {
        fun createRoute(tripId: Long) = "trip/$tripId"
    }
    object FillupList : Screen("fillups/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "fillups/$vehicleId"
    }
    object FillupDetail : Screen("fillup/{fillupId}") {
        fun createRoute(fillupId: Long) = "fillup/$fillupId"
    }
    object ExpenseList : Screen("expenses/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "expenses/$vehicleId"
    }
    object Stats : Screen("stats/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "stats/$vehicleId"
    }
    object Settings : Screen("settings")
}

@Composable
fun MileaNavigation(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    // These three match bottom-bar tab destinations, so they use the
                    // same popUpTo/launchSingleTop/restoreState behavior as the bottom
                    // bar. Otherwise a Quick Action tap pushes a duplicate instance of
                    // the destination, and tapping "Dashboard" in the bottom bar
                    // afterwards no longer reliably returns you there.
                    onOpenTrips = { navController.navigateToTab("trips_default") },
                    onOpenFillups = { navController.navigateToTab("fillups_default") },
                    onOpenExpenses = { navController.navigateToTab("expenses_default") },
                    onOpenStats = { navController.navigate(Screen.Stats.createRoute(0)) },
                    onOpenVehicles = { navController.navigate(Screen.VehicleList.route) }
                )
            }
            composable(Screen.VehicleList.route) {
                VehicleListScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.VehicleDetail.route) { backStackEntry ->
                val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull()
                // VehicleDetailScreen(vehicleId, navController)
            }
            composable("trips_default") {
                TripListScreen(
                    onTripClick = { tripId ->
                        navController.navigate(Screen.TripDetail.createRoute(tripId))
                    }
                )
            }
            composable(Screen.TripList.route) { backStackEntry ->
                // val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull()
                TripListScreen(
                    onTripClick = { tripId ->
                        navController.navigate(Screen.TripDetail.createRoute(tripId))
                    }
                )
            }
            composable(Screen.TripDetail.route) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId")?.toLongOrNull() ?: 0L
                TripDetailScreen(tripId = tripId, onBack = { navController.popBackStack() })
            }
            composable("fillups_default") {
                FillupListScreen()
            }
            composable(Screen.FillupList.route) { backStackEntry ->
                // val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull()
                FillupListScreen()
            }
            composable(Screen.FillupDetail.route) { backStackEntry ->
                val fillupId = backStackEntry.arguments?.getString("fillupId")?.toLongOrNull()
                // FillupDetailScreen(fillupId, navController)
            }
            composable("expenses_default") {
                ExpenseListScreen()
            }
            composable(Screen.ExpenseList.route) { backStackEntry ->
                // val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toLongOrNull()
                ExpenseListScreen()
            }
            composable(Screen.Stats.route) { backStackEntry ->
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

