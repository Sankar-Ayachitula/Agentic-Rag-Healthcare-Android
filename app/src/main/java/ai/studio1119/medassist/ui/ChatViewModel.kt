package ai.studio1119.medassist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.studio1119.medassist.data.ApiClient
import ai.studio1119.medassist.data.ChatRequest
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
    val animate: Boolean = false,   // typewriter effect for fresh assistant replies
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
)

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isLoading) return

        // Show the user's message immediately, flip on the loading indicator.
        _state.value = _state.value.copy(
            messages = _state.value.messages + UiMessage(trimmed, fromUser = true),
            isLoading = true,
        )

        viewModelScope.launch {
            val reply = try {
                val res = ApiClient.api.chat(ChatRequest(trimmed))
                UiMessage(
                    text = res.answer,
                    fromUser = false,
                    disease = res.disease,
                    sourcePages = res.sources.orEmpty().mapNotNull { it.page },
                    animate = true,
                )
            } catch (e: Exception) {
                UiMessage(
                    text = "Sorry, I couldn't reach the server. Is the backend running? (${e.message})",
                    fromUser = false,
                )
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages + reply,
                isLoading = false,
            )
        }
    }
}
