package com.community.seekerconnect

import com.solanamobile.seekerconnect.WalletAvailability
import org.junit.Assert.*
import org.junit.Test

class WalletPolicyTest {
    @Test fun defaultModeRejectsGenericWallets() {
        assertFalse(WalletPolicy.accepts(WalletAvailability.OTHER, false))
    }
    @Test fun testingModeAcceptsGenericWallets() {
        assertTrue(WalletPolicy.accepts(WalletAvailability.OTHER, true))
    }
    @Test fun neitherModeAcceptsMissingWallet() {
        assertFalse(WalletPolicy.accepts(WalletAvailability.NONE, false))
        assertFalse(WalletPolicy.accepts(WalletAvailability.NONE, true))
    }
    @Test fun bothModesAcceptCertifiedWallets() {
        assertTrue(WalletPolicy.accepts(WalletAvailability.CERTIFIED, false))
        assertTrue(WalletPolicy.accepts(WalletAvailability.CERTIFIED, true))
    }
}
