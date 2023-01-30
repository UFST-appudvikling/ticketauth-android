package dk.ufst.ticketauth.automated

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dk.ufst.ticketauth.ActivityProvider
import dk.ufst.ticketauth.AuthEngine
import dk.ufst.ticketauth.AuthResult
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import dk.ufst.ticketauth.authcode.AuthJob
import dk.ufst.ticketauth.log
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionResponse

internal class AutomatedAuthEngine(
    context: Context,
    private val sharedPrefs: SharedPreferences,
    private val baseUrl: String,
    private val clientId: String,
    private val apiKey: String,
    private val provider: String,
    private val onNewAccessToken: OnNewAccessTokenCallback,
): AuthEngine {
    override val roles = mutableListOf<String>()
    var activityProvider: ActivityProvider = null
    private var startForResultAuth: ActivityResultLauncher<Intent?>? = null
    private var startForResultLogout: ActivityResultLauncher<Intent?>? = null
    override var onWakeThreads: ()->Unit = {}
    
    override val jobs: MutableMap<Int, AuthJob> = mutableMapOf()

    override val accessToken: String?
        get() = null
    override val isAuthorized: Boolean
        get() = false

    override fun needsTokenRefresh(): Boolean = false

    init {
        // deserialize authstate if we have one, otherwise start with a fresh
        /*
        sharedPrefs.getString("authState", null)?.let {
            log("Loading existing auth state")
            authState = AuthState.jsonDeserialize(it)
            decodeJWT()
        } ?: run {
            log("Creating a new auth state")
        }

         */
        //log("packageName: ${context.packageName}")
    }

    private fun buildServiceConfig() =
        AuthorizationServiceConfiguration(
            Uri.parse("${baseUrl}${AUTH_PATH}"),  // authorization endpoint
            Uri.parse("${baseUrl}${TOKEN_PATH}"), // token endpoint
            null,
            Uri.parse("${baseUrl}${LOGOUT_PATH}")
        )

    override fun installActivityProvider(activityProvider: ActivityProvider) {
        this.activityProvider = activityProvider
        startForResultAuth =
            activityProvider!!.invoke().registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                processAuthResult(result)
            }
        startForResultLogout =
            activityProvider.invoke().registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                processLogoutResult(result)
            }
    }

    override fun hasActivityProvider() = activityProvider != null

    override fun launchAuthIntent() {
        log("Launching auth intent")

    }

    override fun launchLogoutIntent() {
        log("Launching logout intent")

    }

    override fun performBlockingTokenRefresh(): Boolean {
        return false
    }


    private fun processAuthResult(result: ActivityResult) {
        log("processAuthResult $result")

    }
    
    private fun processLogoutResult(result: ActivityResult) {
        log("processLogoutResult $result")
        if(result.resultCode == Activity.RESULT_CANCELED) {
            wakeThreads(AuthResult.CANCELLED_FLOW)
            return
        }
        result.data?.let { data ->
            val resp = EndSessionResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            if (resp != null) {
                log("Completed logout")
                clear()
                wakeThreads(AuthResult.SUCCESS)
            } else {
                wakeThreads(AuthResult.ERROR)
                log("Logout failed: $ex")
            }
        } ?: run {
            wakeThreads(AuthResult.ERROR)
            log("ActivityResult yielded no data (intent) to process")
        }

    }


    private fun wakeThreads(result: AuthResult) {
        for(job in jobs.values) {
            job.result = result
            job.callback?.invoke(result)
        }
        jobs.entries.removeIf { it.value.noReturn }
        onWakeThreads()
    }


    private fun persistAuthState() {
        //sharedPrefs.edit().putString("authState", authState.jsonSerializeString()).apply()
    }

    private fun onAccessToken() {
        //onNewAccessToken?.invoke(authState.accessToken!!)
    }

    override fun runOnUiThread(block: ()->Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun clear() {
        persistAuthState()
    }

    private fun decodeJWT() {
        /*
        authState.accessToken?.let { accessToken ->
            val split = accessToken.split(".")
            val header = String(Base64.decode(split[0], Base64.URL_SAFE), Charset.forName("UTF-8"))
            val body = String(Base64.decode(split[1], Base64.URL_SAFE), Charset.forName("UTF-8"))
            val headerJson = JSONObject(header)
            val bodyJson = JSONObject(body)

            log("Token Decoded Header: ${headerJson.toString(4)}")
            log("Token Decoded Body: ${bodyJson.toString(4)}")
            roles.clear()
            if (bodyJson.has("realm_access")) {
                val rolesJson = bodyJson.getJSONObject("realm_access").getJSONArray("roles")
                rolesJson.let { ar ->
                    for (i in 0 until ar.length()) {
                        val role = ar.getString(i)
                        roles.add(role)
                    }
                }
            }
        }

         */
    }

    override fun destroy() {
        //authService.dispose()
    }

    companion object {
        private const val AUTH_PATH: String = "/protocol/openid-connect/auth"
        private const val TOKEN_PATH: String = "/protocol/openid-connect/token"
        private const val LOGOUT_PATH: String = "/protocol/openid-connect/logout"
    }
}
