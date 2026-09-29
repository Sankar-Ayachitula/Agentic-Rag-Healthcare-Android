package ai.studio1119.medassist.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One event from the backend's SSE stream. */
sealed interface StreamEvent {
    data class Meta(val disease: String?, val sourcePages: List<Int>) : StreamEvent
    data class Token(val text: String) : StreamEvent
    data object Done : StreamEvent
    data class Error(val message: String) : StreamEvent
}

object StreamingClient {
    // 10.0.2.2 = the emulator's alias for your machine's localhost.
    private const val BASE_URL = "http://10.0.2.2:8000/"
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // 0 = no read timeout; the stream is long-lived
        .build()

    /**
     * Streams a chat answer from POST /chat/stream as a Flow of StreamEvents:
     * one Meta, then many Tokens, then Done (or an Error).
     */
    fun streamChat(message: String): Flow<StreamEvent> = callbackFlow {
        val payload = JSONObject().put("message", message).toString()
        val request = Request.Builder()
            .url(BASE_URL + "chat/stream")
            .post(payload.toRequestBody(JSON))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                try {
                    val obj = JSONObject(data)
                    when (obj.getString("type")) {
                        "meta" -> {
                            val disease =
                                if (obj.isNull("disease")) null else obj.getString("disease")
                            val pages = obj.optJSONArray("sources")?.let { arr ->
                                (0 until arr.length()).map { arr.getInt(it) }
                            } ?: emptyList()
                            trySend(StreamEvent.Meta(disease, pages))
                        }
                        "token" -> trySend(StreamEvent.Token(obj.getString("text")))
                        "done" -> {
                            trySend(StreamEvent.Done)
                            close()
                        }
                        "error" -> {
                            trySend(StreamEvent.Error(obj.optString("message", "server error")))
                            close()
                        }
                    }
                } catch (_: Exception) {
                    // ignore a malformed line; keep streaming
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val reason = when {
                    response != null && !response.isSuccessful -> "HTTP ${response.code}"
                    else -> t?.message ?: "connection failed"
                }
                trySend(StreamEvent.Error(reason))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}
