package dk.ufst.ticketauth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

internal var hostApplicationContext: Context? = null

class TicketAuthInitProvider: ContentProvider() {
    override fun onCreate(): Boolean {
        Log.d("TicketAuth", "TicketAuth ${BuildConfig.TICKETAUTH_VERSION} initializing")
        hostApplicationContext = context
        return false
    }

    override fun query(
        p0: Uri,
        p1: Array<out String>?,
        p2: String?,
        p3: Array<out String>?,
        p4: String?
    ): Cursor? {
        return null
    }

    override fun getType(p0: Uri): String? {
        return null
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        return null
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        return 0
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return 0
    }
}
