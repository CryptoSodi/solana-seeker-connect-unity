package com.community.seekerconnect

import com.solanamobile.seekerconnect.WalletAvailability
import java.security.MessageDigest

internal object WalletPolicy {
    fun accepts(availability: WalletAvailability, allowMwaTesting: Boolean, allowGenericMwa: Boolean = false): Boolean =
        availability == WalletAvailability.CERTIFIED ||
            ((allowMwaTesting || allowGenericMwa) && availability == WalletAvailability.OTHER)

    fun cacheName(identity: String, chain: String, allowMwaTesting: Boolean, allowGenericMwa: Boolean): String {
        // Keep existing certified-only and mock caches compatible. Mock opt-in always isolates the cache.
        val suffix = when {
            allowMwaTesting -> "|mwa-testing"
            allowGenericMwa -> "|generic-mwa"
            else -> ""
        }
        val key = "$identity|$chain$suffix"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SeekerConnect-$hash"
    }
}
