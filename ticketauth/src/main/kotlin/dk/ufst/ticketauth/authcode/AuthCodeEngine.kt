package dk.ufst.ticketauth.authcode

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import dk.ufst.ticketauth.ActivityLauncher
import dk.ufst.ticketauth.AuthEngine
import dk.ufst.ticketauth.AuthResult
import dk.ufst.ticketauth.ErrorCause
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import dk.ufst.ticketauth.log
import dk.ufst.ticketauth.shared.AuthJob
import dk.ufst.ticketauth.shared.MicroHttp
import dk.ufst.ticketauth.shared.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.Charset
import java.time.Instant

/**
 * Micro implementation of OAuth 2.0 Authorization Code Flow
 *
 * Specifications used:
 * https://www.rfc-editor.org/rfc/rfc6749
 * https://openid.net/specs/openid-connect-core-1_0.html
 * https://openid.net/specs/openid-connect-rpinitiated-1_0.html (logout endpoint)
 *
 * TODO check that the state we generate and send at the start of the flow is the same returned by the server as per the standard
 */
internal class AuthCodeEngine(
    private val sharedPrefs: SharedPreferences,
    private val dcsBaseUrl: String,
    private val clientId: String,
    private val scopes: String,
    private val redirectUri: String,
    private val onNewAccessToken: OnNewAccessTokenCallback,
    private val onAuthResultCallback: OnAuthResultCallback,
    private val onLoginResultCallback: OnAuthResultCallback,
    private val onLogoutResultCallback: OnAuthResultCallback,
    private val usePKSE: Boolean,
    allowUnsafeHttps: Boolean
): AuthEngine {
    data class AuthState (
        var accessToken: String,
        var refreshToken: String,
        var idToken: String,
        var accessTokenExpTime: Instant = Instant.EPOCH,
        var refreshTokenExpTime: Instant = Instant.EPOCH,
    )
    override val roles = mutableListOf<String>()
    private var authState: AuthState? = null
    override val jobs: MutableMap<Int, AuthJob> = mutableMapOf()
    override val accessToken: String?
        get() = authState?.accessToken
    private val scope = CoroutineScope(Dispatchers.Default)
    private val redirectUriParser = RedirectUriParser()
    private var codeVerifier = ""

    init {
        MicroHttp.unsafe = allowUnsafeHttps
        // deserialize authstate if we have one, otherwise start with a fresh
        if(sharedPrefs.contains(ACCESS_TOKEN) && sharedPrefs.contains(REFRESH_TOKEN) && sharedPrefs.contains(
                ID_TOKEN)) {
            authState = AuthState(
                accessToken = sharedPrefs.getString(ACCESS_TOKEN, null)!!,
                refreshToken = sharedPrefs.getString(REFRESH_TOKEN, null)!!,
                idToken = sharedPrefs.getString(ID_TOKEN, null)!!,
            ).also {
                decodeAccessToken(it)
                decodeRefreshToken(it)
            }
        } else {
            log("Creating a new auth state")
        }
    }

    override fun launchAuthIntent() {
        val authUri = Uri.parse("${dcsBaseUrl}${AUTH_PATH}").buildUpon()
            .appendQueryParameter(RESPONSE_TYPE, CODE)
            .appendQueryParameter(CLIENT_ID, clientId)
            .appendQueryParameter(REDIRECT_URI, redirectUri)
            .appendQueryParameter(SCOPE, scopes)
            .appendQueryParameter(STATE, Util.generateRandomState())
            .appendQueryParameter(NONCE, Util.generateRandomState())
            .apply {
                if(usePKSE) {
                    codeVerifier = PkceUtil.generateRandomCodeVerifier()
                    appendQueryParameter(CODE_CHALLENGE, PkceUtil.deriveCodeVerifierChallenge(codeVerifier))
                    appendQueryParameter(CODE_CHALLENGE_METHOD, PkceUtil.codeVerifierChallengeMethod)
                }
            }
            .build()

        log("authUri: $authUri")

        val intent = Intent(authActivityLauncher!!.activity(), BrowserManagementActivity::class.java).apply {
            data = authUri
        }
        authActivityLauncher!!.launch(intent, ::onAuthIntentResult)
    }

    private fun onAuthIntentResult(result: ActivityResult) {
        log("onAuthIntentResult $result")
        if(result.resultCode == Activity.RESULT_CANCELED) {
            if(result.data != null) {
                val error = AuthorizationError.fromIntent(result.data!!)
                if(error.error == BrowserManagementActivity.USER_CANCEL) {
                    log("User cancelled authorization flow")
                    wakeThreads(AuthResult.CANCELLED_FLOW, isLogin = true)
                } else {
                    log("Received error authorization flow: $error")
                    wakeThreads(AuthResult.ERROR(ErrorCause.AuthorizationFlow(error)), isLogin = true)
                }
                return
            }
        } else if(result.resultCode == Activity.RESULT_OK) {
            val responseUri = result.data?.data
            if(responseUri != null) {
                scope.launch {
                    val parsedResult = redirectUriParser.parse(responseUri)
                    log("responseUri parsed result: $parsedResult")
                    when(parsedResult) {
                        is RedirectUriParser.ParsedResult.Error -> {
                            wakeThreads(AuthResult.ERROR(ErrorCause.ParseRedirectUri(parsedResult)), isLogin = true)
                        }
                        is RedirectUriParser.ParsedResult.Success -> exchangeCodeForToken(parsedResult)
                    }
                }
                return
            }
        }
        wakeThreads(AuthResult.ERROR(ErrorCause.UnknownAuthIntentResult(result)), isLogin = true)
    }

    private fun exchangeCodeForToken(result: RedirectUriParser.ParsedResult.Success) {
        val params = mutableMapOf(
            GRANT_TYPE to AUTHORIZATION_CODE,
            CODE to result.code,
            CLIENT_ID to clientId,
            REDIRECT_URI to redirectUri,
        )
        if(usePKSE) {
            params[CODE_VERIFIER] = codeVerifier
        }
        try {
            log("Sending authorization request:\n${params}")
            val jsonResponse = MicroHttp.postFormUrlEncoded("${dcsBaseUrl}${TOKEN_PATH}", params)
            processTokenResponse(jsonResponse)
            wakeThreads(AuthResult.SUCCESS, isLogin = true)
        } catch (t : Throwable) {
            log("Token endpoint called failed with exception: ${t.message}")
            t.printStackTrace()
            wakeThreads(AuthResult.ERROR(ErrorCause.GetToken(throwable = t)), isLogin = true)
        }
    }

    private fun processTokenResponse(tokenResponse: JSONObject) {
        log("processTokenResponse ${tokenResponse.toString(2)}")
        val accessToken = tokenResponse.getString("access_token")
        val refreshToken = tokenResponse.getString("refresh_token")
        val idToken = tokenResponse.getString("id_token")
        authState = AuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken
        ).also {
            decodeAccessToken(it)
            decodeRefreshToken(it)
        }

        log("AccessToken expiration: ${authState?.accessTokenExpTime}")
        log("RefreshToken expiration: ${authState?.refreshTokenExpTime}")
        persistAuthState()
        onAccessToken()
    }

    private fun onAccessToken() {
        runOnUiThread {
            onNewAccessToken?.invoke(authState!!.accessToken)
        }
    }

    override fun performBlockingTokenRefresh(): Boolean {
        authState?.let { state ->
            // check if refresh token have expired, if so give up
            val deadline = Instant.now().minusMillis(6000)
            if(state.refreshTokenExpTime.isBefore(deadline)) {
                return false
            }
            return refreshAccessToken(state)
        }
        return false
    }

    private fun refreshAccessToken(state: AuthState): Boolean {
        val params = mapOf(
            CLIENT_ID to clientId,
            GRANT_TYPE to REFRESH_TOKEN,
            REFRESH_TOKEN to state.refreshToken,
            SCOPE to scopes,
        )
        try {
            log("Sending token refresh request:\n${params}")
            val jsonResponse = MicroHttp.postFormUrlEncoded("${dcsBaseUrl}${TOKEN_PATH}", params)
            processTokenResponse(jsonResponse)
            return true
        } catch (t : Throwable) {
            log("Token endpoint called failed with exception: ${t.message}")
        }
        return false
    }

    override fun launchLogoutIntent() {
        if(authState == null) {
            log("Cannot call logout endpoint with no id token")
            wakeThreads(AuthResult.ERROR(ErrorCause.MissingIdToken), isLogin = false)
            return
        }
        val logoutUri = Uri.parse("${dcsBaseUrl}${LOGOUT_PATH}").buildUpon()
            .appendQueryParameter(CLIENT_ID, clientId)
            .appendQueryParameter(ID_TOKEN_HINT, authState!!.idToken)
            .appendQueryParameter(POST_LOGOUT_REDIRECT_URI, redirectUri)
            .appendQueryParameter(STATE, Util.generateRandomState())
            .build()

        log("logoutUri: $logoutUri")

        val intent = Intent(logoutActivityLauncher!!.activity(), BrowserManagementActivity::class.java).apply {
            data = logoutUri
        }
        logoutActivityLauncher!!.launch(intent, ::onLogoutIntentResult)
    }

    private fun onLogoutIntentResult(result: ActivityResult) {
        log("onLogoutIntentResult $result")
        if(result.resultCode == Activity.RESULT_CANCELED) {
            if(result.data != null) {
                val error = AuthorizationError.fromIntent(result.data!!)
                if(error.error == BrowserManagementActivity.USER_CANCEL) {
                    log("User cancelled authorization flow")
                    wakeThreads(AuthResult.CANCELLED_FLOW, isLogin = false)
                } else {
                    log("Received error authorization flow: $error")
                    wakeThreads(AuthResult.ERROR(ErrorCause.AuthorizationFlow(error)), isLogin = false)
                }
                return
            }
        } else if(result.resultCode == Activity.RESULT_OK) {
            val responseUri = result.data?.data
            if(responseUri != null) {
                if(responseUri.toString().startsWith(redirectUri)) {
                    clear()
                    roles.clear()
                    wakeThreads(AuthResult.SUCCESS, isLogin = false)
                } else {
                    log("wrong uri, expected: $redirectUri")
                    wakeThreads(
                        AuthResult.ERROR(ErrorCause.UnknownRedirectUri(
                            expectedRedirectUri = redirectUri,
                            actualRedirectUri = responseUri.toString(),
                        )), isLogin = false
                    )
                }
                return
            }
        }
        wakeThreads(AuthResult.ERROR(ErrorCause.UnknownAuthIntentResult(result)), isLogin = false)
    }

    override fun needsTokenRefresh(): Boolean {
        authState?.let {
            return hasExpired(it.accessTokenExpTime)
        }
        return true
    }

    override val isAuthorized: Boolean
        get() {
            authState?.let { state ->
                // check if refresh token have expired
                if(hasExpired(state.refreshTokenExpTime)) {
                    return false
                }
                return true
            }
            return false
        }

    override fun clear() {
        authState = null
        persistAuthState()
    }

    override var onWakeThreads: ()->Unit = {}

    override fun runOnUiThread(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun destroy() {
        scope.cancel()
    }

    private fun wakeThreads(result: AuthResult, isLogin: Boolean) {
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
        if(isLogin) {
            onLoginResultCallback?.let { callback ->
                runOnUiThread {
                    callback.invoke(result)
                }
            }
        } else {
            onLogoutResultCallback?.let { callback ->
                runOnUiThread {
                    callback.invoke(result)
                }
            }
        }
    }

    private fun hasExpired(i: Instant): Boolean {
        // 6 seconds grace period to safeguard against clock inaccuracies (borrowed from AppAuth)
        val deadline = Instant.now().minusMillis(GRACE_PERIOD)
        return i.isBefore(deadline)
    }

    private fun decodeAccessToken(state: AuthState) {
        val split = state.accessToken.split(".")
        val header = String(Base64.decode(split[0], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val body = String(Base64.decode(split[1], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val headerJson = JSONObject(header)
        val bodyJson = JSONObject(body)

        log("AccessToken Decoded Header: ${headerJson.toString(4)}")
        log("AccessToken Decoded Body: ${bodyJson.toString(4)}")
        val exp : Long = bodyJson.getLong("exp")
        state.accessTokenExpTime = Instant.ofEpochSecond(exp)

        roles.clear()
        if (bodyJson.has(REALM_ACCESS)) {
            val realmAccess = bodyJson.getJSONObject(REALM_ACCESS)
            if(realmAccess.has(ROLES)) {
                val rolesJson = realmAccess.getJSONArray(ROLES)
                rolesJson.let { ar ->
                    for (i in 0 until ar.length()) {
                        val role = ar.getString(i)
                        roles.add(role)
                    }
                }
            }
        }
    }

    private fun decodeRefreshToken(state: AuthState) {
        val split = state.refreshToken.split(".")
        val header = String(Base64.decode(split[0], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val body = String(Base64.decode(split[1], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val headerJson = JSONObject(header)
        val bodyJson = JSONObject(body)
        log("RefreshToken Decoded Header: ${headerJson.toString(4)}")
        log("RefreshToken Decoded Body: ${bodyJson.toString(4)}")
        val exp : Long = bodyJson.getLong(EXP)
        state.refreshTokenExpTime = Instant.ofEpochSecond(exp)
    }

    private fun persistAuthState() {
        authState?.let {
            sharedPrefs.edit()
                .putString(ACCESS_TOKEN, it.accessToken)
                .putString(REFRESH_TOKEN, it.refreshToken)
                .putString(ID_TOKEN, it.idToken)
                .apply()
        } ?: run {
            sharedPrefs.edit()
                .remove(ACCESS_TOKEN)
                .remove(REFRESH_TOKEN)
                .remove(ID_TOKEN)
                .apply()
        }
    }

    override val hasRegisteredActivityLaunchers: Boolean
        get() = authActivityLauncher != null && logoutActivityLauncher != null

    companion object {
        private const val AUTH_PATH: String = "/protocol/openid-connect/auth"
        private const val TOKEN_PATH: String = "/protocol/openid-connect/token"
        private const val LOGOUT_PATH: String = "/protocol/openid-connect/logout"
        private const val AUTHORIZATION_CODE = "authorization_code"
        private const val RESPONSE_TYPE = "response_type"
        private const val CODE = "code"
        private const val CLIENT_ID = "client_id"
        private const val REDIRECT_URI = "redirect_uri"
        private const val SCOPE = "scope"
        private const val STATE = "state"
        private const val NONCE = "nonce"
        private const val GRANT_TYPE = "grant_type"
        private const val ID_TOKEN_HINT = "id_token_hint"
        private const val POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri"
        private const val GRACE_PERIOD = 6000L
        private const val ACCESS_TOKEN = "access_token"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val ID_TOKEN = "id_token"
        private const val ROLES = "roles"
        private const val REALM_ACCESS = "realm_access"
        private const val EXP = "exp"
        private const val CODE_VERIFIER = "code_verifier"
        private const val CODE_CHALLENGE = "code_challenge"
        private const val CODE_CHALLENGE_METHOD = "code_challenge_method"

        private var authActivityLauncher: ActivityLauncher? = null
        private var logoutActivityLauncher: ActivityLauncher? = null

        fun registerActivityLaunchers(activity: ComponentActivity) {
            authActivityLauncher = ActivityLauncher(activity)
            logoutActivityLauncher = ActivityLauncher(activity)
        }
    }
}
