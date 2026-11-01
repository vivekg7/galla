package io.github.vivekg7.galla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.vivekg7.galla.ui.GallaTheme
import io.github.vivekg7.galla.ui.Shell

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
    }
}
