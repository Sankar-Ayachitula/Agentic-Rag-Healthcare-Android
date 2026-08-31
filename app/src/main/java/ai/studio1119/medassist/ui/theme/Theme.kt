package ai.studio1119.medassist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
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

private val DarkColors = darkColorScheme(
    primary = AccentPurple,
    secondary = AccentPurple,
)

@Composable
fun MedAssistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
