package com.community.seekerconnect

import com.solanamobile.seekerconnect.WalletAvailability

internal object WalletPolicy {
    fun accepts(availability: WalletAvailability, allowMwaTesting: Boolean): Boolean =
        availability == WalletAvailability.CERTIFIED ||
            (allowMwaTesting && availability == WalletAvailability.OTHER)
}
