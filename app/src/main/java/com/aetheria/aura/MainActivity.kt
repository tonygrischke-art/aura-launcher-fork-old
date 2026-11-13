package com.aetheria.aura

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.provider.Settings as PlatformSettings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aetheria.aura.avatar.AuraMood
import com.aetheria.aura.avatar.AuraHomeScreen
import com.aetheria.aura.nim.NimChatMessage
import com.aetheria.aura.nim.NimClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.aetheria.aura.model.TestModelFileManager
import com.aetheria.aura.net.HermesClient
import com.aetheria.aura.actions.ActionExecutor
import com.aetheria.aura.actions.ActionGuard
import com.aetheria.aura.router.AppIndex
import com.aetheria.aura.router.CommandRouter
import com.aetheria.aura.router.AuraActionRoute
import com.aetheria.aura.settings.SettingsScreen
import com.aetheria.aura.settings.UserProfileStore
import com.aetheria.aura.wakeWord.WakeWordService
import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
import com.aetheria.aura.actions.AuraAction
import com.aetheria.aura.accessibility.AuraAccessibilityService
import com.aetheria.aura.shizuku.ShellExecutor
import com.aetheria.aura.shizuku.ShizukuManager
import com.aetheria.aura.db.AuraDatabase
import com.aetheria.aura.db.ChatRepository
import com.aetheria.aura.db.Conversation
import com.aetheria.aura.db.ConversationDao
import com.aetheria.aura.db.Message
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import com.aetheria.aura.model.ModelFileManager

// Top-level PermissionItem for onboarding
data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val isSpecial: Boolean = false,
    val settingsAction: String? = null
)

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val content: String,
    val timestamp: Date = Date()
) {
    enum class Role { USER, AURA, SYSTEM }
}

class MainActivity : ComponentActivity() {
    private val TAG = "AuraMain"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private lateinit var nimClient: NimClient
    private lateinit var chatRepository: ChatRepository
    private lateinit var conversationDao: ConversationDao
    private var currentConversation: Conversation? = null
    private var currentPhase: AuraMood = AuraMood.IDLE
    private var micLevel = mutableFloatStateOf(0f)
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                Log.i(TAG, "Wake word detected - setting orb to LISTENING")
                currentPhase = AuraMood.LISTENING
            }
        }
    }

    // Permission onboarding state
    private val prefsName = "aura_prefs"
    private val prefsFirstRunKey = "first_run_complete"
    private val prefsAccessibilityEnabledKey = "accessibility_enabled"

    // Permission request launchers
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Permission result handled by onboarding UI
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Handled by onboarding UI
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize ChatRepository
        chatRepository = ChatRepository(this)
        conversationDao = AuraDatabase.getInstance(this).conversationDao()

        // Ensure model files are available in app private storage
        lifecycleScope.launch(Dispatchers.IO) {
            val success = TestModelFileManager.ensureModelsAvailable(this@MainActivity)
            Log.i("TestModelFileManager", "Models available: $success")
        }

        // Initialize SpeechRecognizer
        initSpeechRecognizer()

        // Initialize NimClient
        nimClient = NimClient(this) { readNimKey() }

        // Start wake word service on resume (API 34+ requires activity in foreground for microphone FGS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    val serviceIntent = Intent(this@MainActivity, WakeWordService::class.java)
                    startForegroundService(serviceIntent)
                    Log.i("AuraRouter", "Started WakeWordService from MainActivity.onResume (API 34+, RESUMED)")
                }
            }
        }

        // Register wake word broadcast receiver
        ContextCompat.registerReceiver(
            this, wakeWordReceiver,
            IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Load or create conversation, recover pending messages
        lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.recoverAllPendingMessages()
            // Try to restore the last used conversation ID from preferences
            val savedConversationId = UserProfileStore.getConversationId(this@MainActivity)
            if (savedConversationId > 0) {
                val savedConversation = conversationDao.getById(savedConversationId).first()
                if (savedConversation != null) {
                    currentConversation = savedConversation
                } else {
                    currentConversation = chatRepository.getOrCreateDefaultConversation()
                }
            } else {
                currentConversation = chatRepository.getOrCreateDefaultConversation()
            }
            // Persist the conversation ID for next launch
            currentConversation?.let { UserProfileStore.setConversationId(this@MainActivity, it.id) }
            Log.d(TAG, "Loaded conversation: ${currentConversation?.id} ${currentConversation?.title}")
        }

        setContent {
            val pm = packageManager
            val appIndex = AppIndex(this)
            // Build AppIndex on background thread
            lifecycleScope.launch(Dispatchers.IO) {
                val result = appIndex.build()
                val count = result.first
                val buildTimeMs = result.second
                Log.d("AuraRouter", "AppIndex built: $count apps in ${buildTimeMs}ms")
            }

            // Check if first run onboarding is needed
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            val firstRunComplete = prefs.getBoolean(prefsFirstRunKey, false)
            
            AuraTheme {
                            var input by remember { mutableStateOf("") }
                            var focused by remember { mutableStateOf(false) }
                            var phase by remember { mutableStateOf(currentPhase) }
                            var status by remember { mutableStateOf<String?>(null) }
                            var showSettings by remember { mutableStateOf(false) }
                            val scope = rememberCoroutineScope()

                            // Onboarding state
                            var showOnboarding by remember { mutableStateOf(!firstRunComplete) }

                            // Observe messages from database
                            val conversationId = currentConversation?.id ?: -1L
                            val messagesFlow = if (conversationId > 0) chatRepository.getMessages(conversationId) else kotlinx.coroutines.flow.emptyFlow()
                            val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                            fun submit() {
                                val cmd = input.trim(); if (cmd.isEmpty()) return
                                input = ""; status = null
                                doSubmit(cmd)
                            }

                            fun startListening() {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startSpeechRecognition()
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            fun stopListening() {
                                speechRecognizer?.stopListening()
                                isListening = false
                            }

                            val shown = if (phase != AuraMood.IDLE) phase else if (focused) AuraMood.LISTENING else AuraMood.IDLE

                            // Show onboarding overlay if needed
                            if (showOnboarding) {
                                PermissionOnboardingScreen(
                                    onComplete = {
                                        showOnboarding = false
                                        getSharedPreferences(prefsName, MODE_PRIVATE)
                                            .edit()
                                            .putBoolean(prefsFirstRunKey, true)
                                            .apply()
                                    },
                                    onSkip = {
                                        showOnboarding = false
                                        getSharedPreferences(prefsName, MODE_PRIVATE)
                                            .edit()
                                            .putBoolean(prefsFirstRunKey, true)
                                            .apply()
                                    },
                                    requestPermissionLauncher = requestPermissionLauncher,
                                    requestMultiplePermissionsLauncher = requestMultiplePermissionsLauncher
                                )
                            } else if (showSettings) {
                                SettingsScreen(
                                    onClose = { showSettings = false },
                                    chatRepository = chatRepository,
                                    currentConversation = currentConversation,
                                    onClearConversation = { conv ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            chatRepository.clearConversation(conv.id)
                                        }
                                    }
                                )
                            } else {
                                AuraHomeScreen(
                                    mood = shown,
                                    input = input,
                                    onInputChange = { input = it },
                                    onSubmit = { submit() },
                                    onFocusChange = { focused = it },
                                    onMic = {
                                        if (isListening) stopListening() else startListening()
                                    },
                                    status = status,
                                    messages = messages,
                                    onSettings = { showSettings = true },
                                    micLevel = micLevel.floatValue
                                )
                            }
                        }
        }
    }

    fun doSubmit(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        // Add user message to database
        val conv = currentConversation ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.addUserMessage(conv.id, trimmed)
        }
        Log.d("AuraRouter", "submit cmd='$trimmed'")

        // Run on background thread
        val activityContext = this@MainActivity
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        scope.launch {
            currentPhase = AuraMood.THINKING
            runOnUiThread { currentPhase = AuraMood.THINKING }
            try {
                // Route locally first
                val route = CommandRouter.route(trimmed)
                Log.d("AuraRouter", "route=$route")
                val startMs = System.currentTimeMillis()
                // Exhaustive when on sealed class AuraActionRoute
                val activity = activityContext
                val context = activity
                val pkgMgr = activity.packageManager
                val appIdx = AppIndex(activity)
                when (route) {
                    is AuraActionRoute.OpenApp -> {
                        val action = AuraAction.OpenApp(route.target)
                        val result = ActionExecutor.execute(action, context, pkgMgr, appIdx)
                        Log.d("AuraRouter", "Routed OPEN_APP target='${route.target}' action=$result matchTimeMs=${System.currentTimeMillis() - startMs}")
                        if (result == "error") {
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Could not find app: ${route.target}") }
                        }
                    }
                    is AuraActionRoute.CallContact -> {
                        // Confirm before making a call
                        val confirmed = ActionGuard.confirmCall(context, route.name)
                        if (!confirmed) {
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Call to ${route.name} cancelled") }
                            return@launch
                        }
                        val action = AuraAction.CallContact(route.name)
                        val result = ActionExecutor.execute(action, context, pkgMgr, appIdx)
                        Log.d("AuraRouter", "Routed CALL_CONTACT name='${route.name}' action=$result matchTimeMs=${System.currentTimeMillis() - startMs}")
                    }
                    is AuraActionRoute.SendText -> {
                        // Confirm before sending a text
                        val confirmed = ActionGuard.confirmSendText(context, route.name, route.body)
                        if (!confirmed) {
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Text to ${route.name} cancelled") }
                            return@launch
                        }
                        val action = AuraAction.SendText(route.name, route.body)
                        val result = ActionExecutor.execute(action, context, pkgMgr, appIdx)
                        Log.d("AuraRouter", "Routed SEND_TEXT name='${route.name}' body='${route.body}' action=$result matchTimeMs=${System.currentTimeMillis() - startMs}")
                    }
                    is AuraActionRoute.WebSearch -> {
                        val action = AuraAction.WebSearch(route.query)
                        val result = ActionExecutor.execute(action, context, pkgMgr, appIdx)
                        Log.d("AuraRouter", "Routed WEB_SEARCH query='${route.query}' action=$result matchTimeMs=${System.currentTimeMillis() - startMs}")
                    }
                    is AuraActionRoute.NavigateWeb -> {
                        val action = AuraAction.NavigateWeb(route.url)
                        val result = ActionExecutor.execute(action, context, pkgMgr, appIdx)
                        Log.d("AuraRouter", "Routed NAVIGATE_WEB url='${route.url}' action=$result matchTimeMs=${System.currentTimeMillis() - startMs}")
                    }
                    is AuraActionRoute.LocalLlm -> {
                        Log.d("AuraRouter", "Routed LOCAL_LLM input='${route.input}' matchTimeMs=${System.currentTimeMillis() - startMs}")

                        // Check model source preference
                        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                        val modelSource = ModelSource.valueOf(
                            prefs.getString("model_source", ModelSource.ONLINE.name) ?: ModelSource.ONLINE.name
                        )

                        // OFFLINE or BOTH: run the on-device model first; BOTH falls back to NIM on failure.
                        if (modelSource == ModelSource.OFFLINE || modelSource == ModelSource.BOTH) {
                            val c = currentConversation ?: return@launch
                            val localMsg = lifecycleScope.async(Dispatchers.IO) { chatRepository.addPendingAssistantMessage(c.id) }.await()
                            val localId = localMsg.id
                            if (localId < 0) return@launch
                            val history = chatRepository.buildHistoryForLlm(c.id)
                            val sb = StringBuilder()
                            val localStart = System.currentTimeMillis()
                            val localErr = com.aetheria.aura.llm.LocalLlm.chat(activity.applicationContext, history, 512) { piece ->
                                sb.append(piece)
                                val text = sb.toString()
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.updateAssistantMessage(localId, text) } }
                            }
                            Log.d("AuraRouter", "local inference done err=$localErr chars=${sb.length} ms=${System.currentTimeMillis() - localStart}")
                            if (localErr == null) {
                                lifecycleScope.launch(Dispatchers.IO) { chatRepository.completeAssistantMessage(localId, sb.toString()) }
                                return@launch
                            }
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(localId, "Local model failed: $localErr") }
                            if (modelSource == ModelSource.OFFLINE) {
                                lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Local model failed: $localErr") }
                                return@launch
                            }
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Local model unavailable ($localErr); using NIM...") }
                        }

                        // Use NIM (online) for now
                        val startTime = System.currentTimeMillis()
                        var firstToken = false
                        var tokenCount = 0
                        val c = currentConversation ?: return@launch
                        val msg = lifecycleScope.async(Dispatchers.IO) { chatRepository.addPendingAssistantMessage(c.id) }.await()
                        val messageId = msg.id
                        if (messageId < 0) return@launch

                        // Convert local messages to NimClient messages
                        val nimMessages = chatRepository.getNimMessages(c.id)

                        // Determine tier: VISION for image-related, THINKING for complex, INSTANT for quick
                        val tier = when {
                            route.input.contains("image") || route.input.contains("photo") || route.input.contains("picture") || route.input.contains("camera") || route.input.contains("screenshot") -> com.aetheria.aura.nim.NimClient.Tier.VISION
                            route.input.length > 200 -> com.aetheria.aura.nim.NimClient.Tier.THINKING
                            else -> com.aetheria.aura.nim.NimClient.Tier.INSTANT
                        }

                        nimClient.streamChat(
                            messages = nimMessages,
                            tier = tier,
                            onToken = { token ->
                                if (!firstToken) {
                                    val ttfb = System.currentTimeMillis() - startTime
                                    Log.d("AuraRouter", "TTFB: ${ttfb}ms")
                                    firstToken = true
                                }
                                tokenCount++
                                // Update the streaming message
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.updateAssistantMessage(messageId, "") } }
                            },
                            onComplete = { success ->
                                val totalTime = System.currentTimeMillis() - startTime
                                runOnUiThread {
                                    // Message content is already updated via onToken
                                }
                                Log.d("AuraRouter", "NimClient stream complete: success=$success tokens=$tokenCount totalTime=${totalTime}ms tier=$tier")
                                if (!success) {
                                    runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(messageId, "NIM unreachable (stream failed)") } }
                                } else {
                                    runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.completeAssistantMessage(messageId, "") } } // Content already updated
                                }
                            },
                            onError = { error ->
                                Log.e("AuraRouter", "NimClient error: $error")
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(messageId, "NIM unreachable ($error)") } }
                            }
                        )
                    }
                    is AuraActionRoute.AskHermes -> {
                        Log.d("AuraRouter", "Routed ASK_HERMES input='${route.input}' matchTimeMs=${System.currentTimeMillis() - startMs}")

                        // Check model source preference
                        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                        val modelSource = ModelSource.valueOf(
                            prefs.getString("model_source", ModelSource.ONLINE.name) ?: ModelSource.ONLINE.name
                        )

                        // OFFLINE or BOTH: run the on-device model first; BOTH falls back to NIM on failure.
                        if (modelSource == ModelSource.OFFLINE || modelSource == ModelSource.BOTH) {
                            val c = currentConversation ?: return@launch
                            val localMsg = lifecycleScope.async(Dispatchers.IO) { chatRepository.addPendingAssistantMessage(c.id) }.await()
                            val localId = localMsg.id
                            if (localId < 0) return@launch
                            val history = chatRepository.buildHistoryForLlm(c.id)
                            val sb = StringBuilder()
                            val localStart = System.currentTimeMillis()
                            val localErr = com.aetheria.aura.llm.LocalLlm.chat(activity.applicationContext, history, 512) { piece ->
                                sb.append(piece)
                                val text = sb.toString()
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.updateAssistantMessage(localId, text) } }
                            }
                            Log.d("AuraRouter", "local inference done err=$localErr chars=${sb.length} ms=${System.currentTimeMillis() - localStart}")
                            if (localErr == null) {
                                lifecycleScope.launch(Dispatchers.IO) { chatRepository.completeAssistantMessage(localId, sb.toString()) }
                                return@launch
                            }
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(localId, "Local model failed: $localErr") }
                            if (modelSource == ModelSource.OFFLINE) {
                                lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Local model failed: $localErr") }
                                return@launch
                            }
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Local model unavailable ($localErr); using NIM...") }
                        }

                        // Use NIM (online) for now
                        val startTime = System.currentTimeMillis()
                        var firstToken = false
                        var tokenCount = 0
                        val c = currentConversation ?: return@launch
                        val msg = lifecycleScope.async(Dispatchers.IO) { chatRepository.addPendingAssistantMessage(c.id) }.await()
                        val messageId = msg.id
                        if (messageId < 0) return@launch

                        // Convert local messages to NimClient messages
                        val nimMessages = chatRepository.getNimMessages(c.id)

                        // Determine tier: VISION for image-related, THINKING for complex, INSTANT for quick
                        val tier = when {
                            route.input.contains("image") || route.input.contains("photo") || route.input.contains("picture") || route.input.contains("camera") || route.input.contains("screenshot") -> com.aetheria.aura.nim.NimClient.Tier.VISION
                            route.input.length > 200 -> com.aetheria.aura.nim.NimClient.Tier.THINKING
                            else -> com.aetheria.aura.nim.NimClient.Tier.INSTANT
                        }

                        nimClient.streamChat(
                            messages = nimMessages,
                            tier = tier,
                            onToken = { token ->
                                if (!firstToken) {
                                    val ttfb = System.currentTimeMillis() - startTime
                                    Log.d("AuraRouter", "TTFB: ${ttfb}ms")
                                    firstToken = true
                                }
                                tokenCount++
                                // Update the streaming message
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.updateAssistantMessage(messageId, "") } }
                            },
                            onComplete = { success ->
                                val totalTime = System.currentTimeMillis() - startTime
                                runOnUiThread {
                                    // Message content is already updated via onToken
                                }
                                Log.d("AuraRouter", "NimClient stream complete: success=$success tokens=$tokenCount totalTime=${totalTime}ms tier=$tier")
                                if (!success) {
                                    runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(messageId, "NIM unreachable (stream failed)") } }
                                } else {
                                    runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.completeAssistantMessage(messageId, "") } } // Content already updated
                                }
                            },
                            onError = { error ->
                                Log.e("AuraRouter", "NimClient error: $error")
                                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.failAssistantMessage(messageId, "NIM unreachable ($error)") } }
                            }
                        )
                    }
                    is AuraActionRoute.ShizukuShell -> {
                        // Confirm before executing shell command (with dangerous pattern check)
                        val confirmed = ActionGuard.confirmShell(context, route.command)
                        if (!confirmed) {
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Shell command cancelled") }
                            return@launch
                        }
                        // Raw shell command via the Shizuku user service (uid 2000 shell) with 30s timeout.
                        val shizukuResult = withTimeoutOrNull(30_000) {
                            ShellExecutor.exec(route.command)
                        } ?: ShellExecutor.Result("", "TIMEOUT: Shell command exceeded 30 seconds", -1)
                        Log.d("AuraRouter", "Routed SHIZUKU_SHELL cmd='${route.command}' exit=${shizukuResult.exitCode}")
                        val c = currentConversation ?: return@launch
                        lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, buildString {
                            append("shizuku: ${route.command}")
                            append("\nexit=${shizukuResult.exitCode}")
                            if (shizukuResult.stdout.isNotBlank()) append("\nstdout:\n${shizukuResult.stdout.take(4000)}")
                            if (shizukuResult.stderr.isNotBlank()) append("\nstderr:\n${shizukuResult.stderr.take(4000)}")
                        }) }
                    }
                    is AuraActionRoute.ScreenControl -> {
                        // Screen control via ScreenAgent (local LLM + Shizuku shell).
                        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id,
                                "Screen control needs Shizuku: ${ShizukuManager.state.value}. " +
                                "Start Shizuku app, grant permission, then retry."
                            ) }
                        } else {
                            val result = com.aetheria.aura.shizuku.ScreenAgent.runTask(activityContext, route.command) { step ->
                                Log.d("ScreenAgent", "step: $step")
                            }
                            val c = currentConversation ?: return@launch
                            lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, result) }
                        }
                    }
                    is AuraActionRoute.IdentityQuery -> {
                        val c = currentConversation ?: return@launch
                        val name = UserProfileStore.getUserName(context)
                        val reply = when (route.queryType) {
                            "name" -> if (name.isNotBlank()) "You're $name." else "I don't know your name yet. You can set it in Settings."
                            "location" -> "I can't see your location."
                            else -> "Unknown query."
                        }
                        lifecycleScope.launch(Dispatchers.IO) {
                            chatRepository.addUserMessage(c.id, trimmed)
                            chatRepository.addSystemMessage(c.id, reply)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AuraRouter", "submit failed cmd='$trimmed'", e)
                val c = currentConversation ?: return@launch
                runOnUiThread { lifecycleScope.launch(Dispatchers.IO) { chatRepository.addSystemMessage(c.id, "Error: ${e.message}") } }
            } finally {
                currentPhase = AuraMood.IDLE
                runOnUiThread { currentPhase = AuraMood.IDLE }
            }
        }
    }

    private fun readNimKey(): String {
        return try {
            val prefs = getSharedPreferences("aura_prefs", MODE_PRIVATE)
            prefs.getString("nim_key", "") ?: ""
        } catch (e: Exception) {
            Log.e("AuraRouter", "Failed to read NIM key", e)
            ""
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Log.d("AuraMic", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("AuraMic", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                micLevel.floatValue = micLevel.floatValue * 0.6f + norm * 0.4f
            }

            override fun onBufferReceived(buffer: ByteArray) {}

            override fun onEndOfSpeech() {
                isListening = false
                micLevel.floatValue = 0f
                Log.d("AuraMic", "Speech ended")
            }

            override fun onError(error: Int) {
                isListening = false
                micLevel.floatValue = 0f
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error ($error)"
                }
                Log.e("AuraMic", "Speech error: $errorMsg")
            }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("AuraMic", "Recognized: $recognizedText")
                    // Submit the recognized text
                    doSubmit(recognizedText)
                    micLevel.floatValue = 0f
                }
            }

            override fun onPartialResults(partialResults: Bundle) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("AuraMic", "Failed to start listening", e)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "wakeWordReceiver not registered", e)
        }
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF8B5CF6),
        surface = Color(0xFF000000),
        error = Color(0xFFFF4D1A),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onSurface = Color.White,
        onError = Color.White,
    )
    androidx.compose.material3.MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        shapes = androidx.compose.material3.Shapes(),
        content = content
    )
}

/**
 * Permission onboarding screen shown on first run.
 * Guides user through granting all required permissions.
 */
@Composable
fun PermissionOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    requestMultiplePermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // All dangerous permissions from manifest (normal permissions like INTERNET, ACCESS_NETWORK_STATE are auto-granted)
    val dangerousPermissions = remember {
        mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ).filter { perm ->
            // Filter out permissions not applicable to current SDK
            when (perm) {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO -> android.os.Build.VERSION.SDK_INT >= 33
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> android.os.Build.VERSION.SDK_INT <= 32
                Manifest.permission.POST_NOTIFICATIONS -> android.os.Build.VERSION.SDK_INT >= 33
                else -> true
            }
        }.toMutableList().also { permList ->
            // Add POST_NOTIFICATIONS for SDK 33+
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                permList.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    // Special access permissions (each needs its own settings screen)
    val specialPermissions = remember {
        listOf(
            SpecialPermissionItem(
                title = "Display Over Other Apps",
                description = "Show Aura overlay and floating UI",
                checkGranted = { PlatformSettings.canDrawOverlays(context) },
                requestAction = {
                    val intent = Intent(PlatformSettings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        android.net.Uri.parse("package:${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            ),
            SpecialPermissionItem(
                title = "Accessibility Service",
                description = "Screen reading and UI automation for screen-act loop",
                checkGranted = { checkAccessibilityEnabled(context) },
                requestAction = {
                    val intent = Intent(PlatformSettings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            ),
            SpecialPermissionItem(
                title = "Battery Optimization",
                description = "Allow Aura to run in background for wake word and automations",
                checkGranted = { isIgnoringBatteryOptimizations(context) },
                requestAction = {
                    val intent = Intent(PlatformSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            ),
            SpecialPermissionItem(
                title = "Default Home App",
                description = "Set Aura as your home screen for voice-first access",
                checkGranted = { isDefaultHomeApp(context) },
                requestAction = {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    context.startActivity(intent)
                }
            ),
            SpecialPermissionItem(
                title = "Default Assistant",
                description = "Activate Aura with long-press power or voice",
                checkGranted = { isDefaultAssistant(context) },
                requestAction = {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
                    context.startActivity(intent)
                }
            ),
            SpecialPermissionItem(
                title = "Shizuku (Shell Access)",
                description = "Required for screen control and system-level actions",
                checkGranted = { com.aetheria.aura.shizuku.ShizukuManager.state.value == com.aetheria.aura.shizuku.ShizukuManager.State.READY },
                requestAction = {
                    com.aetheria.aura.shizuku.ShizukuManager.requestPermission()
                }
            )
        )
    }
    
    // Track dangerous permission states
    val dangerousPermStates = remember {
        mutableStateMapOf<String, Boolean>().also { map ->
            dangerousPermissions.forEach { map[it] = ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }
    }
    
    // Track special permission states
    val specialPermStates = remember {
        mutableStateMapOf<String, Boolean>().also { map ->
            specialPermissions.forEach { map[it.title] = it.checkGranted() }
        }
    }
    
    val allDangerousGranted = remember { mutableStateOf(dangerousPermStates.values.all { it }) }
    val allSpecialGranted = remember { mutableStateOf(specialPermStates.values.all { it }) }
    val allGranted = remember { mutableStateOf(allDangerousGranted.value && allSpecialGranted.value) }
    
    // Request all dangerous permissions at once
    fun requestAllDangerousPermissions() {
        val toRequest = dangerousPermissions.filter { dangerousPermStates[it] != true }.toTypedArray()
        if (toRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(toRequest)
        }
    }
    
    // Update states when permissions change
    androidx.compose.runtime.LaunchedEffect(dangerousPermStates, specialPermStates) {
        allDangerousGranted.value = dangerousPermStates.values.all { it }
        allSpecialGranted.value = specialPermStates.values.all { it }
        allGranted.value = allDangerousGranted.value && allSpecialGranted.value
    }
    
    // Re-check special permissions on resume
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            specialPermissions.forEach { perm ->
                specialPermStates[perm.title] = perm.checkGranted()
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
        )
        
        // Onboarding card
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp)
                .heightIn(min = 400.dp, max = 700.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Welcome to Aura",
                    style = Typography().headlineMedium,
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Grant permissions to unlock all features",
                    style = Typography().bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                // Progress indicator
                val totalCount = dangerousPermissions.size + specialPermissions.size
                val grantedCount = dangerousPermStates.values.count { it } + specialPermStates.values.count { it }
                
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = grantedCount.toFloat() / totalCount.toFloat()
                )
                
                Text(
                    text = "$grantedCount of $totalCount permissions granted",
                    style = Typography().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                // Dangerous permissions section
                Text(
                    text = "App Permissions",
                    style = Typography().titleMedium,
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Start
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    dangerousPermissions.forEach { perm ->
                        val granted = dangerousPermStates[perm] ?: false
                        PermissionRow(
                            title = getPermissionTitle(perm),
                            subtitle = getPermissionDescription(perm),
                            granted = granted,
                            onRequest = { requestAllDangerousPermissions() }
                        )
                    }
                }
                
                // Special permissions section
                Text(
                    text = "Special Access",
                    style = Typography().titleMedium,
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Start
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    specialPermissions.forEach { perm ->
                        val granted = specialPermStates[perm.title] ?: false
                        SpecialPermissionRow(
                            permission = perm,
                            granted = granted
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip for now")
                    }
                    
                    Button(
                        onClick = { if (allGranted.value) onComplete() },
                        modifier = Modifier.weight(1f),
                        enabled = allGranted.value,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allGranted.value) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (allGranted.value) Color.Black else Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(if (allGranted.value) "Continue" else "Grant all first")
                    }
                }
            }
        }
    }
}

data class SpecialPermissionItem(
    val title: String,
    val description: String,
    val checkGranted: () -> Boolean,
    val requestAction: () -> Unit
)

@Composable
fun SpecialPermissionRow(
    permission: SpecialPermissionItem,
    granted: Boolean,
    onRequest: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                if (granted) Color(0xFF00E5FF).copy(alpha = 0.1f) else Color.Transparent,
                shape = Shapes().small
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Settings,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = permission.title, style = Typography().bodyMedium, color = Color.White)
            Text(text = permission.description, style = Typography().bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        
        if (!granted) {
            Button(
                onClick = permission.requestAction,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                )
            ) {
                Text("Open Settings")
            }
        } else {
            Text(text = "✓ Granted", style = Typography().bodySmall, color = Color(0xFF00E5FF))
        }
    }
}

private fun getPermissionTitle(permission: String): String = when (permission) {
    Manifest.permission.RECORD_AUDIO -> "Microphone"
    Manifest.permission.READ_CONTACTS -> "Contacts (Read)"
    Manifest.permission.WRITE_CONTACTS -> "Contacts (Write)"
    Manifest.permission.READ_SMS -> "SMS (Read)"
    Manifest.permission.SEND_SMS -> "SMS (Send)"
    Manifest.permission.RECEIVE_SMS -> "SMS (Receive)"
    Manifest.permission.CALL_PHONE -> "Phone Calls"
    Manifest.permission.READ_PHONE_STATE -> "Phone State"
    Manifest.permission.READ_CALL_LOG -> "Call Log (Read)"
    Manifest.permission.WRITE_CALL_LOG -> "Call Log (Write)"
    Manifest.permission.ANSWER_PHONE_CALLS -> "Answer Calls"
    Manifest.permission.CAMERA -> "Camera"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location (Fine)"
    Manifest.permission.ACCESS_COARSE_LOCATION -> "Location (Coarse)"
    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Location (Background)"
    Manifest.permission.READ_MEDIA_IMAGES -> "Media (Images)"
    Manifest.permission.READ_MEDIA_VIDEO -> "Media (Video)"
    Manifest.permission.READ_MEDIA_AUDIO -> "Media (Audio)"
    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    else -> permission
}

private fun getPermissionDescription(permission: String): String = when (permission) {
    Manifest.permission.RECORD_AUDIO -> "Required for voice commands and wake word detection"
    Manifest.permission.READ_CONTACTS -> "Needed to send texts and make calls by name"
    Manifest.permission.WRITE_CONTACTS -> "Create or update contacts from voice"
    Manifest.permission.READ_SMS -> "Read SMS for context and automation"
    Manifest.permission.SEND_SMS -> "Send text messages from Aura"
    Manifest.permission.RECEIVE_SMS -> "Receive SMS for automation triggers"
    Manifest.permission.CALL_PHONE -> "Make phone calls by voice command"
    Manifest.permission.READ_PHONE_STATE -> "Detect call state for automation"
    Manifest.permission.READ_CALL_LOG -> "Read call history for context"
    Manifest.permission.WRITE_CALL_LOG -> "Log calls made by Aura"
    Manifest.permission.ANSWER_PHONE_CALLS -> "Auto-answer calls for automation"
    Manifest.permission.CAMERA -> "Future: vision features and QR codes"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location-based queries and services"
    Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location for services"
    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Location access when app is backgrounded"
    Manifest.permission.READ_MEDIA_IMAGES -> "Access photos for vision features"
    Manifest.permission.READ_MEDIA_VIDEO -> "Access videos for vision features"
    Manifest.permission.READ_MEDIA_AUDIO -> "Access audio for voice features"
    Manifest.permission.POST_NOTIFICATIONS -> "Show Aura responses and alerts"
    else -> "Required for Aura functionality"
}

private fun checkAccessibilityEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    return try {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        enabledServices.any { it.id.contains("AuraAccessibilityService") }
    } catch (e: Exception) {
        false
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return try {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isDefaultHomeApp(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

private fun isDefaultAssistant(context: Context): Boolean {
    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
    return try {
        roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)
    } catch (e: Exception) {
        false
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                if (granted) Color(0xFF00E5FF).copy(alpha = 0.1f) else Color.Transparent,
                shape = Shapes().small
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.HelpOutline,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography().bodyMedium, color = Color.White)
            Text(text = subtitle, style = Typography().bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        
        if (!granted) {
            Button(
                onClick = onRequest,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text("Grant")
            }
        } else {
            Text(text = "✓ Granted", style = Typography().bodySmall, color = Color(0xFF00E5FF))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onClose: () -> Unit,
    chatRepository: ChatRepository,
    currentConversation: Conversation?,
    onClearConversation: (Conversation) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)

    // Model source preference
    var modelSource by remember { mutableStateOf(
        ModelSource.valueOf(prefs.getString("model_source", ModelSource.ONLINE.name) ?: ModelSource.ONLINE.name)
    ) }

    // Show onboarding on next launch
    var showOnboardingOnNextLaunch by remember { mutableStateOf(
        prefs.getBoolean("show_onboarding_next_launch", false)
    ) }

    // NIM key - stored in SharedPreferences (not committed to git)
    var nimKey by remember { mutableStateOf(prefs.getString("nim_key", "") ?: "") }

    // NIM key status
    val nimKeyExists = nimKey.isNotBlank()

    // Local models status - use ModelFileManager's directory
    val localModelsDir = ModelFileManager.modelsDir(context)
    val localModelsExist = localModelsDir.exists() && localModelsDir.listFiles()?.isNotEmpty() == true

    // Availability: ONLINE needs key, OFFLINE needs models, BOTH needs BOTH
    val onlineAvailable = nimKeyExists
    val offlineAvailable = localModelsExist
    val bothAvailable = onlineAvailable && offlineAvailable

    // If selected source becomes unavailable, switch to first available
    LaunchedEffect(onlineAvailable, offlineAvailable, bothAvailable) {
        val currentAvailable = when (modelSource) {
            ModelSource.ONLINE -> onlineAvailable
            ModelSource.OFFLINE -> offlineAvailable
            ModelSource.BOTH -> bothAvailable
        }
        if (!currentAvailable) {
            val firstAvailable = when {
                onlineAvailable -> ModelSource.ONLINE
                offlineAvailable -> ModelSource.OFFLINE
                bothAvailable -> ModelSource.BOTH
                else -> ModelSource.ONLINE
            }
            modelSource = firstAvailable
            prefs.edit().putString("model_source", firstAvailable.name).apply()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
        )
        
        // Settings card
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp)
                .heightIn(min = 400.dp, max = 700.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = Typography().headlineMedium,
                        color = Color(0xFF00E5FF)
                    )
                    
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close settings",
                            tint = Color.White
                        )
                    }
                }
                
                // Model Source Selection
                Text(
                    text = "AI Model Source",
                    style = Typography().titleMedium,
                    color = Color(0xFF00E5FF)
                )
                
                Text(
                    text = "Choose how Aura processes your requests",
                    style = Typography().bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                
                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ModelSource.values().forEach { source ->
                                        val isSelected = (modelSource == source)
                                        val isAvailable = when (source) {
                                            ModelSource.ONLINE -> onlineAvailable
                                            ModelSource.OFFLINE -> offlineAvailable
                                            ModelSource.BOTH -> bothAvailable
                                        }

                                        // Keep selected item enabled even if unavailable, so user can see status/fix it
                                        // If ALL are unavailable, keep ONLINE enabled as default
                                        val allUnavailable = !onlineAvailable && !offlineAvailable && !bothAvailable
                                        val itemEnabled = isAvailable || isSelected || (allUnavailable && source == ModelSource.ONLINE)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f)
                                    else if (!itemEnabled) Color.White.copy(alpha = 0.05f)
                                    else Color.Transparent
                                ),
                            onClick = { 
                                if (itemEnabled) {
                                    modelSource = source
                                    prefs.edit().putString("model_source", source.name).apply()
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { 
                                        if (itemEnabled) {
                                            modelSource = source
                                            prefs.edit().putString("model_source", source.name).apply()
                                        }
                                    },
                                    enabled = itemEnabled,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = source.displayName,
                                        style = Typography().bodyMedium,
                                        color = if (itemEnabled) Color.White else Color.White.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = source.description,
                                        style = Typography().bodySmall,
                                        color = if (itemEnabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                                
                                if (!itemEnabled) {
                                    Text(
                                        text = "Unavailable",
                                        style = Typography().bodySmall,
                                        color = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Divider
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                
                // Other Settings
                Text(
                    text = "Other Settings",
                    style = Typography().titleMedium,
                    color = Color(0xFF00E5FF)
                )
                
                // Show onboarding toggle
                var showOnboardingNextLaunch by remember { mutableStateOf(
                    context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
                        .getBoolean("show_onboarding_next_launch", false)
                ) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Show onboarding on next launch",
                            style = Typography().bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Re-run permission onboarding",
                            style = Typography().bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    Switch(
                                            checked = showOnboardingNextLaunch,
                                            onCheckedChange = { checked ->
                                                showOnboardingNextLaunch = checked
                                                context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)
                                                    .edit()
                                                    .putBoolean("show_onboarding_next_launch", checked)
                                                    .apply()
                                            }
                                        )
                                    }

                                    // NIM API Key input
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "NIM API Key",
                                            style = Typography().titleMedium,
                                            color = Color(0xFF00E5FF)
                                        )
                                        Text(
                                            text = "Enter your NVIDIA NIM API key for online models",
                                            style = Typography().bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                        var keyInput by remember { mutableStateOf(nimKey) }
                                        TextField(
                                            value = keyInput,
                                            onValueChange = { newVal ->
                                                keyInput = newVal
                                                nimKey = newVal
                                                prefs.edit().putString("nim_key", newVal).apply()
                                            },
                                            label = { Text("NIM API Key", style = Typography().bodyMedium, color = Color.White.copy(alpha = 0.6f)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            colors = TextFieldDefaults.textFieldColors(
                                                containerColor = Color.White.copy(alpha = 0.05f),
                                                unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
                                                focusedIndicatorColor = Color(0xFF00E5FF)
                                            )
                                        )
                                        if (nimKeyExists) {
                                            Text(
                                                text = "Key saved ✓",
                                                style = Typography().bodySmall,
                                                color = Color(0xFF00E5FF)
                                            )
                                        }
                                    }

                                    // Divider
                                    Divider(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        color = Color.White.copy(alpha = 0.2f),
                                        thickness = 1.dp
                                    )

                                    // Local Models Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Local Models (GGUF)",
                            style = Typography().bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = if (localModelsExist) "Available ✓" else "Not found - copy to external storage: /sdcard/Android/data/com.aetheria.aura/files/models/",
                            style = Typography().bodySmall,
                            color = if (localModelsExist) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    if (!localModelsExist) {
                        OutlinedButton(
                            onClick = {
                                // Could open file manager or show instructions
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Manage")
                        }
                    }
                }

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                LaunchedEffect(Unit) { ShizukuManager.refresh() }
                val shizukuState by ShizukuManager.state.collectAsState()

                Text(
                    text = "Shizuku (shell access)",
                    style = Typography().titleMedium,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "State: ${shizukuState.name}",
                    style = Typography().bodyMedium,
                    color = if (shizukuState == ShizukuManager.State.READY) Color(0xFF00E5FF) else Color.White
                )
                Text(
                    text = "Powers the \"shizuku:\" command prefix and Aura's local screen tools.",
                    style = Typography().bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { ShizukuManager.requestPermission() },
                        enabled = shizukuState == ShizukuManager.State.PERMISSION_NEEDED || shizukuState == ShizukuManager.State.NOT_RUNNING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                    ) {
                        Text("Request permission")
                    }
                    Button(
                        onClick = {
                            context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { intent ->
                                context.startActivity(intent)
                            }
                        },
                        enabled = shizukuState != ShizukuManager.State.NOT_INSTALLED,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                    ) {
                        Text("Open Shizuku")
                    }
                    Button(
                        onClick = { context.startActivity(ShizukuManager.getShizukuIntent()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                    ) {
                        Text("Get Shizuku")
                    }
                }

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                // Clear Conversation
                if (currentConversation != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Clear Conversation",
                                style = Typography().bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Delete all messages in current chat",
                                style = Typography().bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Button(
                            onClick = { onClearConversation(currentConversation) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D1A), contentColor = Color.White)
                        ) {
                            Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

enum class ModelSource {
    ONLINE("Online (NIM)", "NVIDIA NIM API - requires internet, fastest"),
    OFFLINE("Offline (Local)", "Local GGUF models - private, no internet needed"),
    BOTH("Auto (Offline → Online)", "Try local first, fallback to NIM");

    val displayName: String
    val description: String

    constructor(displayName: String, description: String) {
        this.displayName = displayName
        this.description = description
    }
}

