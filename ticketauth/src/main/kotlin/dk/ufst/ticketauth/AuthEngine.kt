package dk.ufst.ticketauth

internal interface AuthEngine {
    fun launchAuthIntent()
    fun launchLogoutIntent()
    fun performBlockingTokenRefresh(): Boolean
    fun needsTokenRefresh(): Boolean
    fun clear()
    var onWakeThreads: ()->Unit
    fun runOnUiThread(block: ()->Unit)
    val loginWasCancelled: Boolean
    val roles: List<String>
    val accessToken: String?
}
