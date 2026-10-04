package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ServerAccountDialog(
    repository: GameRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val authSession by repository.authSession.collectAsState()
    val networkState by repository.networkState.collectAsState()
    val availableCharacters by repository.availableCharacters.collectAsState()

    var isRegisterTab by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(repository.apiClient.getBaseUrl()) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newCharacterName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (networkState.isConnected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = if (networkState.isConnected) NeonGreen else NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Realm Multiplayer Server",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        repository.apiClient.setBaseUrl(it)
                    },
                    label = { Text("Server URL", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = VoidOutline
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                )

                // Status message
                Text(
                    text = "Status: ${networkState.statusMessage}",
                    color = if (networkState.isConnected) NeonGreen else Color.LightGray,
                    fontSize = 12.sp
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = VoidCrimson,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (successMessage != null) {
                    Text(
                        text = successMessage!!,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (authSession == null) {
                    // TAB SELECTOR: Login vs Register
                    TabRow(
                        selectedTabIndex = if (isRegisterTab) 1 else 0,
                        containerColor = VoidSurfaceVariant,
                        contentColor = NeonCyan
                    ) {
                        Tab(
                            selected = !isRegisterTab,
                            onClick = {
                                isRegisterTab = false
                                errorMessage = null
                            },
                            text = { Text("Login") }
                        )
                        Tab(
                            selected = isRegisterTab,
                            onClick = {
                                isRegisterTab = true
                                errorMessage = null
                            },
                            text = { Text("Register") }
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = VoidOutline
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_username_input")
                    )

                    if (isRegisterTab) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = VoidOutline
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = VoidOutline
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                    )

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                if (isRegisterTab) {
                                    val res = repository.registerOnServer(username, email, password)
                                    res.onSuccess {
                                        successMessage = "Registered & Logged in!"
                                    }.onFailure {
                                        errorMessage = it.message
                                    }
                                } else {
                                    val res = repository.loginToServer(username, password)
                                    res.onSuccess {
                                        successMessage = "Login successful!"
                                    }.onFailure {
                                        errorMessage = it.message
                                    }
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AstralVioletDark),
                        modifier = Modifier.fillMaxWidth().testTag("auth_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(18.dp))
                        } else {
                            Text(if (isRegisterTab) "Create Account" else "Authenticate")
                        }
                    }
                } else {
                    // LOGGED IN: Manage Characters & World Connection
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VoidSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Account: ${authSession!!.username} [${authSession!!.role.displayName}]",
                                color = NeonYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "User ID: ${authSession!!.userId.take(8)}...",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text("Characters in Realm:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    if (availableCharacters.isEmpty()) {
                        Text("No characters found on this account.", color = Color.LightGray, fontSize = 12.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableCharacters) { charSummary ->
                                val isConnectedToThis = networkState.activeCharacterId == charSummary.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isConnectedToThis) AstralVioletDark.copy(alpha = 0.4f) else VoidDark)
                                        .border(1.dp, if (isConnectedToThis) NeonGreen else VoidOutline, RoundedCornerShape(6.dp))
                                        .clickable {
                                            repository.connectToWorld(charSummary.id)
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(charSummary.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Lvl ${charSummary.level} • Gold: ${charSummary.gold}", color = VoidGold, fontSize = 11.sp)
                                    }
                                    if (isConnectedToThis) {
                                        Badge(containerColor = NeonGreen) {
                                            Text("ACTIVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text("Connect", color = NeonCyan, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Create Character Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCharacterName,
                            onValueChange = { newCharacterName = it },
                            label = { Text("New Character") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = VoidOutline
                            ),
                            modifier = Modifier.weight(1f).testTag("new_character_name_input")
                        )
                        Button(
                            onClick = {
                                if (newCharacterName.isNotBlank()) {
                                    coroutineScope.launch {
                                        repository.createServerCharacter(newCharacterName)
                                        newCharacterName = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AstralViolet),
                            enabled = newCharacterName.length >= 3,
                            modifier = Modifier.testTag("create_character_button")
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NeonCyan)
            }
        },
        containerColor = VoidDark
    )
}
