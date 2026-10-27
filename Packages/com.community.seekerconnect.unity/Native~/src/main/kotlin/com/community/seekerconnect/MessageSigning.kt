package com.community.seekerconnect

import com.solanamobile.seekerconnect.SeekerConnect

internal class InvalidWalletResponse : Exception()
internal class WalletAccountChanged : Exception()

internal object MessageSigning {
    suspend fun <H> sign(sdk: SeekerConnect<H>, host: H, message: ByteArray): ByteArray {
        val expected = sdk.restore()?.accounts?.firstOrNull() ?: throw IllegalStateException("No account")
        // Check the freshly reauthorized account before sending the challenge to sign.
        try { return sdk.transact(host) { authorization ->
            val current = authorization.accounts.firstOrNull()
            if (current == null || current.address != expected.address || !current.publicKey.contentEquals(expected.publicKey))
                throw WalletAccountChanged()
            val replies = signMessages(listOf(expected.address), listOf(message))
            if (replies.size != 1) throw InvalidWalletResponse()
            try { Wire.signature(message, replies.single(), expected.publicKey) }
            catch (_: IllegalArgumentException) { throw InvalidWalletResponse() }
        } } catch (e: InvalidWalletResponse) {
            sdk.disconnect()
            throw e
        } catch (e: WalletAccountChanged) {
            sdk.disconnect()
            throw e
        }
    }
}
