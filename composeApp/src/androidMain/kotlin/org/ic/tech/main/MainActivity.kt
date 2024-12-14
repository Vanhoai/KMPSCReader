package org.ic.tech.main

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ic.tech.main.core.AndroidBacHandler
import org.ic.tech.main.core.AndroidTagReader
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardStatus
import org.ic.tech.main.readers.passport.PassportReader

class MainActivity : ComponentActivity() {

    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }

        if (nfcAdapter == null) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        startNfcListening()
    }

    private fun startNfcListening() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val techList = arrayOf(arrayOf(IsoDep::class.java.name))
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
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
            val tagReader = AndroidTagReader(tag)
            val bacHandler = AndroidBacHandler()
            val passportReader = PassportReader(tagReader, bacHandler)
            val updateBacKeyResponse = passportReader.updateBacKey(
                BacKey(
                    documentNumber = "203014513",
                    expireDate = "281224",
                    birthDate = "031224"
                )
            )

            if (updateBacKeyResponse.status == ReadIdCardStatus.Failed) {
                println("Update BAC key failed: ${updateBacKeyResponse.message}")
                return@launch
            }

            passportReader.startReadIdCard().collect { response ->
                println("Response: $response")
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}