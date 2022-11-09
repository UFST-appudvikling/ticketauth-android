package dk.ufst.ticketauth

interface Authenticator {
    fun login()
    fun logout()
    fun prepareCall(): AuthResult
    fun clearToken()
}
