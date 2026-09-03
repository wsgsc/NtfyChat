package com.xiaogong.ntfy.im.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaogong.ntfy.im.db.ConnectionState
import com.xiaogong.ntfy.im.db.Notification
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.Subscription
import com.xiaogong.ntfy.im.db.User
import com.xiaogong.ntfy.im.msg.ApiService
import com.xiaogong.ntfy.im.msg.NotificationParser
import com.xiaogong.ntfy.im.util.CryptoUtil
import com.xiaogong.ntfy.im.util.Log
import com.xiaogong.ntfy.im.util.topicUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call

class JsonConnection(
    private val connectionId: ConnectionId,
    private val scope: CoroutineScope,
    private val repository: Repository,
    private val api: ApiService,
    private val user: User?,
    private val sinceId: String?,
    private val connectionDetailsListener: (String, ConnectionState, Throwable?, Long) -> Unit,
    private val notificationListener: (Subscription, Notification) -> Unit,
    private val serviceActive: () -> Boolean
) : Connection {
    private val baseUrl = connectionId.baseUrl
    private val topicsToSubscriptionIds = connectionId.topicsToSubscriptionIds
    private val topicsStr = topicsToSubscriptionIds.keys.joinToString(separator = ",")
    private val url = topicUrl(baseUrl, topicsStr)
    private val parser = NotificationParser()
    private val gson = Gson()

    private var since: String? = sinceId
    private var errorCount = 0
    private lateinit var call: Call
    private lateinit var job: Job

    override fun start() {
        job = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "[$url] Starting connection for subscriptions: $topicsToSubscriptionIds")

            while (isActive && serviceActive()) {
                Log.d(TAG, "[$url] (Re-)starting connection for subscriptions: $topicsToSubscriptionIds")
                
                try {
                    val (newCall, source) = api.subscribe(baseUrl, topicsStr, since, user)
                    call = newCall
                    if (errorCount > 0) {
                        errorCount = 0
                    }
                    connectionDetailsListener(baseUrl, ConnectionState.CONNECTED, null, 0L)
                    
                    // Blocking read loop: reads JSON lines until connection closes or is cancelled
                    while (isActive && serviceActive() && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val notificationWithTopic = parser.parseWithTopic(line,  subscriptionId = 0)
                        if (notificationWithTopic != null) {
                            since = notificationWithTopic.notification.id
                            val topic = notificationWithTopic.topic
                            val subscriptionId = topicsToSubscriptionIds[topic] ?: continue
                            val subscription = repository.getSubscription(subscriptionId) ?: continue
                            var notification = notificationWithTopic.notification.copy(subscriptionId = subscription.id)

                            // Try to decrypt the message if it's in encrypted format
                            notification = decryptNotificationIfNeeded(notification, subscription)

                            notificationListener(subscription, notification)
                        }
                    }
                    
                    Log.d(TAG, "[$url] Connection closed cleanly")
                } catch (e: Exception) {
                    if (!isActive) {
                        Log.d(TAG, "[$url] Connection cancelled")
                        break
                    }
                    Log.d(TAG, "[$url] Connection broken, reconnecting ...")
                    errorCount++
                    val retrySeconds = RETRY_SECONDS.getOrNull(errorCount-1) ?: RETRY_SECONDS.last()
                    val nextRetryTime = System.currentTimeMillis() + (retrySeconds * 1000L)
                    val error = if (isConnectionBrokenException(e)) null else e
                    connectionDetailsListener(baseUrl, ConnectionState.CONNECTING, error, nextRetryTime)
                    Log.w(TAG, "[$url] Retrying connection in ${retrySeconds}s ...")
                    delay(retrySeconds * 1000L)
                }
            }
            Log.d(TAG, "[$url] Connection job SHUT DOWN")
        }
    }

    override fun since(): String? {
        return since
    }

    override fun close() {
        Log.d(TAG, "[$url] Cancelling connection")
        if (this::job.isInitialized) job.cancel()
        if (this::call.isInitialized) call.cancel()
    }

    /**
     * Checks if the notification message is encrypted and decrypts it if encryption is enabled.
     * The encrypted format is a Base64 string that decrypts to {"user":"xxx","message":"yyy"}
     * Returns the modified notification with decrypted message and senderUser.
     */
    private fun decryptNotificationIfNeeded(notification: Notification, subscription: Subscription): Notification {
        // Only attempt decryption if encryption is enabled for this subscription
        if (!subscription.encryptionEnabled) {
            return notification
        }

        // Check if we have an encryption password
        val encryptPassword = subscription.encryptPassword
        if (encryptPassword.isNullOrEmpty()) {
            // Encryption enabled but no password, check if message looks encrypted
            if (isLikelyEncrypted(notification.message)) {
                return notification.copy(
                    message = "[解密失败: 未配置密码]",
                    senderUser = ""
                )
            }
            return notification
        }

        // Try to decrypt the message
        try {
            val decryptedJson = CryptoUtil.decrypt(notification.message, encryptPassword)
            // Try to parse as JSON with user/message fields
            val jsonObj = gson.fromJson(decryptedJson, JsonObject::class.java)
            if (jsonObj != null && jsonObj.has("user") && jsonObj.has("message")) {
                val senderUser = jsonObj.get("user").asString
                val plainMessage = jsonObj.get("message").asString
                return notification.copy(
                    message = plainMessage,
                    senderUser = senderUser
                )
            }
            // Decrypted but not expected JSON format, show decrypted content
            return notification.copy(message = decryptedJson)
        } catch (e: Exception) {
            // Decryption failed - might not be encrypted or wrong password
            if (isLikelyEncrypted(notification.message)) {
                Log.w(TAG, "Failed to decrypt message: ${e.message}")
                return notification.copy(
                    message = "[解密失败: ${e.message}]",
                    senderUser = ""
                )
            }
            // Not encrypted, return as-is
            return notification
        }
    }

    /**
     * Simple heuristic to check if a message looks like encrypted Base64 data
     */
    private fun isLikelyEncrypted(message: String): Boolean {
        // Encrypted messages are Base64 and typically longer than 40 chars
        // and don't look like normal text (no spaces at the beginning)
        return message.length > 40 &&
               !message.contains(" ") &&
               message.matches(Regex("^[A-Za-z0-9+/]+=*$"))
    }

    companion object {
        private const val TAG = "NtfyJsonConnection"
        private val RETRY_SECONDS = listOf(5, 10, 15, 20, 30, 45, 60, 120)
    }
}
