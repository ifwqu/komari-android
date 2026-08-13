package com.komari.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.komari.app.ui.AdminScreen
import com.komari.app.ui.NodeDetailScreen
import com.komari.app.ui.NodeListScreen
import com.komari.app.ui.ServerEditScreen
import com.komari.app.ui.ServerListScreen
import com.komari.app.ui.theme.KomariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KomariTheme {
                AppNav()
            }
        }
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "servers") {

        composable("servers") {
            ServerListScreen(
                onAddServer = { navController.navigate("edit") },
                onEditServer = { id -> navController.navigate("edit/$id") },
                onOpenServer = { id -> navController.navigate("nodes/$id") }
            )
        }

        composable("edit") {
            ServerEditScreen(serverId = null, onBack = { navController.popBackStack() })
        }

        composable(
            route = "edit/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) { entry ->
            ServerEditScreen(
                serverId = entry.arguments?.getString("serverId"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "nodes/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) { entry ->
            val serverId = entry.arguments?.getString("serverId").orEmpty()
            NodeListScreen(
                serverId = serverId,
                onBack = { navController.popBackStack() },
                onOpenAdmin = { navController.navigate("admin/$serverId") },
                onOpenNode = { nodeId, name ->
                    navController.navigate("detail/$serverId/$nodeId?name=${Uri.encode(name)}")
                }
            )
        }

        composable(
            route = "admin/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) { entry ->
            AdminScreen(
                serverId = entry.arguments?.getString("serverId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "detail/{serverId}/{nodeId}?name={name}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.StringType },
                navArgument("nodeId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            NodeDetailScreen(
                serverId = entry.arguments?.getString("serverId").orEmpty(),
                nodeId = entry.arguments?.getString("nodeId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}