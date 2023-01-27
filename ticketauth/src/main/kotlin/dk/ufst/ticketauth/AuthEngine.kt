package dk.ufst.ticketauth

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

    val jobs: MutableMap<Int, AuthJob>

    val roles: List<String>
    val accessToken: String?
}
