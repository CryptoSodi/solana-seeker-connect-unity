package com.community.seekerconnect

import android.app.Activity
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
