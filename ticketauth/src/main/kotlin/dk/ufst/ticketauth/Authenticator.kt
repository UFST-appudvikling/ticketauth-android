package dk.ufst.ticketauth

interface Authenticator {
    fun login()
    fun logout()
    fun prepareCall(): Boolean
    fun clearToken()
}
