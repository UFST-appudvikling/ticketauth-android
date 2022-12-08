package dk.ufst.ticketauth

class AuthEngineMock: AuthEngine {
    override var onWakeThreads: ()->Unit = {}

    override fun launchAuthIntent() {
        Thread.sleep(100)
        onWakeThreads()
    }

    override fun launchLogoutIntent() {
        Thread.sleep(100)
        onWakeThreads()
    }

    override fun performBlockingTokenRefresh(): Boolean {
        return true
    }

    override fun needsTokenRefresh(): Boolean {
        return true
    }

    override fun clear() {
    }


    override fun runOnUiThread(block: () -> Unit) {
        block()
    }

    override val loginWasCancelled: Boolean
        get() = false
    override val logoutWasCancelled: Boolean
        get() = false
    override val roles: List<String>
        get() = emptyList()
    override val accessToken: String?
        get() = "TOKEN"
}