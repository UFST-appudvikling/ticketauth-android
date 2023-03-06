package dk.ufst.ticketauth.authcode

import android.util.Base64
import dk.ufst.ticketauth.log
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Generates code verifiers and challenges for PKCE exchange.
 * Borrowed from AppAuth and converted to Kotlin
 */
object PkceUtil {
    /**
     * The default entropy (in bytes) used for the code verifier.
     */
    private const val DEFAULT_CODE_VERIFIER_ENTROPY = 64

    /**
     * The minimum permitted entropy (in bytes) for use with
     * [.generateRandomCodeVerifier].
     */
    private const val MIN_CODE_VERIFIER_ENTROPY = 32

    /**
     * The maximum permitted entropy (in bytes) for use with
     * [.generateRandomCodeVerifier].
     */
    private const val MAX_CODE_VERIFIER_ENTROPY = 96

    /**
     * Base64 encoding settings used for generated code verifiers.
     */
    private const val PKCE_BASE64_ENCODE_SETTINGS =
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

    private const val CODE_CHALLENGE_METHOD_S256 = "S256"

    /**
     * Plain-text code verifier challenge method. This is only used by AppAuth for Android if
     * SHA-256 is not supported on this platform.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    private const val CODE_CHALLENGE_METHOD_PLAIN = "plain"

    /**
     * Generates a random code verifier string using the provided entropy source and the specified
     * number of bytes of entropy.
     */
    /**
     * Generates a random code verifier string using [SecureRandom] as the source of
     * entropy, with the default entropy quantity as defined by
     * [.DEFAULT_CODE_VERIFIER_ENTROPY].
     */
    @JvmOverloads
    fun generateRandomCodeVerifier(
        entropySource: SecureRandom = SecureRandom(),
        entropyBytes: Int = DEFAULT_CODE_VERIFIER_ENTROPY
    ): String {
        checkArgument(
            MIN_CODE_VERIFIER_ENTROPY <= entropyBytes,
            "entropyBytes is less than the minimum permitted"
        )
        checkArgument(
            entropyBytes <= MAX_CODE_VERIFIER_ENTROPY,
            "entropyBytes is greater than the maximum permitted"
        )
        val randomBytes = ByteArray(entropyBytes)
        entropySource.nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, PKCE_BASE64_ENCODE_SETTINGS)
    }

    /**
     * Produces a challenge from a code verifier, using SHA-256 as the challenge method if the
     * system supports it (all Android devices _should_ support SHA-256), and falls back
     * to the plain challenge type if
     * unavailable.
     */
    fun deriveCodeVerifierChallenge(codeVerifier: String): String {
        return try {
            val sha256Digester = MessageDigest.getInstance("SHA-256")
            sha256Digester.update(codeVerifier.toByteArray(charset("ISO_8859_1")))
            val digestBytes = sha256Digester.digest()
            Base64.encodeToString(digestBytes, PKCE_BASE64_ENCODE_SETTINGS)
        } catch (e: NoSuchAlgorithmException) {
            log("SHA-256 is not supported on this device! Using plain challenge")
            codeVerifier
        } catch (e: UnsupportedEncodingException) {
            log("ISO-8859-1 encoding not supported on this device!")
            throw IllegalStateException("ISO-8859-1 encoding not supported", e)
        }
    }// no exception, so SHA-256 is supported

    /**
     * Returns the challenge method utilized on this system: typically
     * SHA-256 if supported by
     * the system, plain otherwise.
     */
    val codeVerifierChallengeMethod: String
        get() = try {
            MessageDigest.getInstance("SHA-256")
            // no exception, so SHA-256 is supported
            CODE_CHALLENGE_METHOD_S256
        } catch (e: NoSuchAlgorithmException) {
            CODE_CHALLENGE_METHOD_PLAIN
        }

    private fun checkArgument(expression: Boolean, errorMessage: Any?) {
        require(expression) { errorMessage.toString() }
    }
}

