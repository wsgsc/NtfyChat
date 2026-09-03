package com.xiaogong.ntfy.im.service

import android.app.AlarmManager
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xiaogong.ntfy.im.db.ConnectionState
import com.xiaogong.ntfy.im.db.CustomHeader
import com.xiaogong.ntfy.im.db.Notification
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.Subscription
import com.xiaogong.ntfy.im.db.User
import com.xiaogong.ntfy.im.msg.NotificationParser
import com.xiaogong.ntfy.im.util.CryptoUtil
import com.xiaogong.ntfy.im.util.HttpUtil
import com.xiaogong.ntfy.im.util.Log
import com.xiaogong.ntfy.im.util.topicShortUrl
import com.xiaogong.ntfy.im.util.topicUrlWs
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ProtocolException
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Connect to ntfy server via WebSockets. This connection represents a single connection to a server, with
 * one or more topics. When the topics are changed, the connection is recreated by the service.
 *
 * The connection re-connects on failure, indefinitely. It reports limited status via the stateChangeListener,
 * and forwards incoming messages via the notificationListener.
 *
 * The original class is taken from the fantastic Gotify project (MIT). Thank you:
 * https://github.com/gotify/android/blob/master/app/src/main/java/com/github/gotify/service/WebSocketConnection.java
 */
class WsConnection(
    private val connectionId: ConnectionId,
    private val repository: Repository,
    private val httpClient: OkHttpClient,
    private val user: User?,
    private val customHeaders: List<CustomHeader>,
    private val sinceId: String?,
    private val connectionDetailsListener: (String, ConnectionState, Throwable?, Long) -> Unit,
    private val notificationListener: (Subscription, Notification) -> Unit,
    private val alarmManager: AlarmManager
) : Connection {
    private val parser = NotificationParser()
    private val gson = Gson()
    private var errorCount = 0
    private var webSocket: WebSocket? = null
    private var state: State? = null
    private var closed = false

    private val globalId = GLOBAL_ID.incrementAndGet()
    private val listenerId = AtomicLong(0)

    private val since = AtomicReference<String?>(sinceId)
    private val baseUrl = connectionId.baseUrl
    private val topicsToSubscriptionIds = connectionId.topicsToSubscriptionIds
    private val topicsStr = topicsToSubscriptionIds.keys.joinToString(separator = ",")
    private val shortUrl = topicShortUrl(baseUrl, topicsStr)

    init {
        Log.d(TAG, "$shortUrl (gid=$globalId): New connection with global ID $globalId")
    }

    @Synchronized
    override fun start() {
        if (closed || state == State.Connecting || state == State.Connected) {
            Log.d(TAG,"$shortUrl (gid=$globalId): Not (re-)starting, because connection is marked closed/connecting/connected")
            return
        }
        if (webSocket != null) {
            webSocket!!.close(WS_CLOSE_NORMAL, "")
        }
        state = State.Connecting
        val nextListenerId = listenerId.incrementAndGet()
        val sinceId = since.get()
        val sinceVal = sinceId ?: "all"
        val urlWithSince = topicUrlWs(baseUrl, topicsStr, sinceVal)
        val request = HttpUtil.requestBuilder(urlWithSince, user, customHeaders).build()
        Log.d(TAG, "$shortUrl (gid=$globalId): Opening $urlWithSince with listener ID $nextListenerId ...")
        webSocket = httpClient.newWebSocket(request, Listener(nextListenerId))
    }

    @Synchronized
    override fun close() {
        closed = true
        if (webSocket == null) {
            Log.d(TAG,"$shortUrl (gid=$globalId): Not closing existing connection, because there is no active web socket")
            return
        }
        Log.d(TAG, "$shortUrl (gid=$globalId): Closing connection")
        state = State.Disconnected
        webSocket!!.close(WS_CLOSE_NORMAL, "")
        webSocket = null
    }

    @Synchronized
    override fun since(): String? {
        return since.get()
    }

    @Synchronized
    fun scheduleReconnect(seconds: Int) {
        if (closed || state == State.Connecting || state == State.Connected) {
            Log.d(TAG,"$shortUrl (gid=$globalId): Not rescheduling connection, because connection is marked closed/connecting/connected")
            return
        }
        state = State.Scheduled
        Log.d(TAG,"$shortUrl (gid=$globalId): Scheduling a restart in $seconds seconds (via alarm manager)")
        val reconnectTime = Calendar.getInstance()
        reconnectTime.add(Calendar.SECOND, seconds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reconnectTime.timeInMillis,
                    RECONNECT_TAG,
                    { start() },
                    null
                )
            } else {
                Log.d(TAG, "SCHEDULE_EXACT_ALARM permission denied: Failed to reschedule websocket connection")
            }
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                reconnectTime.timeInMillis,
                RECONNECT_TAG,
                { start() },
                null
            )
        }
    }

    private inner class Listener(private val id: Long) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronize("onOpen") {
                Log.d(TAG, "$shortUrl (gid=$globalId, lid=$id): Opened connection")
                state = State.Connected
                if (errorCount > 0) {
                    errorCount = 0
                }
                connectionDetailsListener(baseUrl, ConnectionState.CONNECTED, null, 0L)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronize("onMessage") {
                Log.d(TAG, "$shortUrl (gid=$globalId, lid=$id): Received message: $text")
                val notificationWithTopic = parser.parseWithTopic(text, subscriptionId = 0)
                if (notificationWithTopic == null) {
                    Log.d(TAG, "$shortUrl (gid=$globalId, lid=$id): Irrelevant or unknown message. Discarding.")
                    return@synchronize
                }
                val topic = notificationWithTopic.topic
                var notification = notificationWithTopic.notification
                val subscriptionId = topicsToSubscriptionIds[topic] ?: return@synchronize
                val subscription = repository.getSubscription(subscriptionId) ?: return@synchronize
                notification = notification.copy(subscriptionId = subscription.id)

                // Try to decrypt the message if it's in encrypted format
                notification = decryptNotificationIfNeeded(notification, subscription)

                notificationListener(subscription, notification)
                since.set(notification.id)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronize("onClosed") {
                Log.w(TAG, "$shortUrl (gid=$globalId, lid=$id): Closed connection")
                state = State.Disconnected
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronize("onFailure") {
                if (response == null) {
                    Log.e(TAG, "$shortUrl (gid=$globalId, lid=$id): Connection failed (response is null): ${t.message}", t)
                } else {
                    Log.e(TAG, "$shortUrl (gid=$globalId, lid=$id): Connection failed (response code ${response.code}, message: ${response.message}): ${t.message}", t)
                }
                if (closed) {
                    Log.d(TAG, "$shortUrl (gid=$globalId, lid=$id): Connection marked as closed. Not retrying.")
                    return@synchronize
                }
                state = State.Disconnected
                errorCount++
                val retrySeconds = RETRY_SECONDS.getOrNull(errorCount-1) ?: RETRY_SECONDS.last()
                val nextRetryTime = System.currentTimeMillis() + (retrySeconds * 1000L)
                
                // Special cases:
                // - Ignore broken connections in the UI, we don't want to show warning icons
                // - Handle authentication errors
                // - Handle servers that do not support WebSockets
                val error = when {
                    isConnectionBrokenException(t) -> null
                    isProtocolException(t) -> WebSocketNotSupportedException(response!!.code, response.message, t)
                    isResponseCode(response, 401, 403) -> NotAuthorizedException(response!!.code, response.message, t)
                    else -> t
                }
                connectionDetailsListener(baseUrl, ConnectionState.CONNECTING, error, nextRetryTime)
                scheduleReconnect(retrySeconds)
            }
        }

        private fun synchronize(tag: String, fn: () -> Unit) {
            synchronized(this) {
                if (listenerId.get() == id) {
                    fn()
                } else {
                    Log.w(TAG, "$shortUrl (gid=$globalId, lid=$id): Skipping synchronized block '$tag', because listener ID does not match ${listenerId.get()}")
                }
            }
        }
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
                Log.w(TAG, "$shortUrl: Failed to decrypt message: ${e.message}")
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

    internal enum class State {
        Scheduled, Connecting, Connected, Disconnected
    }

    companion object {
        private const val TAG = "NtfyWsConnection"
        private const val RECONNECT_TAG = "WsReconnect"
        private const val WS_CLOSE_NORMAL = 1000
        private val RETRY_SECONDS = listOf(5, 10, 15, 20, 30, 45, 60, 120)
        private val GLOBAL_ID = AtomicLong(0)
    }
}
