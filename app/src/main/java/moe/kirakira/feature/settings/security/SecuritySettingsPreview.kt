package moe.kirakira.feature.settings.security

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.security.SecurityStatus
import moe.kirakira.data.security.TotpSecrets
import moe.kirakira.data.security.TotpSetup
import moe.kirakira.ui.theme.KIRAKIRATheme

// Synthetic fixtures belong only to these previews; filled codes are fictional and passwords stay empty.
private const val PREVIEW_EMAIL = "preview@example.com"
private const val PREVIEW_NEW_EMAIL = "new@example.com"
private const val PREVIEW_BINDING_DATE = 1767225600000L
private const val PREVIEW_CODE = "123456"

@Preview(name = "Security · Guest", showBackground = true)
@Preview(name = "Security · Guest, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SecuritySettingsPreview() {
    SecurityPreviewContent(SecuritySettingsState(), email = null)
}

internal class SecurityStepPreviewProvider : PreviewParameterProvider<SecurityStep> {
    override val values = SecurityStep.entries.asSequence().filter { it != SecurityStep.OVERVIEW }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Security · Wizard steps", locale = "en", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "账号安全 · 向导步骤", locale = "zh", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "Security steps · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 411, heightDp = 891)
@Preview(name = "Security steps · Narrow, large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Security steps · Wide", widthDp = 840, heightDp = 600)
@Composable
private fun SecurityStepPreview(@PreviewParameter(SecurityStepPreviewProvider::class) step: SecurityStep) {
    SecurityPreviewContent(securityStepPreviewState(step))
}

internal class SecurityFieldErrorsPreviewProvider : PreviewParameterProvider<Map<SecurityField, Int>> {
    override val values = sequenceOf(
        mapOf(SecurityField.NEW_EMAIL_CODE to R.string.security_code_invalid),
        mapOf(
            SecurityField.PASSWORD to R.string.security_password_required,
            SecurityField.CODE to R.string.security_code_invalid,
            SecurityField.NEW_EMAIL_CODE to R.string.security_code_invalid,
        ),
    )

    override fun getDisplayName(index: Int): String = if (index == 0) "Single field error" else "Multiple field errors"
}

@Preview(name = "Security · Verification, long email", locale = "en", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "账号安全 · 验证码错误与倒计时", locale = "zh", widthDp = 411, heightDp = 891)
@Preview(name = "Security · Verification errors, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SecurityVerificationPreview(
    @PreviewParameter(SecurityFieldErrorsPreviewProvider::class) fieldErrors: Map<SecurityField, Int>,
) {
    SecurityPreviewContent(
        state = securityStepPreviewState(SecurityStep.VERIFY_EMAIL).copy(
            credentials = CredentialFlow.VerifyEmail(
                SecurityForm.Empty.with(SecurityField.EMAIL, "new.preview.with.a.long.email.address@example.com"),
            ),
            currentCooldown = 59,
            newCooldown = 29,
            fieldErrors = fieldErrors,
        ),
        email = "current.preview.with.a.long.email.address@example.com",
    )
}

internal class SecurityFilledCodePreviewProvider : PreviewParameterProvider<SecuritySettingsState> {
    override val values = sequenceOf(
        SecuritySettingsState(
            page = SecurityPage.EMAIL,
            status = SecurityStatus(SecondFactor.EMAIL),
            credentials = CredentialFlow.VerifyEmail(
                SecurityForm.Empty
                    .with(SecurityField.EMAIL, PREVIEW_NEW_EMAIL)
                    .with(SecurityField.CODE, PREVIEW_CODE)
                    .with(SecurityField.NEW_EMAIL_CODE, "654321"),
            ),
        ),
        SecuritySettingsState(
            page = SecurityPage.TWO_FACTOR,
            status = SecurityStatus(SecondFactor.NONE),
            twoFactor = TwoFactorFlow.ConfirmTotp(
                setup = previewTotpSetup(),
                form = SecurityForm.Empty.with(SecurityField.CODE, PREVIEW_CODE),
            ),
        ),
        SecuritySettingsState(
            page = SecurityPage.EMAIL,
            status = SecurityStatus(SecondFactor.TOTP),
            credentials = CredentialFlow.VerifyEmail(
                SecurityForm.Empty
                    .with(SecurityField.EMAIL, PREVIEW_NEW_EMAIL)
                    .with(SecurityField.CODE, PREVIEW_CODE)
                    .with(SecurityField.NEW_EMAIL_CODE, "654321"),
            ),
        ),
        SecuritySettingsState(
            page = SecurityPage.PASSWORD,
            status = SecurityStatus(SecondFactor.EMAIL),
            credentials = CredentialFlow.VerifyPassword(SecurityForm.Empty.with(SecurityField.CODE, PREVIEW_CODE)),
        ),
        SecuritySettingsState(
            page = SecurityPage.PASSWORD,
            status = SecurityStatus(SecondFactor.TOTP),
            credentials = CredentialFlow.VerifyPassword(SecurityForm.Empty.with(SecurityField.CODE, PREVIEW_CODE)),
        ),
    )

    override fun getDisplayName(index: Int): String = listOf(
        "Email change · Email codes",
        "Authenticator setup · Code",
        "Email change · Authenticator code",
        "Password change · Email code",
        "Password change · Authenticator code",
    )[index]
}

@Preview(name = "Security · Filled verification code", locale = "en", widthDp = 411, heightDp = 891)
@Preview(name = "账号安全 · 验证码已填写", locale = "zh", widthDp = 411, heightDp = 891)
@Preview(name = "Security · Filled code, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 411, heightDp = 891)
@Preview(name = "账号安全 · 验证码已填写、大字体", locale = "zh", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun SecurityFilledCodePreview(
    @PreviewParameter(SecurityFilledCodePreviewProvider::class) state: SecuritySettingsState,
) {
    SecurityPreviewContent(state)
}

internal class EnabledFactorPreviewProvider : PreviewParameterProvider<SecondFactor> {
    override val values = SecondFactor.entries.asSequence().filter { it != SecondFactor.NONE }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "2FA · Enabled", locale = "en", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "二步验证 · 已开启", locale = "zh", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun TwoFactorEnabledPreview(@PreviewParameter(EnabledFactorPreviewProvider::class) factor: SecondFactor) {
    SecurityPreviewContent(
        SecuritySettingsState(
            page = SecurityPage.TWO_FACTOR,
            status = SecurityStatus(
                factor = factor,
                totpCreationDateTime = PREVIEW_BINDING_DATE.takeIf { factor == SecondFactor.TOTP },
            ),
        ),
    )
}

@Composable
private fun SecurityPreviewContent(state: SecuritySettingsState, email: String? = PREVIEW_EMAIL) {
    KIRAKIRATheme {
        SecuritySettingsScreen(
            state = state,
            email = email,
            sessionBusy = false,
            onBack = {},
            onLogin = {},
            onRefresh = {},
            onOpen = {},
            onSelectFactor = {},
            onEdit = { _, _ -> },
            onSendCode = {},
            onSubmit = {},
            onFinishCodes = {},
            onCancelDiscard = {},
            onDismissMessage = {},
            onCopied = {},
            onCancelDiscardCredentials = {},
            onDiscardCredentials = {},
        )
    }
}

private fun securityStepPreviewState(step: SecurityStep): SecuritySettingsState {
    val state = SecuritySettingsState(status = SecurityStatus(SecondFactor.NONE))
    return when (step) {
        SecurityStep.OVERVIEW -> state
        SecurityStep.EMAIL -> state.copy(page = SecurityPage.EMAIL, credentials = CredentialFlow.EditEmail())
        SecurityStep.VERIFY_EMAIL -> state.copy(
            page = SecurityPage.EMAIL,
            credentials = CredentialFlow.VerifyEmail(SecurityForm.Empty.with(SecurityField.EMAIL, PREVIEW_NEW_EMAIL)),
        )
        SecurityStep.PASSWORD -> state.copy(page = SecurityPage.PASSWORD, credentials = CredentialFlow.EditPassword())
        SecurityStep.VERIFY_PASSWORD -> state.copy(
            page = SecurityPage.PASSWORD,
            credentials = CredentialFlow.VerifyPassword(SecurityForm.Empty),
        )
        SecurityStep.TWO_FACTOR -> state.copy(page = SecurityPage.TWO_FACTOR)
        SecurityStep.ENABLE_EMAIL -> state.copy(page = SecurityPage.TWO_FACTOR, twoFactor = TwoFactorFlow.EnableEmail())
        SecurityStep.DISABLE_EMAIL -> state.copy(
            page = SecurityPage.TWO_FACTOR,
            status = SecurityStatus(SecondFactor.EMAIL),
            twoFactor = TwoFactorFlow.DisableEmail(),
        )
        SecurityStep.TOTP_CONFIRM -> state.copy(
            page = SecurityPage.TWO_FACTOR,
            twoFactor = TwoFactorFlow.ConfirmTotp(
                previewTotpSetup(),
            ),
        )
        SecurityStep.DISABLE_TOTP -> state.copy(
            page = SecurityPage.TWO_FACTOR,
            status = SecurityStatus(SecondFactor.TOTP),
            twoFactor = TwoFactorFlow.DisableTotp(),
        )
        SecurityStep.SECRETS -> state.copy(
            page = SecurityPage.TWO_FACTOR,
            status = SecurityStatus(SecondFactor.TOTP),
            twoFactor = TwoFactorFlow.SaveCodes(
                TotpSecrets(backupCodes = List(8) { "000000" }, recoveryCode = "00000000000000000000000000000000"),
            ),
        )
        SecurityStep.CHECK_FACTOR -> state.copy(
            page = SecurityPage.TWO_FACTOR,
            status = null,
            twoFactor = TwoFactorFlow.Checking(TwoFactorMutation.BeginTotp),
        )
    }
}

private fun previewTotpSetup(): TotpSetup = TotpSetup(
    uri = "otpauth://totp/KIRAKIRA:preview?secret=AAAAAAAAAAAAAAAA&issuer=KIRAKIRA",
    secret = "AAAAAAAAAAAAAAAA",
)
