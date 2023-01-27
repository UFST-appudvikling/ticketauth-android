@file:Suppress("unused")

package dk.ufst.ticketauth.automated

import android.content.Context
import android.content.SharedPreferences
import dk.ufst.ticketauth.OnNewAccessTokenCallback

class AutomatedAuthConfig private constructor(
    val sharedPrefs: SharedPreferences,
    val context: Context,
    val baseUrl: String,
    val clientId: String,
    val apiKey: String,
    val provider: String,
    val onNewAccessTokenCallback: OnNewAccessTokenCallback
) {
    data class Builder(
        private var sharedPrefs: SharedPreferences? = null,
        private var context: Context? = null,
        private var baseUrl: String? = null,
        private var clientId: String? = null,
        private var apiKey: String? = null,
        private var provider: String? = null,
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null
    ) {
        fun sharedPrefs(sharedPreferences: SharedPreferences) = apply { this.sharedPrefs = sharedPreferences }
        fun context(context: Context) = apply { this.context = context }
        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        fun apiKey(scopes: String) = apply { this.apiKey = scopes }
        fun provider(uri: String) = apply { this.provider = uri }
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun build() = AutomatedAuthConfig(
            sharedPrefs ?: throw(RuntimeException("sharedPrefs is required")),
            context ?: throw(RuntimeException("context is required")),
            baseUrl ?: throw(RuntimeException("baseUrl is required")),
            clientId ?: throw(RuntimeException("clientId is required")),
            apiKey ?: throw(RuntimeException("apiKey is required")),
            provider ?: throw(RuntimeException("provider is required")),
            onNewAccessTokenCallback,
        )
    }
}
