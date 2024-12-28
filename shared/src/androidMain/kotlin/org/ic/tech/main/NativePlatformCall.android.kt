package org.ic.tech.main

import android.app.Activity
import android.content.Context
import android.nfc.Tag
import kotlinx.coroutines.flow.Flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse

actual class NativePlatformCall {
    private var passportReader: AndroidPassportReader? = null

    actual fun initAndroidNfcReader(context: Any): Boolean {
        try {
            if (context !is Context) throw IllegalArgumentException("Context must be an instance of Context")
            passportReader = AndroidPassportReader(context)
            return true
        } catch (exception: Exception) {
            exception.printStackTrace()
            return false
        }
    }

    @Throws(Exception::class)
    actual fun startListeningForegroundDispatchAndroid(
        activity: Any,
        clazz: Any
    ): Boolean {
        if (activity !is Activity) throw IllegalArgumentException("Activity must be an instance of Context")
        if (clazz !is Class<*>) throw IllegalArgumentException("Class must be an instance of Class")
        return passportReader!!.startListeningForegroundDispatch(activity, clazz)
    }

    @Throws(Exception::class)
    actual fun disableForegroundDispatchAndroid(activity: Any): Boolean {
        if (activity !is Activity) throw IllegalArgumentException("Activity must be an instance of Context")
        return passportReader!!.disableForegroundDispatch(activity)
    }

    @Throws(Exception::class)
    actual fun startReadIdCardAndroid(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ): Flow<ReadIdCardResponse> {
        if (tag !is Tag) throw IllegalArgumentException("Tag must be an instance of Tag")
        if (passportReader == null) throw IllegalArgumentException("PassportReader must be initialized")
        return passportReader!!.startReadIdCard(tag, bacKey, facePathStorage)
    }

    // Not implemented IOS Method at here
    actual fun initIOSNfcReader(): Boolean = true
    actual fun startReadIdCardIOS(
        tag: Any,
        bacKey: BacKey,
        facePathStorage: String
    ) = Unit
}