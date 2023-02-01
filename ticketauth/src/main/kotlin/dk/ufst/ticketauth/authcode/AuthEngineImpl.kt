package dk.ufst.ticketauth.authcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dk.ufst.ticketauth.ActivityProvider
import dk.ufst.ticketauth.AuthEngine
import dk.ufst.ticketauth.AuthResult
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import dk.ufst.ticketauth.log
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

internal class AuthEngineImpl(
    context: Context,
    private val sharedPrefs: SharedPreferences,
    private val dcsBaseUrl: String,
    private val clientId: String,
    private val scopes: String,
    private val redirectUri: String,
    private val onNewAccessToken: OnNewAccessTokenCallback,
    private val onAuthResultCallback: OnAuthResultCallback,
): AuthEngine {
    private var authService: AuthorizationService = AuthorizationService(context)
    private var serviceConfig : AuthorizationServiceConfiguration
    var authState: AuthState = AuthState()

    override val roles = mutableListOf<String>()

    private val refreshLock = ReentrantLock()
    private val refreshCondition: Condition = refreshLock.newCondition()
    var activityProvider: ActivityProvider = null
    private var startForResultAuth: ActivityResultLauncher<Intent?>? = null
    private var startForResultLogout: ActivityResultLauncher<Intent?>? = null
    override var onWakeThreads: ()->Unit = {}
    
    override val jobs: MutableMap<Int, AuthJob> = mutableMapOf()

    override val accessToken: String?
        get() = authState.accessToken

    override val isAuthorized: Boolean
        get() = authState.isAuthorized
    
    override fun needsTokenRefresh(): Boolean = authState.needsTokenRefresh

    init {
        serviceConfig = buildServiceConfig()
        // deserialize authstate if we have one, otherwise start with a fresh
        sharedPrefs.getString("authState", null)?.let {
            log("Loading existing auth state")
            authState = AuthState.jsonDeserialize(it)
            decodeJWT()
        } ?: run {
            log("Creating a new auth state")
        }
        //log("packageName: ${context.packageName}")
    }

    private fun buildServiceConfig() =
        AuthorizationServiceConfiguration(
            Uri.parse("${dcsBaseUrl}$AUTH_PATH"),  // authorization endpoint
            Uri.parse("${dcsBaseUrl}$TOKEN_PATH"), // token endpoint
            null,
            Uri.parse("${dcsBaseUrl}$LOGOUT_PATH")
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
        // TODO this should be removed but properly tested. since config is created in init
        serviceConfig = buildServiceConfig()
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig, clientId,
            ResponseTypeValues.CODE, Uri.parse(redirectUri)
        )
            .setScope(scopes)
            .build()

        val authIntent: Intent = authService.getAuthorizationRequestIntent(authRequest)
        
        try {
            startForResultAuth!!.launch(authIntent)
        } catch (t : Throwable) {
            log("Cannot launch auth intent due to exception: ${t.message}")
            wakeThreads(AuthResult.ERROR)
        }
    }

    override fun launchLogoutIntent() {
        log("Launching logout intent")
        authState.idToken?.let {
            val endSessionRequest = EndSessionRequest.Builder(serviceConfig)
                .setIdTokenHint(it)
                .setPostLogoutRedirectUri(Uri.parse(redirectUri))
                .build()
            val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
            try {
                startForResultLogout!!.launch(endSessionIntent)
            } catch (t : Throwable) {
                log("Cannot launch logout intent due to exception: ${t.message}")
                wakeThreads(AuthResult.ERROR)
            }
        } ?: run {
            log("Cannot launch logout intent because we have no idToken")
            wakeThreads(AuthResult.ERROR)
        }
    }


    private fun processAuthResult(result: ActivityResult) {
        log("processAuthResult $result")
        if(result.resultCode == Activity.RESULT_CANCELED) {
            log("Auth flow was cancelled by user")
            wakeThreads(AuthResult.CANCELLED_FLOW)
        } else {
            result.data?.let { data ->
                val resp = AuthorizationResponse.fromIntent(data)
                val ex = AuthorizationException.fromIntent(data)
                authState.update(resp, ex)
                persistAuthState()
                if (resp != null) {
                    // authorization completed
                    performTokenRequest(resp)
                    log("Got auth code: ${resp.authorizationCode}")
                } else {
                    log("Auth failed: $ex")
                    // user cancelled login flow (in flow cancel option), wake threads so they can return error
                    wakeThreads(AuthResult.ERROR)
                }
            } ?: run {
                log("ActivityResult yielded no data (intent) to process")
                wakeThreads(AuthResult.ERROR)
            }
        }
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

    private fun performTokenRequest(authResp: AuthorizationResponse) {
        log("performTokenRequest")
        authService.performTokenRequest(authResp.createTokenExchangeRequest()) { resp, ex ->
            authState.update(resp, ex)
            persistAuthState()
            if (resp != null) {
                // exchange succeeded
                log("Got access token: ${resp.accessToken}")
                decodeJWT()
                onAccessToken()
                wakeThreads(AuthResult.SUCCESS)
            } else {
                log("Token exchange failed: $ex")
                wakeThreads(AuthResult.ERROR)
            }
        }
    }

    private fun wakeThreads(result: AuthResult) {
        // notify waiting jobs and callbacks
        for(job in jobs.values) {
            job.result = result
            job.callback?.let {
                runOnUiThread {
                    it.invoke(result)
                }
            }
        }
        jobs.entries.removeIf { it.value.noReturn }
        onWakeThreads()
        // call auth result callback if registered
        onAuthResultCallback?.let { callback ->
            runOnUiThread {
                callback.invoke(result)
            }
        }
    }

    /**
    Use a reentrant lock to wait for the callback from createTokenRefreshRequest blocking
    the function from returning until completion. This is necessary because that appauth
    library doesn't provide a synchronous way of making the call
     */
    override fun performBlockingTokenRefresh(): Boolean {
        var success = false
        refreshLock.lock()
        try {
            val req = authState.createTokenRefreshRequest()
            authService.performTokenRequest(req) { resp, ex ->
                authState.update(resp, ex)
                persistAuthState()
                if (resp != null) {
                    success = true
                    decodeJWT()
                    onAccessToken()
                } else {
                    log("Token refresh exception $ex")
                }
                refreshLock.lock()
                try {
                    refreshCondition.signal()
                } finally {
                    refreshLock.unlock()
                }
            }
            refreshCondition.await()
        } catch (t: Throwable) {
            Log.e("TicketAuth", "Token refresh failed, no refresh token", t)
            return false
        } finally {
            refreshLock.unlock()
        }
        return success
    }

    private fun persistAuthState() {
        sharedPrefs.edit().putString("authState", authState.jsonSerializeString()).apply()
    }

    private fun onAccessToken() {
        onNewAccessToken?.invoke(authState.accessToken!!)
    }

    override fun runOnUiThread(block: ()->Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun clear() {
        authState = AuthState()
        persistAuthState()
    }

    private fun decodeJWT() {
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
    }

    override fun destroy() {
        authService.dispose()
    }

    companion object {
        private const val AUTH_PATH: String = "/protocol/openid-connect/auth"
        private const val TOKEN_PATH: String = "/protocol/openid-connect/token"
        private const val LOGOUT_PATH: String = "/protocol/openid-connect/logout"
    }
}
