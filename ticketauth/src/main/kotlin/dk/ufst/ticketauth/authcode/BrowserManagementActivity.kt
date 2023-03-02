package dk.ufst.ticketauth.authcode

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import dk.ufst.ticketauth.log

class BrowserManagementActivity: ComponentActivity() {
    private var browserStarted: Boolean = false
    private var browserUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // read saved state if any
        savedInstanceState?.let {
            log("onCreate found saved state")
            extractState(it)
            if(browserUri == null) {
                sendErrorResult(TICKET_AUTH_ERROR, "saved state contains no authUri")
                finish()
            }
        }
        if(savedInstanceState == null) {
            log("found no saved instance state, creating state from intent")
            if(intent?.data == null) {
                sendErrorResult("ticketauth_error", "No authUri found in intent")
                finish()
                return
            }
            browserUri = intent?.data
            intent = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(BROWSER_STARTED_KEY, browserStarted)
        outState.putParcelable(BROWSER_URI_KEY, browserUri)
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onResume() {
        super.onResume()
        /*
        * If this is the first run of the activity, start the authorization intent.
        * Note that we do not finish the activity at this point, in order to remain on the back
        * stack underneath the authorization activity.
        */
        if(!browserStarted) {
            log("Starting browser..")
            startAuthorization()
            return
        }

        /*
        * On a subsequent run, it must be determined whether we have returned to this activity
        * due to an OAuth2 redirect, or the user canceling the authorization flow. This can
        * be done by checking whether a response URI is available, which would be provided by
        * RedirectUriReceiverActivity. If it is not, we have returned here due to the user
        * pressing the back button, or the authorization activity finishing without
        * RedirectUriReceiverActivity having been invoked - this can occur when the user presses
        * the back button, or closes the browser tab.
        */
        val responseUri = intent?.data
        log("responseUri = ${responseUri.toString()}")
        if(responseUri != null) {
            authorizationComplete(responseUri)
        } else {
            sendErrorResult(USER_CANCEL, "User cancelled browser tab")
        }

        finish()
    }

    // TODO support devices where custom tabs are not available (send a regular view intent)
    private fun startAuthorization() {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .apply {
                intent.data = browserUri
            }
        try {
            startActivity(customTabsIntent.intent)
            browserStarted = true
        } catch (t: ActivityNotFoundException) {
            sendErrorResult(TICKET_AUTH_ERROR, "Custom tabs not supported and system browser workaround not yet implemented")
            finish()
        }
    }

    private fun authorizationComplete(responseUri: Uri) {
        setResult(Activity.RESULT_OK, Intent().apply {
            data = responseUri
        })
    }

    private fun sendErrorResult(error: String, description: String) {
        log("sendErrorResult $error")
        setResult(Activity.RESULT_CANCELED, AuthorizationError(error, description).toIntent())
    }

    private fun extractState(state: Bundle) {
        browserStarted = state.getBoolean(BROWSER_STARTED_KEY, false)
        browserUri = state.getParcelable(BROWSER_URI_KEY)
    }

    companion object {
        const val BROWSER_URI_KEY = "browserUri"
        const val BROWSER_STARTED_KEY = "browserStarted"
        const val TICKET_AUTH_ERROR = "ticketauth_error"
        const val USER_CANCEL = "user_cancel"
    }
}