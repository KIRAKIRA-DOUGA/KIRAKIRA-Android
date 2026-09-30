package moe.kirakira.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable

/** Only present in the isolated test APK; never initializes the production application graph. */
class AuthUiCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { content?.invoke() }
    }

    companion object {
        var content: (@Composable () -> Unit)? = null
    }
}
