package org.ic.tech.main

import kotlinx.coroutines.flow.Flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse

expect class NativePlatformCall() {

    // Android Method
    fun initAndroidNfcReader(context: Any): Boolean
    fun startListeningForegroundDispatchAndroid(activity: Any, clazz: Any): Boolean
    fun disableForegroundDispatchAndroid(activity: Any): Boolean
    fun startReadIdCardAndroid(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ): Flow<ReadIdCardResponse>

    // IOS Method
    fun initIOSNfcReader(): Boolean
    fun startReadIdCardIOS(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    )
}