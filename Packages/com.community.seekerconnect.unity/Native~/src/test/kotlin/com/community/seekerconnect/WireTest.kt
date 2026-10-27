package com.community.seekerconnect

import org.junit.Assert.*
import org.junit.Test

class WireTest {
    private val keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKey = keyPair.public.encoded.takeLast(32).toByteArray()
    private fun sign(message: ByteArray): ByteArray = java.security.Signature.getInstance("Ed25519").run {
        initSign(keyPair.private); update(message); sign()
    }
    @Test fun extractsSignatureAfterUtf8Message() {
        val message = "Hello 🌍".toByteArray(Charsets.UTF_8)
        val signature = sign(message)
        assertArrayEquals(signature, Wire.signature(message, message + signature, publicKey))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsModifiedMessage() {
        Wire.signature(byteArrayOf(1), byteArrayOf(2) + sign(byteArrayOf(2)), publicKey)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsForgedDetachedSignature() {
        Wire.signature(byteArrayOf(1), ByteArray(64), publicKey)
    }
    @Test fun acceptsCryptographicallyValidDetachedSignatureWithoutTestingFlag() {
        val signature = sign(byteArrayOf(1))
        assertArrayEquals(signature, Wire.signature(byteArrayOf(1), signature, publicKey))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDetachedSignatureForDifferentMessage() {
        Wire.signature(byteArrayOf(1), sign(byteArrayOf(2)), publicKey)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedSignature() {
        Wire.signature(byteArrayOf(1), ByteArray(63), publicKey)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDifferentSigner() {
        val other = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.takeLast(32).toByteArray()
        Wire.signature(byteArrayOf(1), sign(byteArrayOf(1)), other)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsForgedFullPayload() {
        Wire.signature(byteArrayOf(1), byteArrayOf(1) + ByteArray(64), publicKey)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidPublicKey() {
        Wire.signature(byteArrayOf(1), sign(byteArrayOf(1)), ByteArray(32))
    }
    private fun memo() = byteArrayOf(1) + ByteArray(64) + byteArrayOf(1, 0, 1, 2) + ByteArray(64) + ByteArray(32) + byteArrayOf(1, 1, 0, 2, 72, 105)
    @Test fun acceptsCompleteLegacyMemo() { Wire.transaction(memo()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedInstruction() { Wire.transaction(memo().dropLast(1).toByteArray()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsTrailingData() { Wire.transaction(memo() + byteArrayOf(0)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsignedMessageInsteadOfTransaction() { Wire.transaction(memo().drop(65).toByteArray()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsSignatureCountMismatch() { val tx = memo(); tx[65] = 2; Wire.transaction(tx) }
    @Test fun acceptsVersionZero() {
        val legacy = memo()
        Wire.transaction(legacy.take(65).toByteArray() + byteArrayOf(128.toByte()) + legacy.drop(65).toByteArray() + byteArrayOf(0))
    }
}
