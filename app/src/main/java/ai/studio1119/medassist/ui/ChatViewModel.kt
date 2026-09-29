package ai.studio1119.medassist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.studio1119.medassist.data.StreamEvent
import ai.studio1119.medassist.data.StreamingClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One chat bubble in the UI.
data class UiMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val disease: String? = null,
    val sourcePages: List<Int> = emptyList(),
    val isStreaming: Boolean = false,  // still typing out (shows the cursor)
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,    // waiting for the first token
    val isStreaming: Boolean = false,  // an answer is still being typed out
) {
    /** True while a reply is in flight; the input bar is disabled meanwhile. */
    val isBusy: Boolean get() = isLoading || isStreaming
}

// Typewriter pacing: one step per frame, revealing a few characters at a time.
// The step grows with the backlog, so a burst from the network is typed out
// smoothly instead of appearing all at once, without falling far behind.
private const val FRAME_MS = 16L
private const val MIN_CHARS_PER_FRAME = 2
private const val MAX_CHARS_PER_FRAME = 12
private const val CATCH_UP_FRAMES = 30

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId = 0L

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isBusy) return

        // Show the user's message immediately; show "Thinking..." until tokens arrive.
        _state.value = _state.value.copy(
            messages = _state.value.messages + UiMessage(nextId++, trimmed, fromUser = true),
            isLoading = true,
            isStreaming = true,
        )

        // Everything below runs on the main thread (viewModelScope), so the
        // collector and the typewriter never touch this state concurrently.
        viewModelScope.launch {
            val assistantId = nextId++
            val received = StringBuilder()   // everything the server has sent
            var shown = 0                    // how much of it is on screen
            var disease: String? = null
            var pages: List<Int> = emptyList()
            var assistantAdded = false
            var networkDone = false
            var error: String? = null

            fun upsertAssistant(streaming: Boolean) {
                val msg = UiMessage(
                    id = assistantId,
                    text = received.substring(0, shown),
                    fromUser = false,
                    disease = disease,
                    sourcePages = pages,
                    isStreaming = streaming,
                )
                val msgs = _state.value.messages
                _state.value = if (assistantAdded) {
                    _state.value.copy(messages = msgs.map { if (it.id == assistantId) msg else it })
                } else {
                    assistantAdded = true
                    _state.value.copy(isLoading = false, messages = msgs + msg)
                }
            }

            val typewriter = launch {
                while (true) {
                    val backlog = received.length - shown
                    if (backlog > 0) {
                        val step = (backlog / CATCH_UP_FRAMES)
                            .coerceIn(MIN_CHARS_PER_FRAME, MAX_CHARS_PER_FRAME)
                        shown = minOf(received.length, shown + step)
                        upsertAssistant(streaming = true)
                    } else if (networkDone) {
                        break
                    }
                    delay(FRAME_MS)
                }
            }

            StreamingClient.streamChat(trimmed).collect { event ->
                when (event) {
                    is StreamEvent.Meta -> {
                        disease = event.disease
                        pages = event.sourcePages
                    }
                    is StreamEvent.Token -> received.append(event.text)
                    is StreamEvent.Done -> Unit
                    is StreamEvent.Error -> error = event.message
                }
            }
            // The stream is over (done, error, or a dropped connection); let the
            // typewriter finish what already arrived.
            networkDone = true
            typewriter.join()

            if (assistantAdded) {
                upsertAssistant(streaming = false)  // hide the cursor
            }
            val failure = when {
                error != null ->
                    "Sorry, something went wrong reaching MedAssist. " +
                        "Is the backend running? ($error)"
                !assistantAdded -> "Sorry, I didn't get an answer. Please try again."
                else -> null
            }
            _state.value = _state.value.copy(
                isLoading = false,
                isStreaming = false,
                messages = _state.value.messages +
                    listOfNotNull(failure?.let { UiMessage(nextId++, it, fromUser = false) }),
            )
        }
    }
}
