package com.xiaogong.ntfy.im.msg

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.db.Action
import com.xiaogong.ntfy.im.db.Notification
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.Subscription
import com.xiaogong.ntfy.im.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * The broadcast service is responsible for sending and receiving broadcast intents
 * in order to facilitate tasks app integrations.
 */
class BroadcastService(private val ctx: Context) {
    fun sendMessage(subscription: Subscription, notification: Notification, muted: Boolean) {
        val intent = Intent()
        intent.action = MESSAGE_RECEIVED_ACTION
        intent.putExtra("id", notification.id)
        intent.putExtra("base_url", subscription.baseUrl)
        intent.putExtra("topic", subscription.topic)
        intent.putExtra("time", notification.timestamp.toInt())
        intent.putExtra("title", notification.title)
        intent.putExtra("message", decodeMessage(notification))
        intent.putExtra("message_bytes", decodeBytesMessage(notification))
        intent.putExtra("message_encoding", notification.encoding)
        intent.putExtra("content_type", notification.contentType)
        intent.putExtra("tags", notification.tags)
        intent.putExtra("tags_map", joinTagsMap(splitTags(notification.tags)))
        intent.putExtra("priority", notification.priority)
        intent.putExtra("click", notification.click)
        intent.putExtra("muted", muted)
        intent.putExtra("muted_str", muted.toString())
        intent.putExtra("attachment_name", notification.attachment?.name ?: "")
        intent.putExtra("attachment_type", notification.attachment?.type ?: "")
        intent.putExtra("attachment_size", notification.attachment?.size ?: 0L)
        intent.putExtra("attachment_expires", notification.attachment?.expires ?: 0L)
        intent.putExtra("attachment_url", notification.attachment?.url ?: "")

        Log.d(TAG, "Sending message intent broadcast: ${intent.action} with extras ${intent.extras}")
        ctx.sendBroadcast(intent)
    }

    fun sendUserAction(action: Action) {
        val intent = Intent()
        intent.action = action.intent ?: USER_ACTION_ACTION
        action.extras?.forEach { (key, value) ->
            intent.putExtra(key, value)
        }
        Log.d(TAG, "Sending user action intent broadcast: ${intent.action} with extras ${intent.extras}")
        ctx.sendBroadcast(intent)
    }

    /**
     * This receiver is triggered when the SEND_MESSAGE intent is received.
     * See AndroidManifest.xml for details.
     */
    class BroadcastReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Broadcast received: $intent")
            when (intent.action) {
                MESSAGE_SEND_ACTION -> send(context, intent)
            }
        }

        private fun send(ctx: Context, intent: Intent) {
            val api = ApiService(ctx)
            val baseUrl = getStringExtra(intent, "base_url") ?: ctx.getString(R.string.app_base_url)
            val topic = getStringExtra(intent, "topic") ?: return
            val message = getStringExtra(intent, "message") ?: return
            val title = getStringExtra(intent, "title") ?: ""
            val tags = getStringExtra(intent,"tags") ?: ""
            val priority = when (getStringExtra(intent, "priority")) {
                "min", "1" -> 1
                "low", "2" -> 2
                "default", "3" -> 3
                "high", "4" -> 4
                "urgent", "max", "5" -> 5
                else -> 0
            }
            val delay = getStringExtra(intent,"delay") ?: ""
            GlobalScope.launch(Dispatchers.IO) {
                val repository = Repository.getInstance(ctx)
                val user = repository.getUser(baseUrl)
                try {
                    // Get subscription to check encryption settings
                    val subscription = repository.getSubscription(baseUrl, topic)

                    // Prepare the message to send
                    val messageToSend = if (subscription?.encryptionEnabled == true) {
                        // Encryption is enabled, encrypt the message
                        val encryptPassword = subscription.encryptPassword
                        if (encryptPassword.isNullOrEmpty()) {
                            // Encryption enabled but no password configured
                            Log.w(TAG, "Encryption enabled but password not configured")
                            return@launch
                        }

                        // Encrypt the entire JSON message
                        try {
                            val username = repository.getUserProfile()?.username.orEmpty()
                            val gson = Gson()
                            val jsonMessage = gson.toJson(mapOf("user" to username, "message" to message))
                            CryptoUtil.encrypt(jsonMessage, encryptPassword)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to encrypt message", e)
                            return@launch
                        }
                    } else {
                        // Encryption not enabled, send plain message
                        message
                    }

                    Log.d(TAG, "Publishing message $intent")
                    api.publish(
                        baseUrl = baseUrl,
                        topic = topic,
                        user = user,
                        message = messageToSend,
                        title = title,
                        priority = priority,
                        tags = splitTags(tags),
                        delay = delay
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to publish message: ${e.message}", e)
                }
            }
        }

        /**
         * Gets an extra as a String value, even if the extra may be an int or a long.
         */
        private fun getStringExtra(intent: Intent, name: String): String? {
            if (intent.getStringExtra(name) != null) {
                return intent.getStringExtra(name)
            } else if (intent.getIntExtra(name, DOES_NOT_EXIST) != DOES_NOT_EXIST) {
                return intent.getIntExtra(name, DOES_NOT_EXIST).toString()
            } else if (intent.getLongExtra(name, DOES_NOT_EXIST.toLong()) != DOES_NOT_EXIST.toLong()) {
                return intent.getLongExtra(name, DOES_NOT_EXIST.toLong()).toString()
            }
            return null
        }
    }

    companion object {
        private const val TAG = "NtfyBroadcastService"
        private const val DOES_NOT_EXIST = -2586000

        // These constants cannot be changed without breaking the contract; also see manifest
        private const val MESSAGE_RECEIVED_ACTION = "com.xiaogong.ntfy.im.MESSAGE_RECEIVED"
        private const val MESSAGE_SEND_ACTION = "com.xiaogong.ntfy.im.SEND_MESSAGE"
        private const val USER_ACTION_ACTION = "com.xiaogong.ntfy.im.USER_ACTION"
    }
}
