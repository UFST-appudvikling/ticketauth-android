@file:Suppress("unused")

package dk.ufst.ticketauth.automated

import android.content.Context
import dk.ufst.ticketauth.OnAuthResultCallback
import dk.ufst.ticketauth.OnNewAccessTokenCallback
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.Charset

class AutomatedAuthConfig private constructor(
    val onNewAccessTokenCallback: OnNewAccessTokenCallback,
    val onAuthResultCallback: OnAuthResultCallback,
    val userConfig: JSONObject
) {
    data class Builder(
        private var onNewAccessTokenCallback: OnNewAccessTokenCallback = null,
        private var onAuthResultCallback: OnAuthResultCallback = null,
        private var userConfig: JSONObject? = null
    ) {
        fun onNewAccessToken(callback: OnNewAccessTokenCallback) = apply { this.onNewAccessTokenCallback = callback}
        fun onAuthResult(callback: OnAuthResultCallback) = apply { this.onAuthResultCallback = callback}
        fun userConfig(config: String) = apply {
            try {
                this.userConfig = JSONObject(config)
            } catch (t : Throwable) {
                throw(RuntimeException("Configuration cannot be parsed as JSON"))
            }
        }
        fun build() = AutomatedAuthConfig(
            onNewAccessTokenCallback,
            onAuthResultCallback,
            userConfig ?: throw(RuntimeException("No configuration specified"))
        )
    }

    enum class Provider(val jsonValue: String) {
        Azure("azure"),
        Dcs("dcs")
    }

    companion object {
        fun fromAssets(context: Context, filename: String): String {
            val stream: InputStream = context.assets.open(filename)
            val size: Int = stream.available()
            val buffer = ByteArray(size)
            stream.read(buffer)
            stream.close()
            return String(buffer, Charset.forName("UTF-8"))
        }
    }
}
