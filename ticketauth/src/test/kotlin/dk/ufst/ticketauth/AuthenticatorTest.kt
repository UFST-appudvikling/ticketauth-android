package dk.ufst.ticketauth

import org.junit.Test

import org.junit.Assert.*

class AuthenticatorTest {
    @Test
    fun `test login`() {
        val engine = AuthEngineMock()
        engine.onWakeThreads = {
            assertTrue(true)
        }
        val authenticator = AuthenticatorImpl(engine)
        authenticator.login()
        assertTrue(false)
    }
}