package com.aetheria.aura.actions

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safety guard for dangerous actions that require user confirmation.
 * Blocks execution until user confirms or cancels via dialog.
 */
object ActionGuard {

    private const val TAG = "ActionGuard"

    /** Dangerous shell command patterns that should be blocked or require extra confirmation */
    private val DANGEROUS_PATTERNS = listOf(
        "rm -rf",
        "rm -fr",
        "dd if=",
        "mkfs",
        "format",
        "> /dev/",
        "chmod 777",
        "chown root",
        "su -",
        "sudo ",
        "reboot",
        "shutdown",
        "poweroff",
        "halt",
        "init 0",
        "init 6",
        "killall",
        "pkill -9",
        "mv / ",
        "cp /dev/null",
        ":(){ :|:& };:",  // fork bomb
        "wget.*\\|.*sh",
        "curl.*\\|.*sh",
        "nc -l",
        "netcat -l"
    )

    /**
     * Validate a shell command for dangerous patterns.
     * Returns null if safe, or error message if dangerous.
     */
    fun validateCommand(command: String): String? {
        val lowerCmd = command.lowercase().trim()
        for (pattern in DANGEROUS_PATTERNS) {
            if (lowerCmd.contains(pattern.lowercase())) {
                return "Blocked dangerous pattern: $pattern"
            }
        }
        return null
    }

    /**
     * Show confirmation dialog for dangerous actions (CALL, SMS, ShizukuShell).
     * Returns true if user confirms, false if cancels.
     * Must be called from UI thread.
     */
    fun confirmAction(
        context: Context,
        title: String,
        message: String,
        positiveButton: String = "Confirm",
        negativeButton: String = "Cancel"
    ): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        
        // Must run on UI thread for AlertDialog
        val handler = android.os.Handler(context.mainLooper)
        handler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButton) { _, _ ->
                    Log.i(TAG, "User confirmed: $title")
                    deferred.complete(true)
                }
                .setNegativeButton(negativeButton) { _, _ ->
                    Log.i(TAG, "User cancelled: $title")
                    deferred.complete(false)
                }
                .setCancelable(false)
                .show()
        }
        
        return deferred
    }

    /**
     * Confirm CallContact action.
     */
    suspend fun confirmCall(context: Context, contactName: String): Boolean {
        val deferred = confirmAction(
            context = context,
            title = "Confirm Call",
            message = "Call $contactName?",
            positiveButton = "Call",
            negativeButton = "Cancel"
        )
        return deferred.await()
    }

    /**
     * Confirm SendText action.
     */
    suspend fun confirmSendText(context: Context, contactName: String, body: String): Boolean {
        val preview = if (body.length > 50) "${body.substring(0, 50)}..." else body
        val deferred = confirmAction(
            context = context,
            title = "Confirm Text Message",
            message = "Send to $contactName:\n\"$preview\"",
            positiveButton = "Send",
            negativeButton = "Cancel"
        )
        return deferred.await()
    }

    /**
     * Confirm ShizukuShell action with dangerous pattern check.
     */
    suspend fun confirmShell(context: Context, command: String): Boolean {
        val validationError = validateCommand(command)
        if (validationError != null) {
            Log.w(TAG, "Shell command blocked: $validationError")
            // Show error dialog instead of confirmation
            val deferred = CompletableDeferred<Boolean>()
            val handler = android.os.Handler(context.mainLooper)
            handler.post {
                AlertDialog.Builder(context)
                    .setTitle("Command Blocked")
                    .setMessage("Dangerous command detected:\n$validationError\n\nCommand: $command")
                    .setPositiveButton("OK") { _, _ -> deferred.complete(false) }
                    .setCancelable(false)
                    .show()
            }
            return deferred.await()
        }

        val deferred = confirmAction(
            context = context,
            title = "Confirm Shell Command",
            message = "Execute via Shizuku:\n$command",
            positiveButton = "Execute",
            negativeButton = "Cancel"
        )
        return deferred.await()
    }
}