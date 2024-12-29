package org.ic.tech.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse

actual class NativePlatformCall {
    actual fun initAndroidNfcReader(context: Any): Boolean = true

    actual fun startReadIdCardAndroid(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ): Flow<ReadIdCardResponse> = flow {}

    actual fun initIOSNfcReader(): Boolean {
        return true
    }

    actual fun startReadIdCardIOS(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ) = Unit

    actual fun startListeningForegroundDispatchAndroid(
        activity: Any,
        clazz: Any
    ): Boolean = true

    actual fun disableForegroundDispatchAndroid(activity: Any): Boolean = true
}