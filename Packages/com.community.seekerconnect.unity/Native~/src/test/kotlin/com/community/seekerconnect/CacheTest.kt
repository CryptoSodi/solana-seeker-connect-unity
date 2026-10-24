package com.community.seekerconnect

import android.app.Application
import com.solanamobile.seekerconnect.*
import com.solanamobile.seekerconnect.android.MwaSeekerLink
import com.solanamobile.seekerconnect.android.SharedPreferencesAuthorizationCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheTest {
    @Test fun officialCacheRestoresAcrossClientInstancesAndDisconnectClearsIt() = runTest {
        val app: Application = RuntimeEnvironment.getApplication()
        val cache = SharedPreferencesAuthorizationCache(app, "test-cache")
        // Explicit test fixture, never used by production code and not a claimed wallet authorization.
        val fixture = StoredAuthorization(listOf(SeekerAccount("test-account", ByteArray(32))), "test-token", null, "solana:devnet", SeekerWalletCapabilities())
        cache.set(fixture)
        val config = SeekerConnectConfig(DappIdentity("Test", "https://example.org", "icon.png"), "solana:devnet")
        val client = SeekerConnect(config, MwaSeekerLink(app), SharedPreferencesAuthorizationCache(app, "test-cache"))
        assertEquals("test-account", client.restore()!!.accounts.first().address)
        client.disconnect()
        assertNull(client.authorization.value)
        assertNull(SharedPreferencesAuthorizationCache(app, "test-cache").get())
    }
    @Test fun separateIdentityCacheDoesNotRestoreOtherAuthorization() = runTest {
        val app: Application = RuntimeEnvironment.getApplication()
        assertNull(SharedPreferencesAuthorizationCache(app, "different-identity-cache").get())
    }
}
