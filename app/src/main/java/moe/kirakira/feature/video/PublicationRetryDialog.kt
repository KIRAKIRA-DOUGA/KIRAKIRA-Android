package moe.kirakira.feature.video

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ShadowButton

@Composable
internal fun PublicationRetryDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.publication_unconfirmed_title)) },
        text = { Text(stringResource(R.string.publication_unconfirmed_message)) },
        modifier = modifier,
        actions = {
            ShadowButton(onClick = onCheck, enabled = !busy) {
                Text(stringResource(R.string.publication_check))
            }
            TextButton(onClick = onResend, enabled = !busy) {
                Text(stringResource(R.string.publication_resend))
            }
        },
    )
}
