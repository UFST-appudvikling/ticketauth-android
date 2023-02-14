package dk.ufst.ticketauth.automated

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import dk.ufst.ticketauth.BuildConfig
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

    @SuppressLint("SetTextI18n")
    private fun buildContentView(users: JSONArray): View {
        val bgColor = Color.parseColor("#14143C")
        val textColor = Color.parseColor("#FFFFFF")
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        ll.setPadding(0, toDp(16f).toInt(), 0, 0)
        ll.setBackgroundColor(bgColor)
        TextView(this)
            .apply {
                text = "Automated Login"
                setTextColor(textColor)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = toDp(10.0f)
            }
            .also { ll.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        TextView(this)
            .apply {
                text = "Powered by TicketAuth v${BuildConfig.TICKETAUTH_VERSION}"
                setTextColor(textColor)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = toDp(5.0f)
            }
            .also { ll.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        ScrollView(this).apply {
            setPadding(toDp(16f).toInt())
            addView(LinearLayout(this@SelectUserActivity).apply {
                orientation = LinearLayout.VERTICAL
                for(i in 0 until users.length()) {
                    val user = users.getJSONObject(i)
                    val title = user.getString("title")
                    val lparams =  LinearLayout.LayoutParams(
                        MATCH_PARENT, WRAP_CONTENT).apply {
                            setMargins(0, toDp(8f).toInt(), 0, toDp(8f).toInt())
                        }
                    addView(Button(this@SelectUserActivity).apply {
                        text = title
                        //setTextColor(textColor)
                        textSize = toDp(9.0f)
                        setOnClickListener {
                            onClicked(i)
                        }
                    }, lparams)
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