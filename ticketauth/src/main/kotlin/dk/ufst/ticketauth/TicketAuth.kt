package dk.ufst.ticketauth

import android.util.Log
import androidx.activity.ComponentActivity
import org.jetbrains.annotations.NonNls

typealias ActivityProvider = (()-> ComponentActivity)?

object TicketAuth {
    private var engine: AuthEngineImpl? = null
    private var debug: Boolean = false
    private val authenticator: Authenticator by lazy {
        AuthenticatorImpl(engine!!)
    }

    fun setup(config: TicketAuthConfig) {
        if(engine != null) {
            throw(RuntimeException("TicketAuth already initialized"))
        }
        debug = config.debug
        engine = AuthEngineImpl(
            sharedPrefs = config.sharedPrefs,
            context = config.context,
            dcsBaseUrl = config.dcsBaseUrl,
            clientId = config.clientId,
            scopes = config.scopes
        )
    }

    fun installActivityProvider(activityProvider: ActivityProvider) {
        checkSetup()
        engine?.installActivityProvider(activityProvider)
    }

    fun authenticator(): Authenticator {
        checkInit()
        return authenticator
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
