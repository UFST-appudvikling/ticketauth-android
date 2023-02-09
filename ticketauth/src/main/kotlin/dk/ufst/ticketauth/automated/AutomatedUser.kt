package dk.ufst.ticketauth.automated

import org.json.JSONObject

internal data class AutomatedUser(
    val title: String,
    val apiKey: String,
    val clientId: String,
    val provider: AutomatedAuthConfig.Provider,
    val nonce: String,
    val providerData: JSONObject,
    val authorizations: JSONObject
) {
    companion object {
        fun fromJSON(json: JSONObject): AutomatedUser {
            val provider = when(json.getString("azureOrDcs")) {
                "azure" -> AutomatedAuthConfig.Provider.Azure
                "dcs" -> AutomatedAuthConfig.Provider.Dcs
                else -> throw(RuntimeException("Error parsing user config, unknown provider: ${json.getString("azureOrDcs")}"))
            }

            return AutomatedUser(
                title = json.getString("title"),
                apiKey = json.getString("api-key"),
                clientId = json.getString("client_id"),
                provider = provider,
                nonce = json.getString("nonce"),
                providerData = json.getJSONObject(provider.jsonValue),
                authorizations = json.getJSONObject("authorizations")
            )
        }
    }
}
