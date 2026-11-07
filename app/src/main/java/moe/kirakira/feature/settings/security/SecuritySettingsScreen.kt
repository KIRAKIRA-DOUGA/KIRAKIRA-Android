package moe.kirakira.feature.settings.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.feature.settings.SettingsColumn
import moe.kirakira.feature.settings.SettingsErrorCard
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ContentLoadingView
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.messageRes

@Composable
internal fun SecuritySettingsScreen(
    state: SecuritySettingsState,
    email: String?,
    sessionBusy: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (SecurityStep) -> Unit,
    onSelectFactor: (SecondFactor) -> Unit,
    onEdit: (SecurityField, String) -> Unit,
    onSendCode: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onFinishCodes: () -> Unit,
    onCancelDiscard: () -> Unit,
    onDismissMessage: () -> Unit,
    onCopied: (Boolean) -> Unit,
    onCancelDiscardCredentials: () -> Unit,
    onDiscardCredentials: () -> Unit,
    modifier: Modifier = Modifier,
    step: SecurityStep = state.step,
    isActive: Boolean = true,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message, isActive) {
        if (message != null && isActive) { snackbar.showSnackbar(message); onDismissMessage() }
        if (!isActive) snackbar.currentSnackbarData?.dismiss()
    }
    if (step == SecurityStep.OVERVIEW) {
        SettingsScaffold(
            title = stringResource(step.title()),
            onBack = onBack,
            shadingIcon = R.drawable.ic_icon_lock,
            modifier = modifier,
            imePadding = true,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            ContentPullToRefresh(
                isRefreshing = email != null && state.loading && state.status != null,
                onRefresh = onRefresh,
                enabled = isActive && email != null && !sessionBusy && !state.busy && state.canRefresh,
                modifier = Modifier.fillMaxSize(),
                indicatorTopPadding = padding.calculateTopPadding(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    when {
                        sessionBusy || (state.loading && state.status == null && !state.statusPending) -> ContentLoadingView(modifier = Modifier.fillMaxSize().padding(padding))
                        email == null -> ContentUnavailableView(
                            state = ContentUnavailableState.EMPTY,
                            modifier = Modifier.padding(padding),
                            title = stringResource(R.string.security_sign_in),
                            description = null,
                            iconRes = R.drawable.ic_icon_lock,
                            presentation = ContentUnavailablePresentation.PAGE,
                            primaryAction = ContentUnavailableAction(
                                stringResource(R.string.management_login), onLogin,
                            ),
                        )
                        else -> SettingsColumn(padding = padding) {
                            when {
                                state.status != null || state.statusPending -> {
                                    SecurityOverview(state, email, onOpen)
                                    state.error?.let { SettingsErrorCard(stringResource(it.messageRes())) }
                                }
                                else -> ContentUnavailableView(
                                    state = ContentUnavailableState.ERROR,
                                    description = state.error?.let { stringResource(it.messageRes()) },
                                    presentation = ContentUnavailablePresentation.INLINE,
                                    onRetry = onRefresh,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        SecurityFlowScreen(
            state = state,
            step = step,
            email = email,
            sessionBusy = sessionBusy,
            isActive = isActive,
            snackbarHostState = snackbar,
            onBack = onBack,
            onLogin = onLogin,
            onRefresh = onRefresh,
            onSelectFactor = onSelectFactor,
            onEdit = onEdit,
            onSendCode = onSendCode,
            onSubmit = onSubmit,
            onFinishCodes = onFinishCodes,
            onCopied = onCopied,
            modifier = modifier,
        )
    }
    if (state.confirmDiscardCodes && isActive) {
        ActionDialog(
            onDismissRequest = onCancelDiscard,
            icon = { Icon(painterResource(R.drawable.ic_icon_lock), contentDescription = null) },
            title = { Text(stringResource(R.string.security_leave_codes_title)) },
            text = { Text(stringResource(R.string.security_leave_codes_message)) },
            actions = {
                ShadowButton(
                    onClick = onFinishCodes,
                    destructive = true,
                ) { Text(stringResource(R.string.privacy_discard)) }
                TextButton(
                    contentPadding = ButtonDefaults.ContentPadding,
                    onClick = onCancelDiscard,
                ) {
                    Text(stringResource(R.string.profile_keep_editing))
                }
            },
        )
    }
    if (state.confirmDiscardCredentials && isActive) {
        ActionDialog(
            onDismissRequest = onCancelDiscardCredentials,
            icon = { Icon(painterResource(R.drawable.ic_icon_lock), contentDescription = null) },
            title = { Text(stringResource(if (state.confirmAbandonCompletion) R.string.write_leave_confirmation_title
                else R.string.security_leave_changes_title)) },
            text = { Text(stringResource(if (state.confirmAbandonCompletion) R.string.write_leave_confirmation_message
                else R.string.profile_discard_message)) },
            actions = {
                ShadowButton(
                    onClick = onDiscardCredentials,
                    destructive = true,
                ) { Text(stringResource(R.string.privacy_discard)) }
                TextButton(
                    contentPadding = ButtonDefaults.ContentPadding,
                    onClick = onCancelDiscardCredentials,
                ) {
                    Text(stringResource(R.string.profile_keep_editing))
                }
            },
        )
    }
}
