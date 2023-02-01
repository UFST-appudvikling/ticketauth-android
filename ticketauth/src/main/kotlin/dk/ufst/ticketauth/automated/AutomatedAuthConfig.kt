@file:Suppress("unused")

package dk.ufst.ticketauth.automated

import android.content.Context
import android.content.SharedPreferences
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback

class AutomatedAuthConfig private constructor(
    val sharedPrefs: SharedPreferences,
    val context: Context,
    val baseUrl: String,
    val clientId: String,
    val scopes: String,
    val redirectUri: String,
    val onNewAccessTokenCallback: OnNewAccessTokenCallback,
    val onAuthResultCallback: OnAuthResultCallback,
    val tokenUrl: String,
    val apiKey: String,
    val nonce: String,
    val provider: Provider,
) {
    data class Builder(
        private var sharedPrefs: SharedPreferences? = null,
        private var context: Context? = null,
        private var baseUrl: String? = null,
        private var clientId: String? = null,
        private var scopes: String? = null,
        private var redirectUri: String? = null,
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null,
        private var onAuthResultCallback: OnAuthResultCallback = null,
        private var tokenUrl: String? = null,
        private var apiKey: String? = null,
        private var nonce: String? = null,
        private var provider: Provider? = null,
    ) {
        fun sharedPrefs(sharedPreferences: SharedPreferences) = apply { this.sharedPrefs = sharedPreferences }
        fun context(context: Context) = apply { this.context = context }
        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        fun scopes(scopes: String) = apply { this.scopes = scopes }
        fun redirectUri(redirectUri: String) = apply { this.redirectUri = redirectUri }
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun onAuthResult(callback: OnAuthResultCallback) = apply { this.onAuthResultCallback = callback}
        fun tokenUrl(url: String) = apply { this.tokenUrl = url }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun nonce(nonce: String) = apply { this.nonce = nonce }
        fun provider(provider: Provider) = apply { this.provider = provider }
        fun build() = AutomatedAuthConfig(
            sharedPrefs ?: throw(RuntimeException("sharedPrefs is required")),
            context ?: throw(RuntimeException("context is required")),
            baseUrl ?: throw(RuntimeException("baseUrl is required")),
            clientId ?: throw(RuntimeException("clientId is required")),
            scopes ?: throw(RuntimeException("scopes is required")),
            redirectUri ?: throw(RuntimeException("redirectUri is required")),
            onNewAccessTokenCallback,
            onAuthResultCallback,
            tokenUrl ?: throw(RuntimeException("tokenUrl is required")),
            apiKey ?: throw(RuntimeException("apiKey is required")),
            nonce ?: throw(RuntimeException("nonce is required")),
            provider ?: throw(RuntimeException("provider is required")),
        )
    }

    enum class Provider(val jsonValue: String) {
        Azure("azure"),
        Dcs("dcs")
    }
}
