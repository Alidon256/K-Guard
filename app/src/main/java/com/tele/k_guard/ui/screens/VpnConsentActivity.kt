package com.tele.k_guard.ui.screens

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tele.k_guard.vpn.ChildWebProtectionSync

/**
 * Child's phone: shows Android's one-time "allow K-Guard to set up a VPN" prompt when the parent has turned
 * web protection on. Android only lets the person holding the phone grant this, so it cannot be done remotely.
 * Has no UI of its own; it closes as soon as the system prompt is answered.
 */
class VpnConsentActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ChildWebProtectionSync.onConsentResult(this, result.resultCode == Activity.RESULT_OK)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // the prompt is already showing
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            ChildWebProtectionSync.onConsentResult(this, true)
            finish()
        } else {
            consent.launch(prepare)
        }
    }
}
