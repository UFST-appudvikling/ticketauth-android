package dk.ufst.ticketauth.authcode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dk.ufst.ticketauth.log

class RedirectUriReceiverActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("Starting redirect activity")
        val responseIntent = Intent(this, BrowserManagementActivity::class.java).apply {
            data = intent.data
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(responseIntent)
        finish()
    }
}