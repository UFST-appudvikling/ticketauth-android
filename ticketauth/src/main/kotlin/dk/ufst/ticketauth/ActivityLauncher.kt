package dk.ufst.ticketauth

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.ref.WeakReference

typealias ProcessActivityResult = (ActivityResult)->Unit

/**
 * This helper class purpose is register an activity launcher upon instantiation
 * making it easy to launch at a later time with a custom callback.
 * it also holds a weak reference to the host app activity, the AuthEngines can use
 */
internal class ActivityLauncher(activity: ComponentActivity) {
    private var startForResultLauncher: ActivityResultLauncher<Intent?>? = null
    private var callback: ProcessActivityResult? = null
    private var activityRef: WeakReference<ComponentActivity> = WeakReference(null)

    init {
        activityRef = WeakReference(activity)
        startForResultLauncher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                callback?.invoke(result)
                callback = null
            }
    }

    fun launch(intent: Intent, callback: ProcessActivityResult?) {
        this.callback = callback
        if(startForResultLauncher == null) {
            throw(RuntimeException("Cannot launch activity. Host activity reference is null"))
        }
        startForResultLauncher?.launch(intent)
    }

    fun activity(): ComponentActivity = activityRef.get() ?: throw(RuntimeException("Host activity reference is null"))
}