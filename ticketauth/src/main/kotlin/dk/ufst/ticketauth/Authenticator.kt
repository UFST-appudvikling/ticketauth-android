package dk.ufst.ticketauth

typealias LoginCallback = (AuthResult)->Unit

interface Authenticator {
    fun login(callback: LoginCallback? = null)
    fun logout()
    fun prepareCall(): AuthResult
    fun clearToken()
    val accessToken: String?
    val roles: List<String>
}
