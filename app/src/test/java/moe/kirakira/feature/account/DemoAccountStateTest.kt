package moe.kirakira.feature.account

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoAccountStateTest {
    @Test
    fun select_validAccount_changesSelectionWithoutReordering() {
        val initial = DemoAccountState()
        val selected = initial.select(DemoAccount.SAKURA.id)
        assertEquals(GUEST_ACCOUNT_ID, initial.selectedId)
        assertEquals(initial.accounts, selected.accounts)
        assertEquals(DemoAccount.SAKURA.id, selected.selectedId)
        assertEquals(selected, selected.select(DemoAccount.SAKURA.id))
    }

    @Test
    fun remove_currentAccount_fallsBackToGuest() {
        val state = DemoAccountState().select(DemoAccount.KIRAKIRA.id).remove(DemoAccount.KIRAKIRA.id)
        assertEquals(GUEST_ACCOUNT_ID, state.selectedId)
        assertEquals(listOf(DemoAccount.GUEST, DemoAccount.SAKURA), state.accounts)
    }

    @Test
    fun remove_otherAccount_preservesSelectionAndGuest() {
        val state = DemoAccountState().select(DemoAccount.SAKURA.id).remove(DemoAccount.KIRAKIRA.id)
        assertEquals(DemoAccount.SAKURA.id, state.selectedId)
        assertEquals(state, state.remove(GUEST_ACCOUNT_ID))
        assertEquals(state, state.select(DemoAccount.KIRAKIRA.id))
        assertEquals(state, state.remove("unknown"))
        assertEquals(state, state.select("unknown"))
    }

    @Test
    fun remove_allAccounts_keepsGuestAsOnlySelection() {
        val state = DemoAccountState().select(DemoAccount.SAKURA.id)
            .remove(DemoAccount.KIRAKIRA.id).remove(DemoAccount.SAKURA.id)
        assertEquals(listOf(DemoAccount.GUEST), state.accounts)
        assertEquals(GUEST_ACCOUNT_ID, state.selectedId)
    }
}
