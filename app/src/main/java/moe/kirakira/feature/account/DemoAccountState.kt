package moe.kirakira.feature.account

import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.listSaver
import moe.kirakira.R

internal const val GUEST_ACCOUNT_ID = "guest"

internal enum class DemoAccount(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val handleRes: Int? = null,
) {
    GUEST(GUEST_ACCOUNT_ID, R.string.account_guest),
    KIRAKIRA("demo_kirakira", R.string.account_demo_kirakira, R.string.account_demo_kirakira_handle),
    SAKURA("demo_sakura", R.string.account_demo_sakura, R.string.account_demo_sakura_handle),
}

/** UI-only sample data. Saved with the activity state, never persisted as login sessions. */
internal data class DemoAccountState(
    val accounts: List<DemoAccount> = DemoAccount.entries,
    val selectedId: String = GUEST_ACCOUNT_ID,
) {
    fun select(id: String): DemoAccountState =
        if (accounts.any { it.id == id }) copy(selectedId = id) else this

    fun remove(id: String): DemoAccountState {
        if (id == GUEST_ACCOUNT_ID || accounts.none { it.id == id }) return this
        return copy(
            accounts = accounts.filterNot { it.id == id },
            selectedId = if (selectedId == id) GUEST_ACCOUNT_ID else selectedId,
        )
    }

    companion object {
        val Saver = listSaver<DemoAccountState, String>(
            save = { state -> listOf(state.selectedId) + state.accounts.map { it.id } },
            restore = { saved ->
                val accounts = DemoAccount.entries.filter { it == DemoAccount.GUEST || it.id in saved.drop(1) }
                DemoAccountState(accounts = accounts).select(saved.firstOrNull() ?: GUEST_ACCOUNT_ID)
            },
        )
    }
}

internal data class AccountItem(val id: String, val name: String, val handle: String? = null)
