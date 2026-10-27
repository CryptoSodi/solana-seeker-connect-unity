package com.community.seekerconnect

import org.bouncycastle.math.ec.rfc8032.Ed25519

/** Wire validation is independent of Android and is exercised by unit tests. */
internal object Wire {
    fun signature(message: ByteArray, signed: ByteArray, publicKey: ByteArray): ByteArray {
        require(message.isNotEmpty() && publicKey.size == 32) { "Invalid message or account key" }
        // Seeker Connect returns raw MWA signedPayloads. Some wallets omit the message;
        // support that encoding only after verifying the exact original message and key.
        val signature = if (signed.size == 64) signed.copyOf() else {
            require(signed.size == message.size + 64) { "Expected one Ed25519 signature" }
            require(signed.copyOfRange(0, message.size).contentEquals(message)) { "Wallet changed the signed message" }
            signed.copyOfRange(message.size, signed.size)
        }
        require(Ed25519.validatePublicKeyFull(publicKey, 0) &&
            Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.size)) { "Invalid message signature" }
        return signature
    }

    fun transaction(bytes: ByteArray) {
        require(bytes.size in 100..1232) { "Transaction must be a complete serialized Solana transaction (100..1232 bytes)" }
        var offset = 0
        fun byte(): Int { require(offset < bytes.size) { "Truncated transaction" }; return bytes[offset++].toInt() and 255 }
        fun skip(n: Int) { require(n >= 0 && offset + n <= bytes.size) { "Truncated transaction" }; offset += n }
        fun short(): Int {
            var value = 0
            for (i in 0..2) {
                val b = byte()
                require(i < 2 || b <= 3) { "Invalid shortvec" }
                require(i == 0 || b != 0) { "Noncanonical shortvec" }
                value = value or ((b and 127) shl (7 * i))
                if (b and 128 == 0) return value
            }
            error("Invalid shortvec")
        }
        val signatures = short()
        require(signatures in 1..12) { "Transaction needs signatures" }
        skip(signatures * 64)
        val first = byte()
        val versioned = first and 128 != 0
        require(!versioned || first == 128) { "Only legacy and v0 transactions are supported" }
        val required = if (versioned) byte() else first
        require(required == signatures) { "Signature count does not match message header" }
        val readonlySigned = byte(); val readonlyUnsigned = byte()
        val accounts = short()
        require(accounts >= required && readonlySigned < required && readonlyUnsigned <= accounts - required) { "Invalid account header" }
        skip(accounts * 32); skip(32)
        repeat(short()) { byte(); skip(short()); skip(short()) }
        if (versioned) repeat(short()) { skip(32); skip(short()); skip(short()) }
        require(offset == bytes.size) { "Trailing transaction bytes" }
    }
}
