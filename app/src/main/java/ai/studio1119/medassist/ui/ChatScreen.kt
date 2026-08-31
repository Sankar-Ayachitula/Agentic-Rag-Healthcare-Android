package ai.studio1119.medassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.studio1119.medassist.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message.
    LaunchedEffect(state.messages.size, state.isLoading) {
        val count = state.messages.size + if (state.isLoading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = LavenderTint,
        topBar = {
            TopAppBar(
                title = { Text("MedAssist", color = AssistantText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LavenderTint),
            )
        },
        bottomBar = { InputBar(enabled = !state.isLoading, onSend = vm::send) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { EmptyState() }
            }
            items(state.messages) { msg -> MessageBubble(msg) }
            if (state.isLoading) {
                item { TypingIndicator() }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    if (msg.fromUser) {
        // User: right-aligned rounded bubble.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(UserBubble, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, color = UserBubbleText, fontSize = 16.sp)
            }
        }
    } else {
        // Assistant: full-width, no bubble (Claude/ChatGPT style).
        Column(Modifier.fillMaxWidth()) {
            msg.disease?.let { d ->
                AssistChip(
                    onClick = {},
                    label = { Text("Likely: $d") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White,
                        labelColor = AccentPurple,
                    ),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (msg.animate) {
                TypewriterText(msg.text)
            } else {
                Text(msg.text, color = AssistantText, fontSize = 16.sp, lineHeight = 23.sp)
            }
            if (msg.sourcePages.isNotEmpty()) {
                Text(
                    "Sources: pages " + msg.sourcePages.distinct().joinToString(", "),
                    color = SubtleGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TypewriterText(fullText: String) {
    var shown by remember(fullText) { mutableStateOf("") }
    LaunchedEffect(fullText) {
        val words = fullText.split(" ")
        val sb = StringBuilder()
        words.forEachIndexed { i, w ->
            sb.append(if (i == 0) w else " $w")
            shown = sb.toString()
            delay(30)   // per-word reveal; tune to taste
        }
    }
    Text(shown, color = AssistantText, fontSize = 16.sp, lineHeight = 23.sp)
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = AccentPurple,
        )
        Spacer(Modifier.width(8.dp))
        Text("Thinking...", color = SubtleGray, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MedAssist", color = AssistantText, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Describe your symptoms or ask a medical question.",
            color = SubtleGray,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun InputBar(enabled: Boolean, onSend: (String) -> Unit) {
    var message by remember { mutableStateOf("") }

    Surface(color = LavenderTint, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message MedAssist") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = InputBar,
                    unfocusedContainerColor = InputBar,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(message); message = ""
                }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { onSend(message); message = "" },
                enabled = enabled && message.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = UserBubble),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
