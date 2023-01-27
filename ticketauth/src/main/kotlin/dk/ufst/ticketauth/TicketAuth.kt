@file:Suppress("unused")

package dk.ufst.ticketauth

import android.util.Log
import androidx.activity.ComponentActivity
import dk.ufst.ticketauth.authcode.AuthCodeConfig
import dk.ufst.ticketauth.authcode.AuthEngineImpl
import dk.ufst.ticketauth.authcode.AuthenticatorImpl
import dk.ufst.ticketauth.automated.AutomatedAuthConfig
import org.jetbrains.annotations.NonNls

typealias ActivityProvider = (()-> ComponentActivity)?

object TicketAuth {
    private var engine: AuthEngineImpl? = null
    private var debug: Boolean = false
    private var authenticator: Authenticator? = null

    fun setup(config: AuthCodeConfig) {
        debug = config.debug
        engine?.destroy()
        engine = AuthEngineImpl(
            sharedPrefs = config.sharedPrefs,
            context = config.context,
            dcsBaseUrl = config.dcsBaseUrl,
            clientId = config.clientId,
            scopes = config.scopes,
            redirectUri = config.redirectUri,
            onNewAccessToken = config.onNewAccessTokenCallback,
            onAuthResultCallback = config.onAuthResultCallback
        )
        authenticator = AuthenticatorImpl(engine!!)
    }

    fun setup(config: AutomatedAuthConfig) {
        debug = true
        engine?.destroy()

    }

    fun installActivityProvider(activityProvider: ActivityProvider) {
        checkSetup()
        engine?.installActivityProvider(activityProvider)
    }

    fun authenticator(): Authenticator {
        checkInit()
        return authenticator!!
    }

    val accessToken: String?
        get()  {
            checkInit()
            return engine?.authState?.accessToken
        }

    val isAuthorized: Boolean
        get() {
            checkInit()
            return engine!!.authState.isAuthorized
        }

    private fun checkInit() {
        checkSetup()
        engine?.let {
            if(it.activityProvider == null) {
                throw(RuntimeException("You must install an activity provider"))
            }
        }
    }

    private fun checkSetup() {
        if(engine == null) {
            throw(RuntimeException("TicketAuth has not been initialized. Please call setup() before calling any other functions"))
        }
    }

    internal fun log(@NonNls message: String) {
        if(debug) {
            Log.d(LOG_TAG, message)
        }
    }

    private const val LOG_TAG = "TicketAuth"
}
