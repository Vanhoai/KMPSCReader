package org.ic.tech.main

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.MRZResponse
import org.ic.tech.main.core.models.common.ReadIdCardStatus


@Composable
fun MainApp() {
    MaterialTheme {
        // Global State
        val passportReader = remember { PassportReader() }
        val activity = LocalContext.current as MainActivity
        val localConfiguration = LocalConfiguration.current
        val heightScreen = localConfiguration.screenHeightDp.dp.value

        val scope = rememberCoroutineScope()

        ComposableLifecycle { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Log.d("MainApplication", "ON_CREATE")
                    passportReader.initAndroidNfcReader(activity)
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("MainApplication", "ON_PAUSE")
                    passportReader.disableForegroundDispatchAndroid(activity)
                }

                else -> {}
            }
        }

        val currentTag = remember { mutableStateOf<Tag?>(null) }
        val currentMessage = remember { mutableStateOf("Detecting NFC Scanner ...") }
        val mrz = remember { mutableStateOf<MRZResponse?>(null) }

        LaunchedEffect(currentTag.value) {
            currentTag.value?.let { tag ->
                val bacKey = BacKey(
                    documentNumber = "20301451", // Replace with your document number
                    expireDate = "011224", // Replace with your expire date
                    birthDate = "011224" // Replace with your birth date
                )
                passportReader.startReadIdCardAndroid(tag, bacKey, "id_images").collect { result ->
                    Log.d("MainApplication", "Message: ${result.message}")
                    currentMessage.value = result.message

                    if (result.status == ReadIdCardStatus.ReadIdCardSuccess) {
                        mrz.value = MRZResponse.fromMap(result.data)
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            val listener = Consumer<Intent> { intent ->
                Log.d("MainApplication", "ON_NEW_INTENT")
                if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
                    val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                    if (tag != null) currentTag.value = tag
                }
            }
            activity.addOnNewIntentListener(listener)
            onDispose { activity.removeOnNewIntentListener(listener) }
        }

        // State for bottom sheet
        val isOpen = remember { mutableStateOf(false) }
        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberBottomSheetState(
                initialValue = BottomSheetValue.Collapsed
            )
        )

        val alpha = animateFloatAsState(
            targetValue = if (isOpen.value) 0.4f else 0f,
            label = "AnimatedAlpha"
        )

        LaunchedEffect(bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
            if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
                if (isOpen.value) {
                    scope.launch { isOpen.value = false }
                }
            }
        }

        BottomSheetScaffold(
            scaffoldState = bottomSheetScaffoldState,
            sheetPeekHeight = 0.dp,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetContent = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((heightScreen * 0.4f).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(currentMessage.value)

                        if (mrz.value != null) {
                            Text("Personal Number: ${mrz.value!!.personalNumber}")
                            Text("Document Type: ${mrz.value!!.documentType}")
                            Text("Document Code: ${mrz.value!!.documentCode}")
                            Text("Document Number: ${mrz.value!!.documentNumber}")
                            Text("Name: ${mrz.value!!.name}")
                            Text("Date of Birth: ${mrz.value!!.dateOfBirth}")
                            Text("Date of Expiry: ${mrz.value!!.dateOfExpiry}")
                            Text("Gender: ${mrz.value!!.gender}")
                            Text("Nationality: ${mrz.value!!.nationality}")
                            Text("Issuing State: ${mrz.value!!.issuingState}")
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        scope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                        scope.launch { isOpen.value = true }
                        passportReader.startListeningForegroundDispatchAndroid(
                            activity,
                            MainActivity::class.java
                        )
                        // Reset state
                        currentTag.value = null
                        currentMessage.value = "Detecting NFC Scanner ..."
                        mrz.value = null
                    }) {
                        Text("Scan NFC")
                    }
                }

                // check if sheet is expanded
                if (isOpen.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = alpha.value))
                            .clickable(
                                enabled = bottomSheetScaffoldState.bottomSheetState.isExpanded
                            ) {
                                scope.launch { isOpen.value = false }
                                scope.launch { bottomSheetScaffoldState.bottomSheetState.collapse() }
                            }
                    )
                }
            }
        }
    }
}
