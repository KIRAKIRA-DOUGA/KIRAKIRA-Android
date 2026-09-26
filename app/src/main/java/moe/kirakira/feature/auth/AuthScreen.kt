package moe.kirakira.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import moe.kirakira.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthScreen(
    state: AuthUiState,
    passwordVisible: Boolean,
    snackbarHostState: SnackbarHostState,
    emailFocusRequester: FocusRequester,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: () -> Unit,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val signInLabel = stringResource(R.string.auth_sign_in)
    val signingInDescription = stringResource(R.string.auth_signing_in)
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_close),
                        contentDescription = stringResource(R.string.auth_close),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    // Keep the viewport edge-to-edge; the final inset scrolls with the button.
                    .padding(top = 24.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(R.drawable.logo_kirakira),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(96.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.auth_sign_in),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.auth_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                    LoginForm(
                        state = state,
                        passwordVisible = passwordVisible,
                        emailFocusRequester = emailFocusRequester,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onPasswordVisibilityChange = onPasswordVisibilityChange,
                        onSubmit = onSubmit,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRegister, enabled = !state.isSubmitting) {
                        Icon(painterResource(R.drawable.ic_symbol_add), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.auth_register))
                    }
                    Spacer(Modifier.height(32.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.submission == AuthSubmission.FAILED) {
                        Text(
                            text = stringResource(R.string.auth_sign_in_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        shapes = ButtonDefaults.shapesFor(56.dp),
                        contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics {
                                if (state.isSubmitting) {
                                    contentDescription = signInLabel
                                    stateDescription = signingInDescription
                                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                    liveRegion = LiveRegionMode.Polite
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier.heightIn(min = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Reserve the label's height even at large font scales while loading.
                            Text(
                                text = if (state.submission == AuthSubmission.FAILED) {
                                    stringResource(R.string.auth_retry)
                                } else {
                                    stringResource(R.string.auth_sign_in_button)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = if (state.isSubmitting) {
                                    Modifier.alpha(0f).clearAndSetSemantics { }
                                } else {
                                    Modifier
                                },
                            )
                            if (state.isSubmitting) {
                                LoadingIndicator(modifier = Modifier.size(24.dp).clearAndSetSemantics { })
                            }
                        }
                    }
                }
            }
        }
    }
}
