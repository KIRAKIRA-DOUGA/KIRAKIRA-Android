package moe.kirakira.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults as Material2ButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.appTopAppBarColors

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFieldChange: (AuthField, String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onResend: () -> Unit = {},
    onRecoveryHelp: () -> Unit = {},
    focusRequesters: Map<AuthField, FocusRequester> = remember { AuthField.entries.associateWith { FocusRequester() } },
) {
    val layoutDirection = LocalLayoutDirection.current
    val title = when (state.step) {
        AuthStep.REGISTER_PROFILE -> R.string.auth_profile_title
        AuthStep.REGISTER_CREDENTIALS -> R.string.auth_credentials_title
        AuthStep.REGISTER_INVITATION -> R.string.auth_invitation_title
        AuthStep.REGISTER_VERIFY -> R.string.auth_verify_email_title
        AuthStep.LOGIN_EMAIL -> R.string.auth_verify_email_title
        AuthStep.LOGIN_TOTP -> R.string.auth_totp_title
        AuthStep.FORGOT_EMAIL -> R.string.auth_forgot_password
        AuthStep.TOTP_HELP -> R.string.auth_recovery_title
        AuthStep.RESET_PASSWORD, AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_reset_password
        AuthStep.SAVE_SESSION -> R.string.auth_save_session
        else -> R.string.auth_sign_in
    }
    val description = when (state.step) {
        AuthStep.LOGIN -> R.string.auth_description
        AuthStep.LOGIN_EMAIL -> R.string.auth_email_code_description
        AuthStep.LOGIN_TOTP -> R.string.auth_totp_description
        AuthStep.REGISTER_PROFILE -> R.string.auth_profile_description
        AuthStep.REGISTER_CREDENTIALS -> R.string.auth_registration_description
        AuthStep.REGISTER_INVITATION -> R.string.auth_invitation_description
        AuthStep.REGISTER_VERIFY -> R.string.auth_register_verify_description
        AuthStep.FORGOT_EMAIL -> R.string.auth_forgot_description
        AuthStep.RESET_PASSWORD -> R.string.auth_reset_description
        AuthStep.TOTP_HELP -> R.string.auth_totp_recovery_description
        AuthStep.SAVE_SESSION -> R.string.auth_save_session_description
        AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_finish_reset_description
    }
    val submitLabel = when (state.step) {
        AuthStep.LOGIN, AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP -> R.string.auth_sign_in_button
        AuthStep.REGISTER_VERIFY -> R.string.auth_register
        AuthStep.RESET_PASSWORD -> R.string.auth_reset_password
        AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_retry
        else -> R.string.auth_continue
    }
    val stepIcon = when (state.step) {
        AuthStep.LOGIN -> R.drawable.logo_kirakira
        AuthStep.LOGIN_EMAIL, AuthStep.REGISTER_VERIFY -> R.drawable.ic_symbol_mail
        AuthStep.LOGIN_TOTP -> R.drawable.ic_symbol_shield
        AuthStep.REGISTER_PROFILE -> R.drawable.ic_symbol_person
        AuthStep.REGISTER_CREDENTIALS -> R.drawable.ic_symbol_person_add
        AuthStep.REGISTER_INVITATION -> R.drawable.ic_symbol_confirmation_number
        AuthStep.FORGOT_EMAIL -> R.drawable.ic_symbol_manage_accounts
        AuthStep.RESET_PASSWORD -> R.drawable.ic_symbol_lock_reset
        AuthStep.TOTP_HELP -> R.drawable.ic_symbol_help
        AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> R.drawable.ic_symbol_save
    }
    val registrationStep = when (state.step) {
        AuthStep.REGISTER_PROFILE -> 1
        AuthStep.REGISTER_CREDENTIALS -> 2
        AuthStep.REGISTER_INVITATION -> 3
        AuthStep.REGISTER_VERIFY -> 4
        else -> 0
    }
    val signInLabel = stringResource(title)
    val signingInDescription = stringResource(R.string.auth_working)
    FrostedScaffold(
        modifier = modifier.fillMaxSize().testTag("auth_${state.step.name}"),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                colors = appTopAppBarColors(),
                title = {
                    Icon(
                        painter = painterResource(stepIcon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (state.step == AuthStep.LOGIN) 40.dp else 32.dp),
                    )
                },
                navigationIcon = {
                    if (state.step != AuthStep.LOGIN) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("auth_back")) {
                            Icon(
                                painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("auth_close")) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_close),
                            contentDescription = stringResource(R.string.auth_close),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    // Keep the viewport edge-to-edge; the final inset scrolls with the button.
                    .padding(top = innerPadding.calculateTopPadding() + 24.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (registrationStep > 0) {
                        Text(stringResource(R.string.auth_step_progress, registrationStep, 4),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { registrationStep / 4f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(28.dp))
                    }
                    Text(
                        text = signInLabel,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                    AuthForm(
                        state = state,
                        passwordVisible = passwordVisible,
                        emailFocusRequester = emailFocusRequester,
                        focusRequesters = focusRequesters,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onPasswordVisibilityChange = onPasswordVisibilityChange,
                        onSubmit = onSubmit,
                        onFieldChange = onFieldChange,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.step == AuthStep.LOGIN) {
                        TextButton(onClick = onForgotPassword, enabled = state.canEdit) {
                            Text(stringResource(R.string.auth_forgot_password))
                        }
                        TextButton(onClick = onRegister, enabled = state.canEdit, modifier = Modifier.testTag("auth_register")) {
                            Icon(painterResource(R.drawable.ic_symbol_add), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_register))
                        }
                    } else {
                        if (state.step in setOf(AuthStep.LOGIN_EMAIL, AuthStep.REGISTER_VERIFY, AuthStep.RESET_PASSWORD)) {
                            TextButton(onClick = onResend, enabled = state.canEdit && state.resendSeconds == 0) {
                                Text(
                                    if (state.resendSeconds > 0) {
                                        stringResource(R.string.auth_resend_countdown, state.resendSeconds)
                                    } else {
                                        stringResource(R.string.auth_resend)
                                    },
                                )
                            }
                        }
                        if (state.step == AuthStep.TOTP_HELP) {
                            TextButton(onClick = onRecoveryHelp) { Text(stringResource(R.string.auth_contact_support)) }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.noticeRes?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.errorRes != null || (state.submission == AuthSubmission.FAILED && state.fieldErrors.isEmpty())) {
                        Text(
                            text = stringResource(state.errorRes ?: R.string.auth_sign_in_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.step != AuthStep.TOTP_HELP) {
                        ShadowButton(
                            onClick = onSubmit,
                            enabled = state.canSubmit,
                            shapes = ButtonDefaults.shapesFor(56.dp),
                            contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_submit")
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
                                        stringResource(submitLabel)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = if (state.isSubmitting) {
                                        Modifier.alpha(0f).clearAndSetSemantics { }
                                    } else {
                                        Modifier
                                    },
                                )
                                if (state.isSubmitting) {
                                    IndeterminateCircularProgressIndicator(
                                        modifier = Modifier.size(Material2ButtonDefaults.IconSize).clearAndSetSemantics { },
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
