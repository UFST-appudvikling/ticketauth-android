package dk.ufst.ticketauth

data class AuthJob (
    var id: Int = 0,
    var result: AuthResult? = null,
    var callback: AuthCallback? = null
)