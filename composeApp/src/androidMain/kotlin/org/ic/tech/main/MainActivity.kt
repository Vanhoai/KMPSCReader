package org.ic.tech.main

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ic.tech.main.core.models.common.BacKey

class MainActivity : ComponentActivity() {

    private val passportReader: PassportReader = PassportReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }

        passportReader.initAndroidNfcReader(this@MainActivity)
    }

    override fun onResume() {
        super.onResume()
        passportReader.startListeningForegroundDispatchAndroid(this, MainActivity::class.java)
    }

    override fun onPause() {
        super.onPause()
        passportReader.disableForegroundDispatchAndroid(this@MainActivity)
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
            passportReader.startReadIdCardAndroid(tag, bacKey, "id_images").collect { response ->
                println("Response: $response")
            }
        }
    }
}
