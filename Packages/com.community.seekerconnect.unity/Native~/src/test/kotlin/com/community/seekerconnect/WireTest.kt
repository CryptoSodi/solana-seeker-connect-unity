package com.community.seekerconnect

import org.junit.Assert.*
import org.junit.Test

class WireTest {
    @Test fun extractsSignatureAfterUtf8Message() {
        val message = "Hello 🌍".toByteArray(Charsets.UTF_8)
        val signature = ByteArray(64) { it.toByte() }
        assertArrayEquals(signature, Wire.signature(message, message + signature))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsModifiedMessage() {
        Wire.signature(byteArrayOf(1), byteArrayOf(2) + ByteArray(64))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSignatureOnly() {
        Wire.signature(byteArrayOf(1), ByteArray(64))
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
