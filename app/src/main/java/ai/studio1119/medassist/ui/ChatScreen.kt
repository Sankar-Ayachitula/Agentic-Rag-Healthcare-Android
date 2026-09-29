package ai.studio1119.medassist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.studio1119.medassist.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // Follow the reply as it types out, like Claude/ChatGPT: stay pinned to the
    // bottom unless the user scrolls up to read; scrolling back down re-pins.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling) follow = !canScrollForward
            }
    }
    // A new message from the user always jumps back to the bottom.
    LaunchedEffect(state.messages.count { it.fromUser }) { follow = true }

    val itemCount = state.messages.size + if (state.isLoading) 1 else 0
    LaunchedEffect(itemCount, state.messages.lastOrNull()?.text) {
        // Scroll by "as far as possible": the list clamps it to the very end,
        // even while the last item is taller than the screen and still growing.
        if (follow && itemCount > 0) listState.scrollBy(SCROLL_TO_END)
    }

    Scaffold(
        containerColor = LavenderTint,
        topBar = {
            TopAppBar(
                title = { Text("MedAssist", color = AssistantText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LavenderTint),
            )
        },
        bottomBar = { InputBar(enabled = !state.isBusy, onSend = vm::send) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            items(state.messages, key = { it.id }) { msg ->
                AppearAnimated(slide = msg.fromUser) { MessageBubble(msg) }
            }
            if (state.isLoading) {
                item(key = "typing") { TypingIndicator() }
            }
        }
    }
}

private const val SCROLL_TO_END = 1_000_000f

/**
 * New messages fade in; the user's also slide up into place. Done purely at
 * draw time (graphicsLayer) so it never disturbs layout: the assistant's reply
 * grows every frame, and AnimatedVisibility mis-positioned it while it did.
 */
@Composable
private fun AppearAnimated(slide: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(220)) }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            if (slide) translationY = (1f - progress.value) * 16.dp.toPx()
        }
    ) { content() }
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
        // Assistant: full-width, no bubble (Claude/ChatGPT style). The text types
        // out live, rendered as Markdown, with a blinking cursor while streaming.
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
            val cursorAlpha = if (msg.isStreaming) blinkingAlpha() else 0f
            // Mid-stream the typewriter can stop between the two * of "**";
            // hide a dangling marker so it never flashes on screen.
            val visibleText = if (msg.isStreaming) msg.text.trimEnd('*', '`', '#') else msg.text
            val rendered = remember(visibleText) { markdownToAnnotated(visibleText, CodeBackground) }
            val withCursor = buildAnnotatedString {
                append(rendered)
                if (msg.isStreaming) {
                    withStyle(SpanStyle(color = AccentPurple.copy(alpha = cursorAlpha))) {
                        append(" ●")
                    }
                }
            }
            Text(withCursor, color = AssistantText, fontSize = 16.sp, lineHeight = 23.sp)
            if (!msg.isStreaming && msg.sourcePages.isNotEmpty()) {
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
private fun blinkingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    return alpha
}

/** Three dots pulsing in sequence while we wait for the first token. */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 180),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val scale = 0.7f + 0.3f * phase
                        scaleX = scale
                        scaleY = scale
                        translationY = -4.dp.toPx() * phase
                    }
                    .alpha(0.35f + 0.65f * phase)
                    .background(AccentPurple, CircleShape)
            )
        }
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
    val canSend = enabled && message.isNotBlank()
    val submit = {
        // Keep the typed text if we can't send yet (e.g. a reply is still streaming).
        if (canSend) {
            onSend(message)
            message = ""
        }
    }

    Surface(color = LavenderTint, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()  // stay above the gesture bar (edge-to-edge)
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
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = submit,
                enabled = canSend,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = UserBubble),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
