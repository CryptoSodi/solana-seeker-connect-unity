package com.community.seekerconnect

import com.solanamobile.seekerconnect.WalletAvailability
import org.junit.Assert.*
import org.junit.Test

class WalletPolicyTest {
    @Test fun productionModeAcceptsGenericAndCertifiedButNeverMissingWallets() {
        assertTrue(WalletPolicy.accepts(WalletAvailability.OTHER, false, true))
        assertTrue(WalletPolicy.accepts(WalletAvailability.CERTIFIED, false, true))
        assertFalse(WalletPolicy.accepts(WalletAvailability.NONE, false, true))
        assertFalse(WalletPolicy.accepts(WalletAvailability.NONE, true, true))
    }
    @Test fun cachesAreSeparatedByPolicyIdentityAndChain() {
        val identity = "https://example.org"
        val chain = "solana:devnet"
        val strict = WalletPolicy.cacheName(identity, chain, false, false)
        val generic = WalletPolicy.cacheName(identity, chain, false, true)
        val testing = WalletPolicy.cacheName(identity, chain, true, false)
        assertEquals(3, setOf(strict, generic, testing).size)
        assertEquals(testing, WalletPolicy.cacheName(identity, chain, true, true))
        assertNotEquals(generic, WalletPolicy.cacheName(identity, "solana:mainnet", false, true))
        assertNotEquals(generic, WalletPolicy.cacheName("https://other.example.org", chain, false, true))
        // The two previously shipped namespaces must remain byte-for-byte compatible.
        fun oldName(key: String) = "SeekerConnect-" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(oldName("$identity|$chain"), strict)
        assertEquals(oldName("$identity|$chain|mwa-testing"), testing)
    }
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
