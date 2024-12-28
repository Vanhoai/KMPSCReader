package org.ic.tech.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse
import org.ic.tech.main.readers.IPassportReader


/**
 * @name PassportReader
 * @author AuroraStudio
 * @version 1.0
 * @date 2024/12/14
 *
 * Class for reading passport id card (CCCD VietNam)
 */
class PassportReader : IPassportReader {

    private val nativePlatformCall = NativePlatformCall()

    // Method for android reader
    @Throws(Exception::class)
    override fun initAndroidNfcReader(context: Any): Boolean {
        return nativePlatformCall.initAndroidNfcReader(context)
    }

    @Throws(Exception::class)
    override fun startListeningForegroundDispatchAndroid(activity: Any, clazz: Any): Boolean {
        return nativePlatformCall.startListeningForegroundDispatchAndroid(activity, clazz)
    }

    @Throws(Exception::class)
    override fun disableForegroundDispatchAndroid(activity: Any): Boolean {
        return nativePlatformCall.disableForegroundDispatchAndroid(activity)
    }

    @Throws(Exception::class)
    override fun startReadIdCardAndroid(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ): Flow<ReadIdCardResponse> {
        return nativePlatformCall.startReadIdCardAndroid(tag, bacKey, facePathStorage)
    }

    // Method for ios reader
}