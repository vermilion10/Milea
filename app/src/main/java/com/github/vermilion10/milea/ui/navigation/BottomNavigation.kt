package com.github.vermilion10.milea.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Dashboard : BottomNavItem(
        route = Screen.Dashboard.route,
        title = "Dashboard",
        icon = Icons.Default.Dashboard
    )
    
    object Trips : BottomNavItem(
        route = "trips_default",
        title = "Trips",
        icon = Icons.Default.Route
    )
    
    object Fillups : BottomNavItem(
        route = "fillups_default",
        title = "Fuel",
        icon = Icons.Default.LocalGasStation
    )
    
    object Expenses : BottomNavItem(
        route = "expenses_default",
        title = "Expenses",
        icon = Icons.Default.Receipt
    )
    
    object Settings : BottomNavItem(
        route = Screen.Settings.route,
        title = "Settings",
        icon = Icons.Default.Settings
    )
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController
) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Trips,
        BottomNavItem.Fillups,
        BottomNavItem.Expenses,
        BottomNavItem.Settings
    )
    
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = { navController.navigateToTab(item.route) }
            )
        }
    }
}

// Shared navigation behavior for any top-level tab destination (the bottom bar
// items, and any Quick Action shortcut that leads to the same destination as a
// bottom bar tab). Pops back to the graph's start destination first and reuses
// a saved instance of the target instead of stacking a fresh one, so the
// back stack can't accumulate duplicate entries and the Dashboard tab always
// cleanly returns regardless of how you navigated away from it.
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        graph.startDestinationRoute?.let { start ->
            popUpTo(start) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}
