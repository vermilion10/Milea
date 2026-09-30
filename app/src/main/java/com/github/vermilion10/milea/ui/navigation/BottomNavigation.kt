package com.github.vermilion10.milea.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class TopLevel(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Home(Screen.Dashboard.route, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Trips(Screen.Trips.route, "Trips", Icons.Outlined.Route, Icons.Filled.Route),
    Fuel(Screen.Fuel.route, "Fuel", Icons.Outlined.LocalGasStation, Icons.Filled.LocalGasStation),
    Expenses(Screen.Expenses.route, "Expenses", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    Stats(Screen.Stats.route, "Stats", Icons.Outlined.BarChart, Icons.Filled.BarChart)
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination
    // Detail screens (trip, settings, vehicles) hide the bar.
    if (destination != null && TopLevel.entries.none { it.route == destination.route }) return

    NavigationBar {
        TopLevel.entries.forEach { item ->
            val selected = destination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.title) },
                selected = selected,
                onClick = { navController.navigateToTab(item.route) }
            )
        }
    }
}

// Shared navigation behavior for any top-level tab destination. Pops back to
// the graph's start destination first and reuses a saved instance of the
// target instead of stacking a fresh one, so the back stack can't accumulate
// duplicate entries and Home always cleanly returns.
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        graph.startDestinationRoute?.let { start ->
            popUpTo(start) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}
