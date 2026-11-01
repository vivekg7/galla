package io.github.vivekg7.galla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.vivekg7.galla.data.Drive
import io.github.vivekg7.galla.ui.GallaTheme
import io.github.vivekg7.galla.ui.Shell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GallaTheme {
                // The Surface sets the text and icon colour for everything inside.
                Surface(color = MaterialTheme.colorScheme.background) { Shell() }
            }
        }
        // Syncs only while the app is on screen: a shop's phone has Galla open while it sells.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { app.sync.whileOpen { Drive(app, it) } } }
    }
}
