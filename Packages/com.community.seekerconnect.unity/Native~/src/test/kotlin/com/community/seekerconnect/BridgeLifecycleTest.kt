package com.community.seekerconnect

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import com.unity3d.player.UnityPlayer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import com.solanamobile.seekerconnect.*
import com.solanamobile.seekerconnect.android.SharedPreferencesAuthorizationCache
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class BridgeLifecycleTest {
    private lateinit var activity: Activity
    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        UnityPlayer.messages.clear()
    }
    private fun invoke(id: String, operation: String, json: String = "{}"): JSONObject {
        SeekerBridge.request(activity, "Receiver", id, operation, json)
        shadowOf(Looper.getMainLooper()).idle()
        return JSONObject(UnityPlayer.messages.last())
    }
    private fun initialize() = invoke("init", "initialize", """{"identityUri":"https://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:devnet","timeoutMs":10000}""")
    @Test fun initializesWithoutWalletAndDoesNotLeakAuthorization() {
        val result = initialize()
        assertTrue(result.getBoolean("ok")); assertEquals("init", result.getString("id"))
        assertTrue(result.isNull("session")); assertFalse(result.toString().contains("authToken"))
    }
    @Test fun rejectsUnsafeIdentity() {
        val result = invoke("bad", "initialize", """{"identityUri":"http://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:devnet"}""")
        assertEquals("INVALID_INPUT", result.getString("code"))
    }
    @Test fun testingModeRejectsMainnetAtNativeBoundary() {
        val result = invoke("mainnet", "initialize", """{"identityUri":"https://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:mainnet","allowMwaTesting":true}""")
        assertEquals("INVALID_INPUT", result.getString("code"))
    }
    @Test fun productionGenericMwaAcceptsMainnetAtNativeBoundary() {
        val result = invoke("generic", "initialize", """{"identityUri":"https://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:mainnet","allowGenericMwa":true}""")
        assertTrue(result.getBoolean("ok"))
    }
    @Test fun productionOptInDoesNotBypassMockMainnetRestriction() {
        val result = invoke("both", "initialize", """{"identityUri":"https://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:mainnet","allowGenericMwa":true,"allowMwaTesting":true}""")
        assertEquals("INVALID_INPUT", result.getString("code"))
    }
    @Test fun productionGenericRestoreSurvivesClientRecreationAndDisconnectClearsCache() = runBlocking {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "example.mwa.wallet"; name = "WalletActivity" } }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("solana-wallet:/v1/associate/local")).addCategory(Intent.CATEGORY_BROWSABLE)
        shadowOf(activity.packageManager).addResolveInfoForIntent(intent, info)
        val cache = SharedPreferencesAuthorizationCache(activity, WalletPolicy.cacheName("https://example.org", "solana:mainnet", false, true))
        cache.set(StoredAuthorization(listOf(SeekerAccount("public-address", ByteArray(32))), "test-token", "https://wallet.example.org", "solana:mainnet", SeekerWalletCapabilities()))
        val config = """{"identityUri":"https://example.org","appName":"Test","iconPath":"icon.png","chain":"solana:mainnet","allowGenericMwa":true}"""
        suspend fun execute(operation: String): JSONObject {
            SeekerBridge.request(activity, "Receiver", operation, operation, "{}")
            shadowOf(Looper.getMainLooper()).idle()
            val host = Robolectric.buildActivity(WalletHostActivity::class.java).get()
            SeekerBridge.execute(checkNotNull(SeekerBridge.attach(host, false)), host)
            return JSONObject(UnityPlayer.messages.last())
        }
        repeat(2) {
            assertTrue(invoke("init", "initialize", config).getBoolean("ok"))
            assertEquals("OTHER", invoke("availability", "availability").getString("availability"))
            val restored = execute("restore")
            assertTrue(restored.toString(), restored.getBoolean("ok"))
            assertEquals("public-address", restored.getJSONObject("session").getJSONArray("accounts").getJSONObject(0).getString("address"))
            assertFalse(restored.toString().contains("test-token"))
        }
        assertNull(SharedPreferencesAuthorizationCache(activity, WalletPolicy.cacheName("https://example.org", "solana:mainnet", false, false)).get())
        assertNull(SharedPreferencesAuthorizationCache(activity, WalletPolicy.cacheName("https://example.org", "solana:devnet", true, false)).get())
        assertTrue(execute("disconnect").getBoolean("ok"))
        assertNull(cache.get())
        invoke("init", "initialize", config)
        assertTrue(execute("restore").isNull("session"))
    }
    @Test fun unavailableWalletNeverReportsCertified() {
        initialize()
        assertEquals("NONE", invoke("available", "availability").getString("availability"))
    }
    @Test fun conflictingRequestsAreRejectedAndCancellationReleasesGate() {
        initialize()
        SeekerBridge.request(activity, "Receiver", "pending", "connect", "{}")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("BUSY", invoke("other", "connect").getString("code"))
        SeekerBridge.cancel("pending"); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("CANCELLED", JSONObject(UnityPlayer.messages.last()).getString("code"))
        assertTrue(invoke("again", "availability").getBoolean("ok"))
    }
    @Test fun recreatedHostDoesNotReplayWalletRequest() {
        initialize()
        SeekerBridge.request(activity, "Receiver", "pending", "signAndSend", "{}")
        shadowOf(Looper.getMainLooper()).idle()
        val host = Robolectric.buildActivity(WalletHostActivity::class.java).get()
        assertNull(SeekerBridge.attach(host, true))
        assertEquals("ACTIVITY_RECREATED", JSONObject(UnityPlayer.messages.last()).getString("code"))
    }
}
