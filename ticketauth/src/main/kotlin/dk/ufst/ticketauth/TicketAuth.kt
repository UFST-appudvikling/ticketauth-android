@file:Suppress("unused")

package dk.ufst.ticketauth

import android.util.Log
import androidx.activity.ComponentActivity
import dk.ufst.ticketauth.authcode.AuthCodeConfig
import dk.ufst.ticketauth.authcode.AuthEngineImpl
import dk.ufst.ticketauth.authcode.AuthenticatorImpl
import dk.ufst.ticketauth.automated.AutomatedAuthConfig
import dk.ufst.ticketauth.automated.AutomatedAuthEngine
import org.jetbrains.annotations.NonNls

object TicketAuth {
    private var engine: AuthEngine? = null
    private var debug: Boolean = false
    private var authenticator: Authenticator? = null

    fun setup(config: AuthCodeConfig) {
        debug = config.debug
        engine?.destroy()
        engine = AuthEngineImpl(
            context = config.context,
            sharedPrefs = config.sharedPrefs,
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
        engine = AutomatedAuthEngine(
            sharedPrefs = config.sharedPrefs,
            onNewAccessToken = config.onNewAccessTokenCallback,
            onAuthResultCallback = config.onAuthResultCallback,
            userConfig = config.userConfig
        )
        authenticator = AuthenticatorImpl(engine!!)
    }

    fun setHostActivity(activity: ComponentActivity) {
        AutomatedAuthEngine.registerActivityLaunchers(activity)
        AuthEngineImpl.registerActivityLaunchers(activity)
    }

    fun authenticator(): Authenticator {
        checkInit()
        return authenticator!!
    }

    val accessToken: String?
        get()  {
            checkInit()
            return engine?.accessToken
        }

    val isAuthorized: Boolean
        get() {
            checkInit()
            return engine!!.isAuthorized
        }

    private fun checkInit() {
        checkSetup()
        if(!engine!!.hasRegisteredActivityLaunchers) {
            throw(RuntimeException("You must call setHostActivity first"))
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
