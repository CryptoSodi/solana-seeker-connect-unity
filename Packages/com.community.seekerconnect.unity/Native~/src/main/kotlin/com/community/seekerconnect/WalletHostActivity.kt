package com.community.seekerconnect

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** A separate opaque activity preserves Unity's renderer and hosts AndroidX result registration. */
class WalletHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = SeekerBridge.attach(this, savedInstanceState != null)
        if (request == null) { finish(); return }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 32, 32, 32)
            setBackgroundColor(0xff101a25.toInt())
            addView(TextView(context).apply { text = "Seeker Connect\nWaiting for your wallet…"; textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER })
            addView(Button(context).apply { text = "Cancel"; setOnClickListener { SeekerBridge.cancel(request.id) } })
        }
        setContentView(layout)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = SeekerBridge.cancel(request.id)
        })
        request.job = lifecycleScope.launch { SeekerBridge.execute(request, this@WalletHostActivity) }
        // Covers cancellation before the coroutine gets its first execution opportunity.
        request.job!!.invokeOnCompletion {
            SeekerBridge.fail(request, "CANCELLED", "Wallet host closed. Restore your session before continuing.")
        }
    }
}
