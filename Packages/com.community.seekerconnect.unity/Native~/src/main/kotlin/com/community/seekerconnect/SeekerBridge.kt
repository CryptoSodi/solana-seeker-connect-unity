package com.community.seekerconnect

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import com.solanamobile.seekerconnect.*
import com.solanamobile.seekerconnect.android.MwaSeekerLink
import com.solanamobile.seekerconnect.android.SharedPreferencesAuthorizationCache
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** All mutable state is confined to Android's main looper. No tokens cross JNI. */
object SeekerBridge {
    private val main = Handler(Looper.getMainLooper())
    private var client: SeekerConnect<ComponentActivity>? = null
    private var active: Request? = null
    private var timeoutMs = 120_000L
    private var allowMwaTesting = false
    private var allowGenericMwa = false
    internal class Request(val id: String, val receiver: String, val operation: String, val data: JSONObject) {
        var job: Job? = null
        var host: WalletHostActivity? = null
        var deadline: Runnable? = null
    }

    @JvmStatic fun request(activity: Activity, receiver: String, id: String, operation: String, json: String) {
        main.post {
            val request = try { Request(id, receiver, operation, JSONObject(json)) }
            catch (_: Exception) { send(receiver, envelope(id, "INVALID_INPUT", "Invalid request JSON")); return@post }
            if (active != null) { send(receiver, envelope(id, "BUSY", "A wallet operation is already running")); return@post }
            active = request
            try {
                if (operation == "initialize") {
                    initialize(activity, request.data)
                    complete(request, JSONObject())
                } else {
                    check(client != null) { "Initialize Seeker Connect first" }
                    if (operation == "availability") {
                        complete(request, JSONObject().put("availability", client!!.walletAvailability().name))
                    } else {
                        request.deadline = Runnable {
                            request.job?.cancel()
                            fail(request, "TIMEOUT", "Wallet request timed out. A submitted transaction may still have landed; never retry automatically.")
                        }.also { main.postDelayed(it, timeoutMs + 6_000) }
                        activity.startActivity(Intent(activity, WalletHostActivity::class.java))
                    }
                }
            } catch (e: Exception) { fail(request, if(e is IllegalArgumentException) "INVALID_INPUT" else "NOT_INITIALIZED", "Could not start wallet operation; check configuration and activity state.") }
        }
    }

    @JvmStatic fun cancel(id: String) { main.post {
        active?.takeIf { it.id == id }?.let {
            it.job?.cancel()
            // Completion normally comes from the cancelled coroutine after SDK cleanup.
            if (it.job == null) fail(it, "CANCELLED", "Wallet request cancelled")
        }
    } }

    private fun initialize(activity: Activity, data: JSONObject) {
        val identity = data.getString("identityUri")
        val uri = URI(identity)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
        val name = data.getString("appName").trim(); require(name.isNotEmpty())
        val icon = data.getString("iconPath")
        val iconUri = URI(icon)
        require(icon.isNotBlank() && !iconUri.isAbsolute && iconUri.rawAuthority == null)
        val chain = data.getString("chain")
        require(chain in listOf("solana:devnet", "solana:testnet", "solana:mainnet"))
        val testing = data.optBoolean("allowMwaTesting", false)
        val generic = data.optBoolean("allowGenericMwa", false)
        require(!testing || chain == "solana:devnet") { "MWA testing mode is restricted to Devnet" }
        timeoutMs = data.optLong("timeoutMs", 120_000).also { require(it in 10_000..120_000) }
        val cacheName = WalletPolicy.cacheName(identity, chain, testing, generic)
        client = SeekerConnect(
            SeekerConnectConfig(DappIdentity(name, identity, icon), chain, associationTimeoutMs = minOf(30_000, timeoutMs)),
            MwaSeekerLink(activity.applicationContext),
            SharedPreferencesAuthorizationCache(activity.applicationContext, cacheName)
        )
        allowMwaTesting = testing
        allowGenericMwa = generic
    }

    internal fun attach(host: WalletHostActivity, recreated: Boolean): Request? {
        val request = active ?: return null
        if (recreated || request.host != null) {
            request.job?.cancel()
            fail(request, "ACTIVITY_RECREATED", "Wallet host was recreated. Restore the session; do not automatically resubmit transactions.")
            return null
        }
        request.host = host
        return request
    }

    internal suspend fun execute(request: Request, host: ComponentActivity) {
        try {
            val result = withTimeout(timeoutMs) {
                val sdk = checkNotNull(client)
                val data = request.data
                val result = JSONObject()
                if (request.operation != "disconnect") {
                    val availability = sdk.walletAvailability()
                    if (!WalletPolicy.accepts(availability, allowMwaTesting, allowGenericMwa)) {
                        if ((allowMwaTesting || allowGenericMwa) && availability == WalletAvailability.NONE)
                            throw BridgeError("NO_WALLET_FOUND", "No compatible MWA wallet is installed.")
                        throw BridgeError("UNSUPPORTED_DEVICE", "No certified Seeker wallet is available. Enable allowGenericMwa for compatible Android wallets, or allowMwaTesting for Devnet mock testing.")
                    }
                }
                when (request.operation) {
                    "connect" -> sdk.connect(host)
                    "restore" -> sdk.restore()
                    "disconnect" -> sdk.disconnect()
                    "signMessage" -> {
                        val account = sdk.restore()?.accounts?.firstOrNull() ?: throw BridgeError("NOT_CONNECTED", "Connect a wallet first")
                        val message = decode(data.getString("payload")); require(message.size in 1..32768)
                        val signed = sdk.signMessages(host, listOf(account.address), listOf(message)).single()
                        val signature = Wire.signature(message, signed, allowMwaTesting)
                        result.put("signature", encode(signature))
                            .put("signedPayload", encode(message + signature)).put("address", account.address)
                    }
                    "signTransaction", "signAndSend" -> {
                        val auth = sdk.restore() ?: throw BridgeError("NOT_CONNECTED", "Connect a wallet first")
                        val transaction = decode(data.getString("payload")); Wire.transaction(transaction)
                        if (request.operation == "signTransaction") {
                            val signed = sdk.signTransactions(host, listOf(transaction)).single()
                            Wire.transaction(signed)
                            result.put("payload", encode(signed))
                        } else {
                            if (!auth.capabilities.supportsSignAndSendTransactions)
                                throw BridgeError("UNSUPPORTED_OPERATION", "Wallet does not support sign-and-send")
                            val signature = sdk.signAndSendTransactions(host, listOf(transaction), SeekerSignAndSendOptions(commitment = "confirmed", skipPreflight = false, maxRetries = 0)).single()
                            require(signature.size == 64)
                            result.put("signature", encode(signature)).put("address", auth.accounts.first().address)
                            // This reports submission only. The caller must confirm via RPC.
                        }
                    }
                    else -> throw BridgeError("INVALID_INPUT", "Unknown bridge operation")
                }
                result
            }
            complete(request, result)
        } catch (_: TimeoutCancellationException) {
            fail(request, "TIMEOUT", "Wallet request timed out. Check transaction status before any retry.")
        } catch (_: CancellationException) {
            fail(request, "CANCELLED", "Wallet operation cancelled or Android host destroyed; submission status may be unknown.")
        } catch (e: BridgeError) { fail(request, e.code, e.message ?: e.code)
        } catch (e: SeekerConnectException) { fail(request, e.code.name, "Wallet operation failed: ${e.code.name}. Check the wallet and try again only after checking submission status.")
        } catch (_: IllegalArgumentException) { fail(request, "INVALID_INPUT", "Invalid payload or unexpected wallet response encoding")
        } catch (_: Exception) { fail(request, "WALLET_ERROR", "Wallet operation failed. No transaction confirmation is implied.") }
    }

    private class BridgeError(val code: String, message: String): Exception(message)
    private fun complete(request: Request, result: JSONObject) = finish(request, result.put("id", request.id).put("ok", true))
    internal fun fail(request: Request, code: String, message: String) = finish(request, envelope(request.id, code, message))
    private fun finish(request: Request, result: JSONObject) {
        if (active !== request) return
        request.deadline?.let(main::removeCallbacks)
        active = null
        result.put("session", session()).put("hasSession", true)
        send(request.receiver, result)
        request.host?.finish()
        request.host = null
    }
    private fun session(): Any {
        val auth = client?.authorization?.value ?: return JSONObject.NULL
        return JSONObject().put("chain", auth.chain).put("supportsSignAndSend", auth.capabilities.supportsSignAndSendTransactions)
            .put("accounts", JSONArray(auth.accounts.map { JSONObject().put("address", it.address).put("publicKeyBase64", encode(it.publicKey)).put("label", it.label ?: "") }))
    }
    private fun envelope(id: String, code: String, message: String) = JSONObject().put("id", id).put("ok", false).put("code", code).put("message", message)
    private fun send(receiver: String, result: JSONObject) {
        // Reflection avoids bundling Unity classes into the AAR. Consumer rules retain this JNI boundary.
        Class.forName("com.unity3d.player.UnityPlayer").getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
            .invoke(null, receiver, "OnNativeResult", result.toString())
    }
    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}
