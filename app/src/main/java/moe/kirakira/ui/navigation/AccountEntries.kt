package moe.kirakira.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionState
import moe.kirakira.feature.account.AccountSwitchPage
import moe.kirakira.feature.account.GUEST_ACCOUNT_ID
import moe.kirakira.feature.auth.AuthPage
import moe.kirakira.feature.auth.AuthRoute

internal fun registerAccountEntries(
    scope: EntryProviderScope<NavKey>,
    navigation: AppNavigationState,
    authRepository: AuthRepository?,
    accountState: SessionState,
    onSelectAccount: (String?) -> Unit,
    onRemoveAccount: (String) -> Unit,
    predictiveBackEnabled: Boolean,
) {
    with(scope) {
        entry<AccountSwitchRoute> {
            NavigationPage {
                AccountSwitchPage(
                    state = accountState,
                    onSelectAccount = { id -> onSelectAccount(id.takeUnless { it == GUEST_ACCOUNT_ID }) },
                    onReauthenticate = { email ->
                        navigation.openFrom(AccountSwitchRoute, AuthRoute(email))
                    },
                    onRemoveAccount = onRemoveAccount,
                    onAddAccount = { navigation.openFrom(AccountSwitchRoute, AuthRoute()) },
                    onBack = { navigation.popFrom(AccountSwitchRoute) },
                )
            }
        }
        entry<AuthRoute> { route ->
            NavigationPage {
                if (authRepository != null) AuthPage(
                    repository = authRepository,
                    initialEmail = route.email,
                    isActive = navigation.backStack.lastOrNull() == route,
                    predictiveBackEnabled = predictiveBackEnabled,
                    onClose = { navigation.popFrom(route) },
                )
            }
        }
    }
}
