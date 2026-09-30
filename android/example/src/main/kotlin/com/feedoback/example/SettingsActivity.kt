package com.feedoback.example

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.feedoback.FeedbackCategory
import com.feedoback.Feedoback

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Account"
            textSize = 13f
            setPadding(0, dp(28), 0, dp(8))
            alpha = 0.6f
        })

        val passwordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        passwordRow.addView(TextView(this).apply {
            text = "Password\nHidden from any screenshot"
            textSize = 16f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // A real password field, which the SDK finds without being told.
        passwordRow.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("hunter2hunter2")
            isEnabled = false
        }, LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(passwordRow)

        root.addView(TextView(this).apply {
            text = "Feedback"
            textSize = 13f
            setPadding(0, dp(28), 0, dp(8))
            alpha = 0.6f
        })

        listOf(
            "Send feedback" to FeedbackCategory.FEEDBACK,
            "Report a problem" to FeedbackCategory.BUG,
        ).forEach { (label, category) ->
            root.addView(TextView(this).apply {
                text = label
                textSize = 17f
                setPadding(0, dp(16), 0, dp(16))
                isClickable = true
                contentDescription = label
                setOnClickListener { Feedoback.present(category) }
            })
        }

        setContentView(root)
    }
}
