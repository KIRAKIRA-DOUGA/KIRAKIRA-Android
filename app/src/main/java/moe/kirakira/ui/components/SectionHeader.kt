package moe.kirakira.ui.components

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Preview(name = "Section header · Light", showBackground = true)
@Preview(name = "Section header · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            SectionHeader(title = stringResource(R.string.settings_general))
        }
    }
}
