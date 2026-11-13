package com.aetheria.aura.settings

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityServiceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetheria.aura.avatar.AuraColors
import com.aetheria.aura.net.HermesClient
import com.aetheria.aura.settings.UserProfileStore
import com.aetheria.aura.shizuku.ShizukuManager
import com.aetheria.aura.db.ChatRepository
import com.aetheria.aura.db.Conversation

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    chatRepository: ChatRepository,
    currentConversation: Conversation?,
    onClearConversation: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(HermesClient.getStoredEndpoint(context)) }
    var userProfile by remember { mutableStateOf(UserProfileStore.getUserName(context)) }
    var warning by remember { mutableStateOf<String?>(null) }

    val isCleartextNonLocalhost = HermesClient.isCleartextNonLocalhost(endpoint)

    if (isCleartextNonLocalhost && warning == null) {
        warning = "Warning: Release builds block cleartext HTTP to non-localhost hosts. " +
            "Use https:// or set the endpoint to localhost/127.0.0.1 for development."
    } else if (!isCleartextNonLocalhost && warning != null) {
        warning = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .size(44.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "<",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                .fillMaxWidth(0.85f)
                .align(Alignment.Start)
        )

        Text(
            text = "Hermes Endpoint",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.Start)
        )

        BasicTextField(
            value = endpoint,
            onValueChange = { newValue ->
                endpoint = newValue
                warning = null
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14181F))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(AuraColors.Cyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            decorationBox = { innerField ->
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    innerField()
                    if (endpoint.isNotBlank() &&
                        !endpoint.startsWith("http://", ignoreCase = true) &&
                        !endpoint.startsWith("https://", ignoreCase = true)) {
                        Text(
                            text = "Must start with http:// or https://",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (warning != null) {
            val w = warning
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Color(0xFF4D1A1A))
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "!  $w",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (endpoint.isNotBlank()) {
                    HermesClient.setEndpoint(context, endpoint.trim())
                }
                onClose()
            },
            enabled = endpoint.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuraColors.Cyan,
                contentColor = Color.Black
            )
        ) {
            Text(text = "Save & Return", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Current: ${HermesClient.getStoredEndpoint(context)}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Text(
            text = "First-run default: ${com.aetheria.aura.BuildConfig.HERMES_ENDPOINT}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "User Profile Name",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.Start)
        )

        BasicTextField(
            value = userProfile,
            onValueChange = { newValue ->
                userProfile = newValue
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14181F))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(AuraColors.Cyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            decorationBox = { innerField ->
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    innerField()
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                UserProfileStore.setUserName(context, userProfile.trim())
                onClose()
            },
            enabled = userProfile.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuraColors.Cyan,
                contentColor = Color.Black
            )
        ) {
            Text(text = "Save Profile & Return", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        val currentProfile = UserProfileStore.getUserName(context).ifEmpty { "(empty)" }
        Text(
            text = "Current: $currentProfile",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Permissions section
        val shizukuState = ShizukuManager.state.value
        val recordAudioGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityEnabled = checkAccessibilityEnabled(context)

        Text(
            text = "Permissions",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

        PermissionRow(
            title = "Microphone (Record Audio)",
            subtitle = if (recordAudioGranted) "Granted" else "Not granted - needed for voice input",
            granted = recordAudioGranted,
            onAction = {
                if (!recordAudioGranted) {
                    // Permission handled by onboarding
                }
            }
        )

        PermissionRow(
            title = "Shizuku (Shell Access)",
            subtitle = "State: $shizukuState - ${when (shizukuState) {
                com.aetheria.aura.shizuku.ShizukuManager.State.READY -> "Ready"
                com.aetheria.aura.shizuku.ShizukuManager.State.PERMISSION_NEEDED -> "Permission needed"
                com.aetheria.aura.shizuku.ShizukuManager.State.NOT_RUNNING -> "Not running"
                com.aetheria.aura.shizuku.ShizukuManager.State.NOT_INSTALLED -> "Not installed"
                else -> "Unknown"
            }}",
            granted = shizukuState == com.aetheria.aura.shizuku.ShizukuManager.State.READY,
            onAction = {
                when (shizukuState) {
                    com.aetheria.aura.shizuku.ShizukuManager.State.NOT_INSTALLED -> {
                        com.aetheria.aura.shizuku.ShizukuManager.getShizukuIntent().also { context.startActivity(it) }
                    }
                    com.aetheria.aura.shizuku.ShizukuManager.State.NOT_RUNNING -> {
                        com.aetheria.aura.shizuku.ShizukuManager.openShizukuIntent(context)?.also { context.startActivity(it) }
                    }
                    com.aetheria.aura.shizuku.ShizukuManager.State.PERMISSION_NEEDED -> {
                        com.aetheria.aura.shizuku.ShizukuManager.requestPermission()
                    }
                    com.aetheria.aura.shizuku.ShizukuManager.State.READY -> {
                        // Already ready
                    }
                }
            }
        )

        PermissionRow(
            title = "Accessibility Service",
            subtitle = if (accessibilityEnabled) "Enabled" else "Disabled - needed for screen reading",
            granted = accessibilityEnabled,
            onAction = {
                if (!accessibilityEnabled) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        PermissionRow(
            title = "Display Over Other Apps",
            subtitle = if (overlayGranted) "Granted" else "Not granted - needed for overlay UI",
            granted = overlayGranted,
            onAction = {
                if (!overlayGranted) {
                    val intent = android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // Clear Conversation
        if (currentConversation != null) {
            Divider(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 16.dp),
                color = Color.White.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Clear Conversation",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Delete all messages in current chat",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { onClearConversation(currentConversation) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4D1A),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "Clear", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Helper function to check if accessibility service is enabled
private fun checkAccessibilityEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    return try {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.view.accessibility.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        enabledServices.any { it.id.contains("AuraAccessibilityService") }
    } catch (e: Exception) {
        false
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        if (!granted) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(start = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraColors.Cyan,
                    contentColor = Color.Black
                )
            ) {
                Text(text = "Fix", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = AuraColors.Cyan,
                modifier = Modifier.size(24.dp).padding(start = 16.dp)
            )
        }
    }
}