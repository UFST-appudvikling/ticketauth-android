@file:Suppress("unused")

package dk.ufst.ticketauth

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import dk.ufst.ticketauth.authcode.AuthCodeConfig
import dk.ufst.ticketauth.authcode.AuthCodeEngine
import dk.ufst.ticketauth.shared.AuthenticatorImpl
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
        val sharedPrefs = hostApplicationContext!!.getSharedPreferences("ticketauth", Context.MODE_PRIVATE)
        engine = AuthCodeEngine(
            sharedPrefs = sharedPrefs,
            dcsBaseUrl = config.dcsBaseUrl,
            clientId = config.clientId,
            scopes = config.scopes,
            redirectUri = config.redirectUri,
            onNewAccessToken = config.onNewAccessTokenCallback,
            onAuthResultCallback = config.onAuthResultCallback,
            onLoginResultCallback = config.onLoginResultCallback,
            onLogoutResultCallback = config.onLogoutResultCallback,
            usePKSE = config.usePKSE,
            allowUnsafeHttps = config.allowUnsafeHttps
        )
        authenticator = AuthenticatorImpl(engine!!)
    }

    fun setup(config: AutomatedAuthConfig) {
        debug = true
        engine?.destroy()
        val sharedPrefs = hostApplicationContext!!.getSharedPreferences("ticketauth", Context.MODE_PRIVATE)
        engine = AutomatedAuthEngine(
            sharedPrefs = sharedPrefs,
            onNewAccessToken = config.onNewAccessTokenCallback,
            onAuthResultCallback = config.onAuthResultCallback,
            userConfig = config.userConfig,
            allowUnsafeHttps = config.allowUnsafeHttps
        )
        authenticator = AuthenticatorImpl(engine!!)
    }

    fun setHostActivity(activity: ComponentActivity) {
        AutomatedAuthEngine.registerActivityLaunchers(activity)
        AuthCodeEngine.registerActivityLaunchers(activity)
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
        if(hostApplicationContext == null) {
            throw(RuntimeException("TicketAuth has not been initialized properly, unable to get host application context"))
        }
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
