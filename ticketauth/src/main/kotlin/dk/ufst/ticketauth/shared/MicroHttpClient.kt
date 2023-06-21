package dk.ufst.ticketauth.shared

import android.annotation.SuppressLint
import dk.ufst.ticketauth.log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Encapsulates the builtin HTTPUrlConnection (which in turn uses some version of okhttp
 * shipped with android)
 */
internal object MicroHttp {
    var unsafe: Boolean = false

    private fun unsafeHttps(https: HttpsURLConnection) {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts: Array<TrustManager> = arrayOf(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                    // Having some code in this function truly suppresses the TrustAllX509TrustManager
                    // warning. Using SuppressLint/Suppress only suppresses the warning
                    // in Android Studio, but is still warned by the "gradlew lint" command.
                    Any()
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                    // Having some code in this function truly suppresses the TrustAllX509TrustManager
                    // warning. Using SuppressLint/Suppress only suppresses the warning
                    // in Android Studio, but is still warned by the "gradlew lint" command.
                    Any()
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )

        // Install the all-trusting trust manager
        val sslContext: SSLContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        // Create an ssl socket factory with our all-trusting manager
        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
        https.sslSocketFactory = sslSocketFactory
        https.setHostnameVerifier { _, _ -> true }
    }

    /**
     * Encapsulates sending a json body to a https endpoint, receiving a reply and parsing it
     * as json or reading an error body and throwing a RuntimeException.
     *
     * - Makes sure to read the error response body so we don't get leaked connection warnings
     */
    fun postJson(url: String, json: JSONObject): JSONObject {
        val connection: HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
            if(unsafe) {
                unsafeHttps(this)
            }
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        connection.outputStream.use { os ->
            val input = json.toString().toByteArray(charset("utf-8"))
            os.write(input, 0, input.size)
        }
        val responseCode = connection.responseCode
        log("Endpoint Response: $responseCode: ${connection.responseMessage}")
        val success = responseCode in 200..399
        val stream = if(success) connection.inputStream else connection.errorStream
        BufferedReader(InputStreamReader(stream, "utf-8")).use { br ->
            val response = StringBuilder()
            var responseLine: String?
            while (br.readLine().also { responseLine = it } != null) {
                response.append(responseLine!!.trim())
            }
            if(success) {
                return JSONObject(response.toString())
            } else {
                log("Received error body: $response")
                throw(RuntimeException("Token endpoint returned: $responseCode"))
            }
        }
    }

    fun postFormUrlEncoded(url: String, params: Map<String, String>): JSONObject {
        val connection: HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
            if(unsafe) {
                unsafeHttps(this)
            }
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        connection.outputStream.use { os ->
            val input = getFormUrlEncoded(params).toByteArray(charset("utf-8"))
            os.write(input, 0, input.size)
        }
        val responseCode = connection.responseCode
        log("Endpoint Response: $responseCode: ${connection.responseMessage}")
        val success = responseCode in 200..399
        val stream = if(success) connection.inputStream else connection.errorStream
        BufferedReader(InputStreamReader(stream, "utf-8")).use { br ->
            val response = StringBuilder()
            var responseLine: String?
            while (br.readLine().also { responseLine = it } != null) {
                response.append(responseLine!!.trim())
            }
            if(success) {
                return JSONObject(response.toString())
            } else {
                log("Received error body: $response")
                throw(RuntimeException("Token endpoint returned: $responseCode"))
            }
        }
    }

    private fun getFormUrlEncoded(params: Map<String, String>): String {
        val result = StringBuilder()
        var first = true
        for(entry in params) {
            if (first) first = false else result.append("&")
            result.append(URLEncoder.encode(entry.key, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode(entry.value, "UTF-8"))
        }
        return result.toString()
    }
}