package dk.ufst.ticketauth

import androidx.activity.ComponentActivity
import dk.ufst.ticketauth.authcode.AuthJob

typealias OnNewAccessTokenCallback = ((String)->Unit)?
typealias OnAuthResultCallback = ((AuthResult)->Unit)?

internal interface AuthEngine {
    fun launchAuthIntent()
    fun launchLogoutIntent()
    fun performBlockingTokenRefresh(): Boolean
    fun needsTokenRefresh(): Boolean
    fun clear()
    var onWakeThreads: ()->Unit
    fun runOnUiThread(block: ()->Unit)
    fun destroy()

    val hasRegisteredActivityLaunchers: Boolean
    val jobs: MutableMap<Int, AuthJob>

    val roles: List<String>
    val accessToken: String?
    val isAuthorized: Boolean
}
