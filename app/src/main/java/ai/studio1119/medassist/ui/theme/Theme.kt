package ai.studio1119.medassist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = UserBubble,
    onPrimary = UserBubbleText,
    secondary = AccentPurple,
    background = LavenderTint,
    surface = LavenderTint,
    onBackground = AssistantText,
    onSurface = AssistantText,
)

// Light only: the chat screen paints fixed light surfaces (lavender background,
// white input bar), so a dark scheme would put light text on white.
@Composable
fun MedAssistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
