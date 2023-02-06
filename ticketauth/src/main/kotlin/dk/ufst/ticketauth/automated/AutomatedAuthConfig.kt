@file:Suppress("unused")

package dk.ufst.ticketauth.automated

import android.content.Context
import android.content.SharedPreferences
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback

class AutomatedAuthConfig private constructor(
    val sharedPrefs: SharedPreferences,
    val clientId: String,
    val onNewAccessTokenCallback: OnNewAccessTokenCallback,
    val onAuthResultCallback: OnAuthResultCallback,
    val tokenUrl: String,
    val apiKey: String,
    val nonce: String,
    val provider: Provider,
) {
    data class Builder(
        private var sharedPrefs: SharedPreferences? = null,
        private var clientId: String? = null,
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null,
        private var onAuthResultCallback: OnAuthResultCallback = null,
        private var tokenUrl: String? = null,
        private var apiKey: String? = null,
        private var nonce: String? = null,
        private var provider: Provider? = null,
    ) {
        fun sharedPrefs(sharedPreferences: SharedPreferences) = apply { this.sharedPrefs = sharedPreferences }
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun onAuthResult(callback: OnAuthResultCallback) = apply { this.onAuthResultCallback = callback}
        fun tokenUrl(url: String) = apply { this.tokenUrl = url }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun nonce(nonce: String) = apply { this.nonce = nonce }
        fun provider(provider: Provider) = apply { this.provider = provider }
        fun build() = AutomatedAuthConfig(
            sharedPrefs ?: throw(RuntimeException("sharedPrefs is required")),
            clientId ?: throw(RuntimeException("clientId is required")),
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
