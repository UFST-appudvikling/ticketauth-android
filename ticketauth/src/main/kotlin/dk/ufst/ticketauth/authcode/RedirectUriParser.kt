package dk.ufst.ticketauth.authcode

import android.net.Uri

/**
 * Parses the redirect uri and params. If an error is returned it is encoded in the fragment part
 * of the URI. Since android.net.Uri doesn't support parsing that part is being done here with
 * regexes. This is generally not the optimal way (a tokenizer would be a better solution)
 */
class RedirectUriParser {
    private val errorRx = Regex("error=([\\w\\s]+)&")
    private val errorDescriptionRx = Regex("error_description=(.+)&")

    sealed class ParsedResult {
        data class Error(val error: String, val errorDescription: String) : ParsedResult()
        data class Success(val code: String, val state: String) : ParsedResult()
    }

    // Success uri sample: state=xcoiv98y2kd22vusuye3kch&session_state=da833f06-d41a-4975-aedb-2bf0d21a5f21&code=e5e110b1-60f5-4f81-a600-bea93b41f9ca.da833f06-d41a-4975-aedb-2bf0d21a5f21.ea5f3470-3806-4077-bcbb-81683112aa85&transaction_id=22b26a26-fc08-4a1d-9a23-cda802569f01
    // Logout success uri sample:  dk.ufst.dl.app.inspector.uat://callback?state=vOxZhuPBVPH6R6RL8LMq6A&transaction_id=83338156-6499-4531-a554-040117d4cad8
    // Error uri sample: error=invalid_request&error_description=Invalid+state.+State+must+be+at+least+128+bit+long.&state=tooshort
    fun parse(uri: Uri): ParsedResult {
        if(uri.query != null) {
            val state = uri.getQueryParameter("state")
            val code = uri.getQueryParameter("code")
            if(state != null && code != null) {
                return ParsedResult.Success(code, state)
            }
        } else {
            uri.fragment?.let { fragment ->
                val error = errorRx.find(fragment)?.groupValues?.get(1)
                var errorDescription = errorDescriptionRx.find(fragment)?.groupValues?.get(1)
                errorDescription = java.net.URLDecoder.decode(errorDescription, "UTF-8")
                return ParsedResult.Error(error ?: "unknown", errorDescription ?: "")
            }
        }
        return ParsedResult.Error("unknown", "")
    }
}