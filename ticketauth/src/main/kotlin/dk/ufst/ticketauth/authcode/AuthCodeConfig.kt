@file:Suppress("unused")

package dk.ufst.ticketauth.authcode

import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import dk.ufst.ticketauth.hostApplicationContext

class AuthCodeConfig private constructor(
    val debug: Boolean,
    val dcsBaseUrl: String,
    val clientId: String,
    val scopes: String,
    val redirectUri: String,
    val onNewAccessTokenCallback: OnNewAccessTokenCallback,
    val onAuthResultCallback: OnAuthResultCallback,
    val onLoginResultCallback: OnAuthResultCallback,
    val onLogoutResultCallback: OnAuthResultCallback,
    val usePKSE: Boolean,
    val allowUnsafeHttps: Boolean
) {
    data class Builder(
        private var debug: Boolean = false,
        private var dcsBaseUrl: String? = null,
        private var clientId: String? = null,
        private var scopes: String? = null,
        private var redirectUri: String? = null,
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null,
        private var onAuthResultCallback: OnAuthResultCallback = null,
        private var onLoginResultCallback: OnAuthResultCallback = null,
        private var onLogoutResultCallback: OnAuthResultCallback = null,
        private var usePKSE: Boolean = true,
        private var allowUnsafeHttps: Boolean = false
    ) {
        fun debug(debug: Boolean) = apply { this.debug = debug }
        fun dcsBaseUrl(url: String) = apply { this.dcsBaseUrl = url }
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        fun scopes(scopes: String) = apply { this.scopes = scopes }
        fun redirectUri(uri: String) = apply { this.redirectUri = uri }
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun onAuthResult(callback: OnAuthResultCallback) = apply { this.onAuthResultCallback = callback}
        fun onLoginResult(callback: OnAuthResultCallback) = apply { this.onLoginResultCallback = callback}
        fun onLogoutResult(callback: OnAuthResultCallback) = apply { this.onLogoutResultCallback = callback}
        fun usePKSE(usePKSE: Boolean) = apply { this.usePKSE = usePKSE }
        fun allowUnsafeHttps(allowUnsafeHttps: Boolean) = apply { this.allowUnsafeHttps = allowUnsafeHttps }
        fun build() = AuthCodeConfig(
            debug,
            dcsBaseUrl ?: throw(RuntimeException("dcsBaseUrl is required")),
            clientId ?: throw(RuntimeException("clientId is required")),
            scopes ?: throw(RuntimeException("scopes is required")),
            redirectUri ?: "${hostApplicationContext!!.packageName}.ticketauth://callback?",
            onNewAccessTokenCallback,
            onAuthResultCallback,
            onLoginResultCallback,
            onLogoutResultCallback,
            usePKSE,
            allowUnsafeHttps
        )
    }
}
