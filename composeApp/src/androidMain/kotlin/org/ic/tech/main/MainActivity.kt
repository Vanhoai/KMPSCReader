package org.ic.tech.main

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ic.tech.main.core.handlers.AndroidBacHandler
import org.ic.tech.main.core.handlers.ChipAuthenticationHandler
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardStatus
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainActivity : ComponentActivity() {

    private lateinit var passportReader: AndroidPassportReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passportReader = AndroidPassportReader(this@MainActivity)
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume with passportReader: $passportReader")
        passportReader.startListeningForegroundDispatch(this, MainActivity::class.java)
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause with passportReader: $passportReader")
        passportReader.disableForegroundDispatch(this@MainActivity)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let { startPassportReader(it) }
        }
    }

    private fun startPassportReader(tag: Tag) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bacKey = BacKey(
                documentNumber = "203014513",
                expireDate = "281224",
                birthDate = "031224"
            )
            passportReader.startReadIdCard(tag, bacKey).collect { response ->
                println("Response: $response")
            }
        }
    }
}
