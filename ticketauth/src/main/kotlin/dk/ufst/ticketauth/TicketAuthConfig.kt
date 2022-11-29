@file:Suppress("unused")

package dk.ufst.ticketauth

import android.content.Context
import android.content.SharedPreferences

class TicketAuthConfig private constructor(
    val sharedPrefs: SharedPreferences,
    val context: Context,
    val debug: Boolean,
    val dcsBaseUrl: String,
    val clientId: String,
    val scopes: String,
    val redirectUri: String,
    val onNewAccessTokenCallback: OnNewAccessTokenCallback
) {
    data class Builder(
        private var sharedPrefs: SharedPreferences? = null,
        private var context: Context? = null,
        private var debug: Boolean = false,
        private var dcsBaseUrl: String? = null,
        private var clientId: String? = null,
        private var scopes: String? = null,
        private var redirectUri: String? = null,
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null
    ) {
        fun sharedPrefs(sharedPreferences: SharedPreferences) = apply { this.sharedPrefs = sharedPreferences }
        fun context(context: Context) = apply { this.context = context }
        fun debug(debug: Boolean) = apply { this.debug = debug }
        fun dcsBaseUrl(url: String) = apply { this.dcsBaseUrl = url }
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        fun scopes(scopes: String) = apply { this.scopes = scopes }
        fun redirectUri(uri: String) = apply { this.redirectUri = uri }
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun build() = TicketAuthConfig(
            sharedPrefs ?: throw(RuntimeException("sharedPrefs is required")),
            context ?: throw(RuntimeException("context is required")),
            debug,
            dcsBaseUrl ?: throw(RuntimeException("dcsBaseUrl is required")),
            clientId ?: throw(RuntimeException("clientId is required")),
            scopes ?: throw(RuntimeException("scopes is required")),
            redirectUri ?: "${context!!.packageName}.ticketauth://callback?",
            onNewAccessTokenCallback,
        )
    }
}
