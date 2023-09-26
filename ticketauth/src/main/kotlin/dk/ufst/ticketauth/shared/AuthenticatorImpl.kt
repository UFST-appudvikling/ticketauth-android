package dk.ufst.ticketauth.shared

import dk.ufst.ticketauth.AuthCallback
import dk.ufst.ticketauth.AuthEngine
import dk.ufst.ticketauth.AuthResult
import dk.ufst.ticketauth.Authenticator
import dk.ufst.ticketauth.log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

internal class AuthenticatorImpl(
    private var engine: AuthEngine
): Authenticator {
    private var nextJobId: Int = 1

    init {
        engine.onWakeThreads = {
            wakeThreads()
        }
    }

    private var latch: AtomicReference<CountDownLatch> = AtomicReference()

    override fun login(callback: AuthCallback?) {
        if(latch.compareAndSet(null, CountDownLatch(1))) {
            val job = spawnJob(noReturn = true)
            job.callback = callback
            engine.clear()
            engine.runOnUiThread {
                engine.launchAuthIntent()
            }
        } else {
            log("Login already in progress")
        }
    }

    override fun logout(callback: AuthCallback?) {
        if(latch.compareAndSet(null, CountDownLatch(1))) {
            val job = spawnJob(noReturn = true)
            job.callback = callback
            engine.runOnUiThread {
                engine.launchLogoutIntent()
            }
        } else {
            log("Cannot logout while login or token refresh is in progress")
        }
    }

    override fun prepareCall(): AuthResult {
        val job = spawnJob(noReturn = false)
        var result: AuthResult = AuthResult.SUCCESS
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

            job.result?.let {
                log("Thread woke up but found pending AuthResult: ${it}, return result")
                result = it
            }
        }
        killJob(job)
        return result
    }

    private fun spawnJob(noReturn: Boolean = false): AuthJob {
        val job = AuthJob(id = nextJobId, noReturn = noReturn)
        engine.jobs[nextJobId] = job
        nextJobId++
        return job
    }

    private fun killJob(job: AuthJob) {
        engine.jobs.remove(job.id)
    }

    override fun clearToken() = engine.clear()
    override val accessToken: String?
        get() = engine.accessToken

    override val roles: List<String> = engine.roles

    private fun wakeThreads() {
        latch.getAndSet(null)?.countDown()
    }
}
