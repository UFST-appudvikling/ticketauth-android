package dk.ufst.ticketauth

import androidx.activity.result.ActivityResult
import dk.ufst.ticketauth.authcode.AuthorizationError
import dk.ufst.ticketauth.authcode.RedirectUriParser

sealed interface AuthResult {
    object Success : AuthResult
    object CancelledFlow : AuthResult
    data class Error(val reason: ErrorCause) : AuthResult
}

sealed interface ErrorCause {
    data class GetToken(val throwable: Throwable) : ErrorCause
    data class AuthorizationFlow(val authorizationError: AuthorizationError) : ErrorCause
    data class ParseRedirectUri(val redirectUriParserError: RedirectUriParser.ParsedResult.Error) : ErrorCause
    data class UnknownAuthIntentResult(val activityResult: ActivityResult) : ErrorCause
    object MissingIdToken : ErrorCause
    data class UnknownRedirectUri(val expectedRedirectUri: String, val actualRedirectUri: String) : ErrorCause
    data class LaunchIntent(val throwable: Throwable) : ErrorCause
}
