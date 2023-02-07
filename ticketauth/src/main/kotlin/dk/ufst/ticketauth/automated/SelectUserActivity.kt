package dk.ufst.ticketauth.automated

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import org.json.JSONArray

class SelectUserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        intent?.extras?.getString("users")?.let {
            val usersJson = JSONArray(it)
            setContentView(buildContentView(usersJson))
        } ?: run {
            finish()
        }
    }

    private fun buildContentView(users: JSONArray): View {
        val bgColor = Color.parseColor("#14143C")
        val textColor = Color.parseColor("#FFFFFF")
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        ll.setBackgroundColor(bgColor)
        TextView(this)
            .apply {
                text = "Select User"
                setTextColor(textColor)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = toDp(12.0f)
            }
            .also { ll.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        ScrollView(this).apply {
            setPadding(toDp(16f).toInt())
            addView(LinearLayout(this@SelectUserActivity).apply {
                orientation = LinearLayout.VERTICAL
                for(i in 0 until users.length()) {
                    val user = users.getJSONObject(i)
                    val title = user.getString("title")
                    addView(Button(this@SelectUserActivity).apply {
                        text = title
                        //setTextColor(textColor)
                        textSize = toDp(9.0f)
                        setOnClickListener {
                            onClicked(i)
                        }
                    }, LinearLayout.LayoutParams(
                        MATCH_PARENT, WRAP_CONTENT))
                }
            }, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }.also { ll.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, 0).apply { weight = 1.0f }) }
        return ll
    }

    private fun onClicked(index: Int) {
        val intent = Intent()
        intent.putExtra("index", index)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun toDp(dp: Float): Float {
        return dp * Resources.getSystem().displayMetrics.density
    }
}