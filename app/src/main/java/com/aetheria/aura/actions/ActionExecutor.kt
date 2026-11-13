package com.aetheria.aura.actions

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.aetheria.aura.actions.AuraAction.OpenApp
import com.aetheria.aura.net.HermesClient
import com.aetheria.aura.router.AppIndex
import com.aetheria.aura.shizuku.ScreenAgent
import com.aetheria.aura.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes parsed AuraAction results.
 * For OpenApp: matches target against installed app labels using AppIndex,
 * launches via PackageManager.getLaunchIntentForPackage, returns confirm status.
 * For CallContact/SendText: resolves contact name to phone number via ContactsContract.
 * Unknown or no match returns error state.
 */
object ActionExecutor {

    /**
 * Executes the given AuraAction using the PackageManager and AppIndex to find and launch matching apps.
 */
    suspend fun execute(action: AuraAction, context: Context, pm: PackageManager, appIndex: AppIndex? = null): String {
        Log.d("AuraRouter", "ActionExecutor.execute action=${action::class.simpleName} $action")
        return when (action) {
            is AuraAction.OpenApp -> executeOpenApp(action, context, pm, appIndex)
            is AuraAction.CallContact -> executeCallContact(action, context, pm)
            is AuraAction.SendText -> executeSendText(action, context, pm)
            is AuraAction.WebSearch -> executeWebSearch(action, context, pm)
            is AuraAction.NavigateWeb -> executeNavigateWeb(action, context, pm)
            is AuraAction.TakeScreenshot -> executeTakeScreenshot(action, context)
            is AuraAction.TypeText -> executeTypeText(action, context)
            is AuraAction.Tap -> executeTap(action, context)
            is AuraAction.AskHermes -> "hermes"
            AuraAction.Unknown -> "error"
        }
    }

    private fun executeOpenApp(action: AuraAction.OpenApp, context: Context, pm: PackageManager, appIndex: AppIndex?): String {
        val target = action.target
        if (target.isBlank()) return "error"
        val pkg = appIndex?.findBestMatch(target)
        if (pkg == null) {
            Log.w("AuraRouter", "OpenApp: no package match for target='$target'")
            return "error"
        }
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Log.w("AuraRouter", "OpenApp: no launch intent for pkg='$pkg'")
            return "error"
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.d("AuraRouter", "OpenApp: starting intent action=${intent.action} pkg=$pkg target='$target'")
        return try {
            context.startActivity(intent)
            "confirm"
        } catch (e: Exception) {
            Log.e("AuraRouter", "OpenApp: startActivity failed for pkg='$pkg'", e)
            "error"
        }
    }

    private fun executeCallContact(action: AuraAction.CallContact, context: Context, pm: PackageManager): String {
        val name = action.name
        if (name.isBlank()) return "error"
        val phoneNumber = findPhoneNumberByName(context, name)
        val intent = Intent(Intent.ACTION_DIAL)
        if (phoneNumber != null) {
            intent.data = Uri.parse("tel:$phoneNumber")
            Log.d("AuraRouter", "CallContact: dialing name='$name' phoneNumber='$phoneNumber'")
        } else {
            Log.w("AuraRouter", "CallContact: no phone number for name='$name', opening dialer without number")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "confirm"
        } catch (e: Exception) {
            Log.e("AuraRouter", "CallContact: startActivity failed name='$name'", e)
            "error"
        }
    }

    private fun executeSendText(action: AuraAction.SendText, context: Context, pm: PackageManager): String {
        val name = action.name
        val body = action.body
        if (name.isBlank()) return "error"
        val rawPhoneNumber = findPhoneNumberByName(context, name)
        val intent = Intent(Intent.ACTION_SENDTO)
        if (rawPhoneNumber != null) {
            val phoneNumber = cleanPhoneNumberForSms(rawPhoneNumber)
            intent.data = Uri.parse("smsto:$phoneNumber")
            Log.d("AuraRouter", "SendText: to name='$name' phoneNumber='$phoneNumber' body='$body'")
        } else {
            intent.data = Uri.parse("smsto:")
            Log.w("AuraRouter", "SendText: no phone number for name='$name', opening SMS composer without recipient")
        }
        intent.putExtra("sms_body", body)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(pm) == null) {
            Log.w("AuraRouter", "SendText: no SMS app found")
            return "error"
        }
        return try {
            context.startActivity(intent)
            "confirm"
        } catch (e: Exception) {
            Log.e("AuraRouter", "SendText: startActivity failed name='$name'", e)
            "error"
        }
    }

    private fun executeWebSearch(action: AuraAction.WebSearch, context: Context, pm: PackageManager): String {
        val query = action.query
        if (query.isBlank()) return "error"
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
        intent.putExtra(SearchManager.QUERY, query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.d("AuraRouter", "WebSearch: query='$query'")
        if (intent.resolveActivity(pm) == null) {
            Log.w("AuraRouter", "WebSearch: no browser/search app found")
            return "error"
        }
        return try {
            context.startActivity(intent)
            "confirm"
        } catch (e: Exception) {
            Log.e("AuraRouter", "WebSearch: startActivity failed query='$query'", e)
            "error"
        }
    }

    private fun executeNavigateWeb(action: AuraAction.NavigateWeb, context: Context, pm: PackageManager): String {
        val url = action.url
        if (url.isBlank()) return "error"
        
        // Handle combined format "appTarget|urlTarget" for "open app and go to url"
        val (appTarget, urlTarget) = if (url.contains("|")) {
            val parts = url.split("|", limit = 2)
            parts[0] to parts[1]
        } else {
            null to url
        }
        
        // If appTarget is specified, we should open that app first, then navigate
        // For now, just open the URL in the default browser
        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.d("AuraRouter", "NavigateWeb: url='$targetUrl'")
        
        if (intent.resolveActivity(pm) == null) {
            Log.w("AuraRouter", "NavigateWeb: no browser found for url='$targetUrl'")
            return "error"
        }
        return try {
            context.startActivity(intent)
            "confirm"
        } catch (e: Exception) {
            Log.e("AuraRouter", "NavigateWeb: startActivity failed url='$targetUrl'", e)
            "error"
        }
    }

    private fun findPhoneNumberByName(context: Context, name: String): String? {
        val normalizedName = name.lowercase().trim()
        if (normalizedName.isBlank()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$normalizedName%")

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) {
                    cursor.getString(numberIndex)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("AuraRouter", "findPhoneNumberByName failed name='$name'", e)
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Cleans a phone number for use with smsto: URI.
     * Removes spaces, dashes, and parentheses only. Preserves leading + and country code.
     * Pure function with no Android dependencies for unit testing.
     */
    fun cleanPhoneNumberForSms(rawNumber: String): String {
        return rawNumber.replace(Regex("[\\s\\-()]"), "")
    }

    private suspend fun executeTakeScreenshot(action: AuraAction.TakeScreenshot, context: Context): String {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            Log.w("AuraRouter", "TakeScreenshot: Shizuku not ready: ${ShizukuManager.state.value}")
            return "error: Shizuku not ready"
        }
        // Use ScreenAgent to take screenshot
        val result = ScreenAgent.runTask(context, "screenshot") { step ->
            Log.d("ScreenAgent", "screenshot step: $step")
        }
        Log.d("AuraRouter", "TakeScreenshot: $result")
        if (result.contains("error") || result.contains("Error")) {
            return "error: $result"
        }
        return "confirm"
    }

    private suspend fun executeTypeText(action: AuraAction.TypeText, context: Context): String {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            Log.w("AuraRouter", "TypeText: Shizuku not ready: ${ShizukuManager.state.value}")
            return "error: Shizuku not ready"
        }
        // Use ScreenAgent to type text
        val result = ScreenAgent.runTask(context, "type ${action.text}") { step ->
            Log.d("ScreenAgent", "type step: $step")
        }
        Log.d("AuraRouter", "TypeText: $result")
        if (result.contains("error") || result.contains("Error")) {
            return "error: $result"
        }
        return "confirm"
    }

    private suspend fun executeTap(action: AuraAction.Tap, context: Context): String {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) {
            Log.w("AuraRouter", "Tap: Shizuku not ready: ${ShizukuManager.state.value}")
            return "error: Shizuku not ready"
        }
        // Use ScreenAgent to tap at coordinates
        val result = ScreenAgent.runTask(context, "tap ${action.x} ${action.y}") { step ->
            Log.d("ScreenAgent", "tap step: $step")
        }
        Log.d("AuraRouter", "Tap: $result")
        if (result.contains("error") || result.contains("Error")) {
            return "error: $result"
        }
        return "confirm"
    }
}