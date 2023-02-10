package dk.ufst.ticketauth.automated

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import dk.ufst.ticketauth.ActivityLauncher
import dk.ufst.ticketauth.AuthEngine
import dk.ufst.ticketauth.AuthResult
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import dk.ufst.ticketauth.authcode.AuthJob
import dk.ufst.ticketauth.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal class AutomatedAuthEngine(
    private val sharedPrefs: SharedPreferences,
    private val onNewAccessToken: OnNewAccessTokenCallback,
    private val onAuthResultCallback: OnAuthResultCallback,
    private val userConfig: JSONObject
): AuthEngine {
    private var tokenUrl: String = ""
    data class AuthState (
        var accessToken: String,
        var tokenExpTime : Instant
    )
    override val roles = mutableListOf<String>()

    private var authState: AuthState? = null

    override var onWakeThreads: ()->Unit = {}
    override val jobs: MutableMap<Int, AuthJob> = mutableMapOf()
    override val accessToken: String?
        get() = authState?.accessToken
    override val isAuthorized: Boolean
        get() = !needsTokenRefresh()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var users: List<AutomatedUser> = emptyList()

    init {
        parseUserConfig()
        // deserialize authstate if we have one, otherwise start with a fresh
        sharedPrefs.getString("accessToken", null)?.let {
            log("Loading existing auth state")
            decodeJWT(it)
        } ?: run {
            log("Creating a new auth state")
        }
    }

    private fun unsafeHttps(https: HttpsURLConnection) {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts: Array<TrustManager> = arrayOf(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                    // Having some code in this function truly suppresses the TrustAllX509TrustManager
                    // warning. Using SuppressLint/Suppress only suppresses the warning
                    // in Android Studio, but is still warned by the "gradlew lint" command.
                    Any()
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                    // Having some code in this function truly suppresses the TrustAllX509TrustManager
                    // warning. Using SuppressLint/Suppress only suppresses the warning
                    // in Android Studio, but is still warned by the "gradlew lint" command.
                    Any()
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )

        // Install the all-trusting trust manager
        val sslContext: SSLContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
        https.sslSocketFactory = sslSocketFactory
    }

    private fun postJson(url: String, json: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            unsafeHttps(this)
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        connection.outputStream.use { os ->
            val input = json.toString().toByteArray(charset("utf-8"))
            os.write(input, 0, input.size)
        }
        BufferedReader(InputStreamReader(connection.inputStream, "utf-8")).use { br ->
            val response = StringBuilder()
            var responseLine: String?
            while (br.readLine().also { responseLine = it } != null) {
                response.append(responseLine!!.trim())
            }
            return JSONObject(response.toString())
        }
    }

    override fun launchAuthIntent() {
        // launch user selector
        try {
            log("Launching select user activity")
            val intent = Intent(selectUserLauncher!!.activity(), SelectUserActivity::class.java)
            val jsonUsers = JSONArray()
            users.forEach { user ->
                JSONObject().apply {
                    put("title", user.title)
                }.also {
                    jsonUsers.put(it)
                }
            }
            intent.putExtra("users", jsonUsers.toString())
            selectUserLauncher!!.launch(intent) {
                processSelectUserResult(it)
            }
        } catch (t : Throwable) {
            log("Cannot launch select user activity due to exception: ${t.message}")
            t.printStackTrace()
            wakeThreads(AuthResult.ERROR)
        }
    }

    override fun launchLogoutIntent() {
        log("Logout is not supported with automated login, clearing auth state to emulate logout")
        authState = null
        persistAuthState()
        roles.clear()
        wakeThreads(AuthResult.SUCCESS)
    }

    private fun processSelectUserResult(result: ActivityResult) {
        log("processAuthResult $result")
        if(result.resultCode == Activity.RESULT_CANCELED) {
            log("Select user activity was cancelled by user")
            wakeThreads(AuthResult.CANCELLED_FLOW)
        } else {
            result.data?.let { data ->
                val index = data.getIntExtra("index", -1)
                log("got index: $index from user picker, user ${users[index]}")
                loginUser(users[index])
            } ?: run {
                log("ActivityResult yielded no data (intent) to process")
                wakeThreads(AuthResult.ERROR)
            }
        }
    }

    private fun loginUser(user: AutomatedUser) {
        val jsonParams = JSONObject().apply {
            put("api-key", user.apiKey)
            put("client_id", user.clientId)
            put("azureOrDcs", user.provider.jsonValue)
            put("nonce", user.nonce)
        }
        when(user.provider) {
            AutomatedAuthConfig.Provider.Azure -> {
                jsonParams.put("azure", user.providerData)
                jsonParams.put("authorizations", user.authorizations)
            }
            AutomatedAuthConfig.Provider.Dcs -> {
                for (key in user.providerData.keys()) {
                    jsonParams.put(key, user.providerData.get(key))
                }
                jsonParams.put("authorizations", user.authorizations)
            }
        }
        scope.launch {
            try {
                log("Token request:\n${jsonParams.toString(2)}")
                val jsonResponse = postJson(tokenUrl, jsonParams)
                processTokenResponse(jsonResponse)
            } catch (t : Throwable) {
                log("Token endpoint called failed with exception: ${t.message}")
                wakeThreads(AuthResult.ERROR)
                t.printStackTrace()
            }
        }
    }

    private fun processTokenResponse(tokenResponse: JSONObject) {
        log("processTokenResponse ${tokenResponse.toString(4)}")
        decodeJWT(tokenResponse.getString("access_token"))
        log("Token expiration: ${authState?.tokenExpTime}")
        persistAuthState()
        onAccessToken()
        wakeThreads(AuthResult.SUCCESS)
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

    // Token refresh is not supported with automated login
    override fun performBlockingTokenRefresh(): Boolean {
        return false
    }

    private fun persistAuthState() {
        authState?.let {
            sharedPrefs.edit().putString("accessToken", it.accessToken).apply()
        } ?: run {
            sharedPrefs.edit().remove("accessToken").apply()
        }
    }

    private fun onAccessToken() {
        onNewAccessToken?.invoke(authState!!.accessToken)
    }

    override fun runOnUiThread(block: ()->Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun clear() {
        authState = null
        persistAuthState()
    }

    private fun decodeJWT(accessToken : String) {
        val split = accessToken.split(".")
        val header = String(Base64.decode(split[0], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val body = String(Base64.decode(split[1], Base64.URL_SAFE), Charset.forName("UTF-8"))
        val headerJson = JSONObject(header)
        val bodyJson = JSONObject(body)

        log("Token Decoded Header: ${headerJson.toString(4)}")
        log("Token Decoded Body: ${bodyJson.toString(4)}")
        val exp : Long = bodyJson.getLong("exp")

        authState = AuthState(accessToken = accessToken, tokenExpTime = Instant.ofEpochSecond(exp))

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

    override fun needsTokenRefresh(): Boolean {
        authState?.let {
            // 6 seconds grace period to safeguard against clock inaccuracies
            val deadline = Instant.now().minusMillis(6000)
            return it.tokenExpTime.isBefore(deadline)
        }
        return true
    }

    override fun destroy() {}

    private fun parseUserConfig() {
        if(userConfig.has("url")) {
            tokenUrl = userConfig.getString("url")
        } else {
            throw(RuntimeException("Parsing user config failed: no url found (for the token endpoint)"))
        }
        if(!userConfig.has("users")) {
            throw(RuntimeException("Parsing user config failed: no users array found"))
        }
        val usersJson = userConfig.getJSONArray("users")
        val userList: MutableList<AutomatedUser> = mutableListOf()
        for(i in 0 until usersJson.length()) {
            val user = AutomatedUser.fromJSON(usersJson.getJSONObject(i))
            userList.add(user)
            log("Added user from config:\n$user")
        }
        users = userList
        log("Read ${users.size} users from configuration")
    }

    override val hasRegisteredActivityLaunchers: Boolean
        get() = selectUserLauncher != null

    companion object {
        private var selectUserLauncher: ActivityLauncher? = null
        fun registerActivityLaunchers(activity: ComponentActivity) {
            selectUserLauncher = ActivityLauncher(activity)
        }
    }
}

