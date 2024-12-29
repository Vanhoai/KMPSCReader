package org.ic.tech.main.readers

import kotlinx.coroutines.flow.Flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse

interface IPassportReader {
    // Method for android reader
    @Throws(Exception::class)
    fun initAndroidNfcReader(context: Any): Boolean

    @Throws(Exception::class)
    fun startListeningForegroundDispatchAndroid(activity: Any, clazz: Any): Boolean

    @Throws(Exception::class)
    fun disableForegroundDispatchAndroid(activity: Any): Boolean

    @Throws(Exception::class)
    fun startReadIdCardAndroid(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ): Flow<ReadIdCardResponse>
}