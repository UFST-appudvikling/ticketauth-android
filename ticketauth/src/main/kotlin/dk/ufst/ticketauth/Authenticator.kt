package dk.ufst.ticketauth

typealias AuthCallback = (AuthResult)->Unit

interface Authenticator {
    fun login(callback: AuthCallback? = null)
    fun logout(callback: AuthCallback? = null)
    fun prepareCall(): AuthResult
    fun clearToken()
    val accessToken: String?
    val roles: List<String>
}
