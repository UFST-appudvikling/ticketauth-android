package dk.ufst.ticketauth.shared

import android.util.Base64
import java.security.SecureRandom

object Util {
    fun generateRandomState(): String? {
        val sr = SecureRandom()
        val random = ByteArray(STATE_LENGTH)
        sr.nextBytes(random)
        return Base64.encodeToString(random, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    private const val STATE_LENGTH = 16
}