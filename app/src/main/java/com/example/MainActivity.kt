package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.engine.WorldEngine
import com.example.model.UserRole
import com.example.ui.components.TopVitalsBar
import com.example.ui.screens.*
import com.example.ui.theme.*

enum class AppScreen(val title: String, val icon: ImageVector) {
    WORLD("World", Icons.Default.Explore),
    CHARACTER("Hero", Icons.Default.Person),
    INVENTORY("Bags", Icons.Default.Backpack),
    QUESTS("Quests", Icons.Default.Assignment),
    FORGE("Forge", Icons.Default.AutoAwesome),
    CHAT("Chat", Icons.Default.Chat),
    OWNER("Owner", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {
    private val repository = GameRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScaffold(repository = repository)
            }
        }
    }
}

@Composable
fun MainAppScaffold(repository: GameRepository) {
    var currentScreen by remember { mutableStateOf(AppScreen.WORLD) }
    val player by repository.player.collectAsState()
    val serverMessage by repository.serverMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(serverMessage) {
        serverMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            repository.clearServerMessage()
        }
    }

    val currentZoneName = remember(player.zoneId) {
        WorldEngine.ZONES.find { it.id == player.zoneId }?.name ?: "Astral Sanctuary"
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopVitalsBar(
                player = player,
                zoneName = currentZoneName
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = VoidDark,
                contentColor = NeonCyan,
                tonalElevation = 8.dp
            ) {
                val screens = if (player.role == UserRole.OWNER || player.role == UserRole.ADMIN) {
                    AppScreen.values().toList()
                } else {
                    AppScreen.values().filter { it != AppScreen.OWNER }
                }

                screens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = if (isSelected) NeonCyan else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                color = if (isSelected) NeonCyan else Color.Gray,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = VoidSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(VoidBlack)
        ) {
            when (currentScreen) {
                AppScreen.WORLD -> WorldScreen(repository = repository)
                AppScreen.CHARACTER -> CharacterScreen(repository = repository)
                AppScreen.INVENTORY -> InventoryScreen(repository = repository)
                AppScreen.QUESTS -> QuestScreen(repository = repository)
                AppScreen.FORGE -> ForgeScreen(repository = repository)
                AppScreen.CHAT -> ChatScreen(repository = repository)
                AppScreen.OWNER -> OwnerPanelScreen(repository = repository)
            }
        }
    }
}
