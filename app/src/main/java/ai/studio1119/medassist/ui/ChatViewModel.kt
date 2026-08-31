package ai.studio1119.medassist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.studio1119.medassist.data.StreamEvent
import ai.studio1119.medassist.data.StreamingClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One chat bubble in the UI.
data class UiMessage(
    val text: String,
    val fromUser: Boolean,
    val disease: String? = null,
    val sourcePages: List<Int> = emptyList(),
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,   // waiting for the first token
)

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isLoading) return

        // Show the user's message immediately; show "Thinking..." until tokens arrive.
        _state.value = _state.value.copy(
            messages = _state.value.messages + UiMessage(trimmed, fromUser = true),
            isLoading = true,
        )

        viewModelScope.launch {
            val builder = StringBuilder()
            var disease: String? = null
            var pages: List<Int> = emptyList()
            var assistantAdded = false

            fun upsertAssistant() {
                val msg = UiMessage(
                    text = builder.toString(),
                    fromUser = false,
                    disease = disease,
                    sourcePages = pages,
                )
                val msgs = _state.value.messages
                _state.value = if (assistantAdded) {
                    // replace the last (streaming) assistant message
                    _state.value.copy(messages = msgs.dropLast(1) + msg)
                } else {
                    assistantAdded = true
                    _state.value.copy(isLoading = false, messages = msgs + msg)
                }
            }

            StreamingClient.streamChat(trimmed).collect { event ->
                when (event) {
                    is StreamEvent.Meta -> {
                        disease = event.disease
                        pages = event.sourcePages
                    }
                    is StreamEvent.Token -> {
                        builder.append(event.text)
                        upsertAssistant()   // grows the visible message live
                    }
                    is StreamEvent.Done -> {
                        if (!assistantAdded) upsertAssistant()  // empty-answer safety
                    }
                    is StreamEvent.Error -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            messages = _state.value.messages + UiMessage(
                                "Sorry, I couldn't reach the server. Is the backend " +
                                    "running? (${event.message})",
                                fromUser = false,
                            ),
                        )
                    }
                }
            }
        }
    }
}
