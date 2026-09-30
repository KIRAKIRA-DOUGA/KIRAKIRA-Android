package moe.kirakira.feature.account

internal const val GUEST_ACCOUNT_ID = "guest"

internal data class AccountItem(
    val id: String,
    val name: String,
    val handle: String? = null,
    val avatar: String? = null,
)
