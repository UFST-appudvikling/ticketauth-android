package dk.ufst.ticketauth.shared

import dk.ufst.ticketauth.AuthCallback
import dk.ufst.ticketauth.AuthResult

data class AuthJob (
    var id: Int = 0,
    var result: AuthResult? = null,
    var callback: AuthCallback? = null,
    var noReturn: Boolean = false
)