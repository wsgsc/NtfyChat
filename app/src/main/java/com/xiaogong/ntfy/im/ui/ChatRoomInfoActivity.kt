package com.xiaogong.ntfy.im.ui

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.util.isDarkThemeOn

class ChatRoomInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room_info)

        val repository = Repository.getInstance(this)

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: ""
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        val password = intent.getStringExtra(EXTRA_PASSWORD)

        // Toolbar
        val toolbarLayout = findViewById<AppBarLayout>(R.id.app_bar_drawer)
        val dynamicColors = repository.getDynamicColorsEnabled()
        val darkMode = isDarkThemeOn(this)
        toolbarLayout.setBackgroundColor(Colors.statusBarNormal(this, dynamicColors, darkMode))
        val toolbar = toolbarLayout.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(Colors.toolbarTextColor(this, dynamicColors, darkMode))
        toolbar.setNavigationIconTint(Colors.toolbarTextColor(this, dynamicColors, darkMode))
        setSupportActionBar(toolbar)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            Colors.shouldUseLightStatusBar(dynamicColors, darkMode)
        title = displayName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Topic
        findViewById<TextView>(R.id.chat_info_topic_value).text = topic
        findViewById<MaterialButton>(R.id.chat_info_topic_copy_btn).setOnClickListener {
            copyToClipboard("topic", topic)
            Toast.makeText(this, R.string.chat_info_topic_copied, Toast.LENGTH_SHORT).show()
        }

        // Password
        val passwordView = findViewById<TextView>(R.id.chat_info_password_value)
        val passwordCopyBtn = findViewById<MaterialButton>(R.id.chat_info_password_copy_btn)
        if (password.isNullOrBlank()) {
            passwordView.text = getString(R.string.chat_info_password_not_set)
            passwordCopyBtn.setOnClickListener {
                Toast.makeText(this, R.string.chat_info_password_not_set, Toast.LENGTH_SHORT).show()
            }
        } else {
            passwordView.text = password
            passwordCopyBtn.setOnClickListener {
                copyToClipboard("password", password)
                Toast.makeText(this, R.string.chat_info_password_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_DISPLAY_NAME = "displayName"
        const val EXTRA_TOPIC = "topic"
        const val EXTRA_PASSWORD = "password"
    }
}
