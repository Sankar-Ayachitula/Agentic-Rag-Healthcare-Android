package ai.studio1119.medassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.studio1119.medassist.ui.ChatScreen
import ai.studio1119.medassist.ui.ChatViewModel
import ai.studio1119.medassist.ui.theme.MedAssistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedAssistTheme {
                val vm: ChatViewModel = viewModel()
                ChatScreen(vm)
            }
        }
    }
}
