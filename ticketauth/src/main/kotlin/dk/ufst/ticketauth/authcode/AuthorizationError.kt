package dk.ufst.ticketauth.authcode

import android.content.Intent

data class AuthorizationError(val error: String, val description: String) {
    fun toIntent(): Intent = Intent().apply {
        putExtra(ERROR_KEY, error)
        putExtra(DESCRIPTION_KEY, description)
    }

    companion object {
        private const val ERROR_KEY = "error"
        private const val DESCRIPTION_KEY = "description"

        fun fromIntent(intent: Intent): AuthorizationError {
            return AuthorizationError(
                error = intent.getStringExtra(ERROR_KEY) ?: throw(RuntimeException("Intent must contain $ERROR_KEY extra")),
                description = intent.getStringExtra(DESCRIPTION_KEY) ?: throw(RuntimeException("Intent must contain $ERROR_KEY extra"))
            )
        }
    }
}