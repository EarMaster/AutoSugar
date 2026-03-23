package com.autosugar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autosugar.ui.settings.ProfileEditScreen
import com.autosugar.ui.settings.SettingsScreen
import com.autosugar.ui.theme.AutoSugarTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_ADD = "add"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoSugarTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_SETTINGS) {
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            onAddProfile = { navController.navigate(ROUTE_ADD) },
                            onEditProfile = { id -> navController.navigate("$ROUTE_EDIT/$id") },
                        )
                    }
                    composable(ROUTE_ADD) {
                        ProfileEditScreen(
                            profileId = null,
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                    composable("$ROUTE_EDIT/{profileId}") { backStack ->
                        ProfileEditScreen(
                            profileId = backStack.arguments?.getString("profileId"),
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                }
            }
        }
    }
}
