package com.community.seekerconnect

import com.solanamobile.seekerconnect.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MessageSigningTest {
    // JDK generates/signs; the production Bouncy Castle verifier independently verifies.
    private val keys = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val account = SeekerAccount("account-a", keys.public.encoded.takeLast(32).toByteArray())
    private val message = "Exact server challenge: nonce=123".toByteArray()
    private var returnedAccount = account
    private var corrupt = false
    private var fullPayload = false
    private var decline = false
    private var signCalls = 0
    private val tokens = mutableListOf<String?>()
    private val endpoints = mutableListOf<String?>()
    private val wallet = object : SeekerWallet {
        override suspend fun authorize(chain: String?, authToken: String?, signInPayload: SeekerSignInPayload?): SeekerAuthorization {
            tokens += authToken
            if (decline) throw SeekerConnectException(SeekerConnectException.Code.AUTHORIZATION_DECLINED, "Declined")
            return SeekerAuthorization(listOf(returnedAccount), "new-token", "https://wallet.example")
        }
        override suspend fun getCapabilities() = SeekerWalletCapabilities()
        override suspend fun deauthorize(authToken: String) = Unit
        override suspend fun signMessages(addresses: List<String>, payloads: List<ByteArray>): List<ByteArray> {
            signCalls++
            assertEquals(listOf(account.address), addresses)
            val payload = payloads.single()
            val signature = java.security.Signature.getInstance("Ed25519").run { initSign(keys.private); update(payload); sign() }
            if (corrupt) signature[0] = (signature[0].toInt() xor 1).toByte()
            return listOf(if (fullPayload) payload + signature else signature)
        }
        override suspend fun signTransactions(payloads: List<ByteArray>): List<ByteArray> = error("Not used")
        override suspend fun signAndSendTransactions(payloads: List<ByteArray>, options: SeekerSignAndSendOptions?): List<ByteArray> = error("Not used")
    }
    private val link = object : SeekerLink<Unit> {
        override fun walletAvailability(config: SeekerConnectConfig) = WalletAvailability.OTHER
        override suspend fun <T> transact(host: Unit, config: SeekerConnectConfig, walletUriBase: String?, block: suspend (SeekerWallet) -> T): T {
            endpoints += walletUriBase
            return block(wallet)
        }
    }
    private val cache = MemoryAuthorizationCache()
    private val sdk = SeekerConnect(SeekerConnectConfig(DappIdentity("Test", "https://app.example"), "solana:mainnet"), link, cache)
    private suspend fun seed() { cache.set(StoredAuthorization(listOf(account), "old-token", "https://old-wallet.example", "solana:mainnet", SeekerWalletCapabilities())) }

    @Test fun mainnetDetachedAndFullPayloadAreVerifiedThroughOfficialSdk() = runBlocking {
        seed()
        assertEquals(64, MessageSigning.sign(sdk, Unit, message).size)
        fullPayload = true
        assertEquals(64, MessageSigning.sign(sdk, Unit, message).size)
        assertEquals(listOf("old-token", "new-token"), tokens)
        assertEquals(listOf("https://old-wallet.example", "https://wallet.example"), endpoints)
    }
    @Test fun invalidSignatureFailsButDoesNotReplayOrSilentlySwitchWallet() = runBlocking {
        seed(); corrupt = true
        try { MessageSigning.sign(sdk, Unit, message); fail("Invalid signature accepted") }
        catch (_: InvalidWalletResponse) { }
        assertEquals(1, signCalls)
        assertNull(sdk.restore()); assertNull(cache.get())
        // The next explicit connect must not reuse the failed wallet's token or endpoint.
        corrupt = false
        sdk.connect(Unit)
        assertNull(tokens.last()); assertNull(endpoints.last())
    }
    @Test fun changedAccountIsRejectedBeforeSigningOldChallenge() = runBlocking {
        seed(); returnedAccount = SeekerAccount("account-b", ByteArray(32) { 7 })
        try { MessageSigning.sign(sdk, Unit, message); fail("Changed account accepted") }
        catch (_: WalletAccountChanged) { }
        assertEquals(0, signCalls)
        assertNull(sdk.restore()); assertNull(cache.get())
        sdk.connect(Unit)
        assertNull(tokens.last()); assertNull(endpoints.last())
    }
    @Test fun declinedReauthorizationClearsCachedTokenWithoutRetry() = runBlocking {
        seed(); decline = true
        try { MessageSigning.sign(sdk, Unit, message); fail("Decline ignored") }
        catch (e: SeekerConnectException) { assertEquals(SeekerConnectException.Code.AUTHORIZATION_DECLINED, e.code) }
        assertNull(sdk.restore()); assertNull(cache.get())
        assertEquals(listOf("old-token"), tokens); assertEquals(0, signCalls)
    }
}
