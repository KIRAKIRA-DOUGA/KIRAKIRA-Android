package moe.kirakira.feature.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.ui.theme.KIRAKIRATheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TestScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun TestScreenPreview() {
    KIRAKIRATheme {
        TestScreen()
    }
}
