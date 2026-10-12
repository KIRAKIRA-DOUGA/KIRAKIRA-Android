@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.security.SecurityStatus
import moe.kirakira.feature.settings.SettingsRadioItem
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowError
import moe.kirakira.ui.components.AccountFlowFeedback
import moe.kirakira.ui.components.AccountFlowSection
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.semanticColors

@Composable
internal fun SecurityOverview(state: SecuritySettingsState, email: String, onOpen: (SecurityStep) -> Unit) {
    SecurityStatusBanner(state)
    val canEdit = !state.working && state.status != null && !state.statusPending
    SettingsSection(stringResource(R.string.security_account_section)) {
        SecurityItem(R.drawable.ic_symbol_mail, stringResource(R.string.auth_email), email, 0, 2,
            enabled = canEdit, onClick = { onOpen(SecurityStep.EMAIL) })
        SecurityItem(R.drawable.ic_symbol_password, stringResource(R.string.security_password),
            stringResource(R.string.security_change_password), 1, 2, enabled = canEdit,
            onClick = { onOpen(SecurityStep.PASSWORD) })
    }
    SettingsSection(stringResource(R.string.security_two_factor_section)) {
        SecurityItem(R.drawable.ic_symbol_lock, stringResource(R.string.security_two_factor_section),
            stringResource(state.status?.factor?.label() ?: R.string.security_status_pending),
            0, 1, enabled = !state.working, onClick = { onOpen(SecurityStep.TWO_FACTOR) })
    }
}

@Composable
private fun SecurityStatusBanner(state: SecuritySettingsState) {
    val factor = state.status?.factor
    val protected = !state.statusPending && factor != null && factor != SecondFactor.NONE
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = when {
                    state.statusPending -> R.drawable.ic_symbol_lock
                    factor == SecondFactor.EMAIL -> R.drawable.ic_symbol_mail
                    factor == SecondFactor.TOTP -> R.drawable.ic_symbol_lock
                    else -> R.drawable.ic_symbol_error
                },
                shape = MaterialShapes.Cookie12Sided.toShape(),
                tone = if (protected) IconBadgeTone.PRIMARY else IconBadgeTone.NEUTRAL,
                size = 64.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(when {
                        state.statusPending -> R.string.security_status_pending
                        protected -> R.string.security_protected
                        else -> R.string.security_unprotected
                    }),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (protected) {
                    Text(
                        text = stringResource(factor.label()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    TwoFactorEnabledBadge()
                }
            }
        }
    }
}

@Composable
internal fun TwoFactorManagement(
    state: SecuritySettingsState,
    email: String,
    isActive: Boolean,
    onSelectFactor: (SecondFactor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status ?: return
    val flow = state.twoFactor as? TwoFactorFlow.Manage ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
        when (status.factor) {
            SecondFactor.NONE -> {
                Text(
                    text = stringResource(R.string.security_not_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccountFlowSection(title = stringResource(R.string.security_verification_method)) {
                    ConnectedListGroup(Modifier.selectableGroup()) {
                        SettingsRadioItem(
                            title = stringResource(R.string.security_email_authenticator),
                            selected = flow.selectedFactor == SecondFactor.EMAIL,
                            onClick = { onSelectFactor(SecondFactor.EMAIL) },
                            index = 0,
                            count = 2,
                            icon = R.drawable.ic_symbol_mail,
                            supporting = email,
                            enabled = isActive && !state.working,
                        )
                        SettingsRadioItem(
                            title = stringResource(R.string.security_totp_authenticator),
                            selected = flow.selectedFactor == SecondFactor.TOTP,
                            onClick = { onSelectFactor(SecondFactor.TOTP) },
                            index = 1,
                            count = 2,
                            icon = R.drawable.ic_symbol_lock,
                            supporting = if (flow.draft is TotpDraft.Available) {
                                stringResource(R.string.security_continue_binding)
                            } else {
                                null
                            },
                            enabled = isActive && !state.working && flow.draft != TotpDraft.Unavailable,
                        )
                    }
                }
            }
            SecondFactor.EMAIL, SecondFactor.TOTP -> {
                TwoFactorEnabledCard(status, email)
            }
        }
        AccountFlowFeedback(flow.notice?.let { stringResource(it) }) { AccountFlowError(it) }
    }
}

@Composable
private fun TwoFactorEnabledCard(
    status: SecurityStatus,
    email: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = if (status.factor == SecondFactor.EMAIL) R.drawable.ic_symbol_mail
                    else R.drawable.ic_symbol_lock,
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                    tone = IconBadgeTone.PRIMARY,
                    size = 64.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(status.factor.label()),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    TwoFactorEnabledBadge()
                }
            }
            if (status.factor == SecondFactor.EMAIL || status.totpCreationDateTime != null) {
                HorizontalDivider(color = scheme.outlineVariant)
                if (status.factor == SecondFactor.EMAIL) {
                    SecurityCurrentEmail(email)
                } else {
                    status.totpCreationDateTime?.let { addedAt ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.security_binding_date),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                text = DateFormat.getDateInstance().format(Date(addedAt)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoFactorEnabledBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.semanticColors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.success,
        contentColor = colors.onSuccess,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_symbol_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(stringResource(R.string.security_enabled), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SecurityItem(icon: Int, title: String, summary: String, index: Int, count: Int,
    enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shapes = connectedListItemShapes(index, count),
        leadingContent = {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        },
        supportingContent = { Text(summary) },
        trailingContent = {
            if (busy) {
                IndeterminateCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_symbol_chevron_right), null, Modifier.size(24.dp))
            }
        },
        content = { Text(title) },
    )
}
