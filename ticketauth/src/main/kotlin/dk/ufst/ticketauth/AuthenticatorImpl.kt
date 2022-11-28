package dk.ufst.ticketauth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

internal class AuthenticatorImpl(
    private var engine: AuthEngine
): Authenticator {
    init {
        engine.onWakeThreads = {
            wakeThreads()
        }

    }

    private var latch: AtomicReference<CountDownLatch> = AtomicReference()
    private var loginCallback: LoginCallback? = null

    override fun login(callback: LoginCallback?) {
        if(latch.compareAndSet(null, CountDownLatch(1))) {
            loginCallback = callback
            engine.clear()
            engine.runOnUiThread {
                engine.launchAuthIntent()
            }
        } else {
            log("Login already in progress")
        }
    }

    override fun logout() {
        if(latch.compareAndSet(null, CountDownLatch(1))) {
            engine.runOnUiThread {
                engine.launchLogoutIntent()
                engine.clear()
            }
        } else {
            log("Cannot logout while login or token refresh is in progress")
        }
    }

    override fun prepareCall(): AuthResult {
        if(engine.needsTokenRefresh()) {
            log("Token needs refresh, pausing network call")
            // first caller creates the latch and waits, subsequent callers just wait on the latch
            if(latch.compareAndSet(null, CountDownLatch(1))) {
                if(!engine.performBlockingTokenRefresh()) {
                    log("Token could NOT be refreshed, attempting login")
                    engine.runOnUiThread {
                        engine.launchAuthIntent()
                    }
                } else {
                    log("Refresh succeeded, resuming network calls in progress")
                    wakeThreads()
                }
            } else {
                log("Token refresh or login in progress, awaiting completion...")
            }
            // goto sleep until wakeThreads is called
            latch.get()?.await()
            if(engine.loginWasCancelled) {
                log("Thread woke up but user cancelled the login flow, return error")
                return AuthResult.CANCELLED_FLOW
            }
            if(engine.needsTokenRefresh()) {
                log("Thread woke up but TicketAuth didn't manage to obtain a new token, return error")
                return AuthResult.ERROR
            }
        }
        return AuthResult.SUCCESS
    }

    override fun clearToken() = engine.clear()
    override val accessToken: String? = engine.accessToken
    override val roles: List<String> = engine.roles

    private fun wakeThreads() {
        latch.getAndSet(null).countDown()
        if(loginCallback != null) {
            val localCallback = loginCallback
            loginCallback = null
            when(true) {
                engine.loginWasCancelled -> engine.runOnUiThread { localCallback!!(AuthResult.CANCELLED_FLOW) }
                engine.needsTokenRefresh() -> engine.runOnUiThread { localCallback!!(AuthResult.ERROR) }
                else -> engine.runOnUiThread { localCallback!!(AuthResult.SUCCESS) }
            }
        }
    }
}