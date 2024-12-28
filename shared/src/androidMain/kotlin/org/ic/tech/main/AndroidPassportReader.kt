package org.ic.tech.main

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.ic.tech.main.core.algorithms.SecureMessagingSessionKeyGenerator
import org.ic.tech.main.core.handlers.AndroidBacHandler
import org.ic.tech.main.core.handlers.ChipAuthenticationHandler
import org.ic.tech.main.core.models.apdu.DataGroup
import org.ic.tech.main.core.models.common.MRZResponse
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse
import org.ic.tech.main.core.models.common.ReadIdCardStatus
import org.jmrtd.lds.DG14File
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.DG2File
import org.jmrtd.lds.MRZInfo
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.Security

/**
 * @name PassportReader
 * @author AuroraStudio
 * @version 1.0
 * @date 2024/12/14
 *
 * Class for reading passport id card (CCCD VietNam)
 */
class AndroidPassportReader(private val context: Context) {
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(context) }

    private var facePathStorage: String? = null
    private lateinit var tagReader: AndroidTagReader
    private lateinit var bacHandler: AndroidBacHandler
    private lateinit var chipAuthenticationHandler: ChipAuthenticationHandler

    init {
        if (nfcAdapter == null) {
            Toast.makeText(context, "NFC is not available !!!", Toast.LENGTH_SHORT).show()
        } else {
            Security.insertProviderAt(BouncyCastleProvider(), BOUNCY_CASTLE_PROVIDER_POSITION)
        }
    }

    fun startListeningForegroundDispatch(
        activity: Activity,
        clazz: Class<*>, // this class will receive the intent with onNewIntent
    ): Boolean {
        if (nfcAdapter == null) {
            Toast.makeText(context, "NFC is not available !!!", Toast.LENGTH_SHORT).show()
            return false
        }

        val intent = Intent(activity, clazz).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent =
            PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_MUTABLE)
        val techList = arrayOf(arrayOf(IsoDep::class.java.name))
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        return true
    }

    fun disableForegroundDispatch(activity: Activity): Boolean {
        if (nfcAdapter == null) return false
        nfcAdapter?.disableForegroundDispatch(activity)
        return true
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.validatePayload(
        bacKey: BacKey,
        facePathStorage: String
    ): Boolean {
        if (bacKey.documentNumber.isEmpty()) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "Document number is empty",
                    data = mapOf()
                )
            )
            return false
        }

        if (bacKey.expireDate.isEmpty()) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "Expire date is empty",
                    data = mapOf()
                )
            )
            return false
        }

        if (bacKey.birthDate.isEmpty()) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "Birth date is empty",
                    data = mapOf()
                )
            )
            return false
        }

        if (facePathStorage.isEmpty()) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "Please provide a path for storage face image",
                    data = mapOf()
                )
            )
            return false
        }

        return true
    }

    /**
     * Prepare to read passport id card
     * @return Boolean
     *
     * Function prepare to read passport id card
     * and return response if success or not.
     */
    private fun prepareReadIdCard(tag: Tag, facePathStorage: String): ReadIdCardResponse {
        this.facePathStorage = facePathStorage

        val sm = SecureMessagingSessionKeyGenerator()
        this.tagReader = AndroidTagReader()

        this.bacHandler = AndroidBacHandler(sm)
        this.chipAuthenticationHandler = ChipAuthenticationHandler(sm)

        val response = tagReader.initialize(tag)
        return response
    }

    /**
     * Start Read Id Card
     * @return Flow<ReadIdCardResponse>
     *
     * Function start a flow to read passport id card. When the flow is collected,
     * the response will be emitted to the collector.
     *
     * Following steps will be executed:
     * 1. Initialize the tag reader
     * 2. Select passport application
     */
    fun startReadIdCard(
        tag: Tag,
        bacKey: BacKey,
        facePathStorage: String,
    ): Flow<ReadIdCardResponse> = flow {
        if (!validatePayload(bacKey, facePathStorage)) return@flow
        emit(
            ReadIdCardResponse(
                ReadIdCardStatus.StartReading,
                message = "Start reading passport id card ✅",
                data = mapOf()
            )
        )
        try {
            val initResponse = prepareReadIdCard(tag, facePathStorage)
            if (!checkStatusAndEmit(initResponse, ReadIdCardStatus.InitializeSuccess)) return@flow
            emitStartReading()
            delay(1000L)

            val response = tagReader.selectPassportApplication()
            val checkStatus = checkStatusAndEmit(
                response,
                ReadIdCardStatus.SelectPassportApplicationSuccess
            )
            delay(1000L)
            if (!checkStatus) return@flow

            val responseDoBac =
                bacHandler.doBACAuthentication(tagReader, bacKey) // Machine Readable Zone
            if (!checkStatusAndEmit(
                    responseDoBac,
                    ReadIdCardStatus.PerformBasicAccessControlSuccess
                )
            ) return@flow

            emit(
                ReadIdCardResponse(
                    ReadIdCardStatus.AccessingDataGroup,
                    message = "Accessing data group ... ",
                    data = mapOf()
                )
            )

            delay(1000L)
            val dg14: Map<BigInteger, PublicKey> = readDataGroup14() ?: return@flow
            val (key, value) = dg14.entries.iterator().next()

            if (!chipAuthenticationPublicKeyInfos(key, value)) return@flow
            val mrzInfo = readDataGroup1() ?: return@flow
            val mrzData = collectStateMRZ(mrzInfo)

            val facePath = readDataGroup2() ?: return@flow
            mrzData.updateFacePath(facePath)

            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardSuccess,
                    message = "Passport id card read success",
                    data = mrzData.toMap()!!
                )
            )
        } catch (exception: Exception) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "Failed to read passport id card ⚠️",
                    data = mapOf(
                        "exception" to (exception.message ?: "Unknown error")
                    )
                )
            )
        } finally {
            tagReader.finalize()
        }
    }

    private fun collectStateMRZ(mrzInfo: MRZInfo): MRZResponse {
        var name = mrzInfo.primaryIdentifier + " "
        val components: Array<String> = mrzInfo.secondaryIdentifierComponents
        for (i in components.indices) {
            name = name.plus(
                mrzInfo.secondaryIdentifierComponents[i] + " "
            )
        }

        val mrzResponse = MRZResponse(
            personalNumber = mrzInfo.personalNumber,
            documentType = mrzInfo.documentType,
            documentCode = mrzInfo.documentCode,
            documentNumber = mrzInfo.documentNumber,
            name = name,
            dateOfBirth = mrzInfo.dateOfBirth,
            dateOfExpiry = mrzInfo.dateOfExpiry,
            gender = mrzInfo.gender.name,
            nationality = mrzInfo.nationality,
            issuingState = mrzInfo.issuingState,
            facePath = null
        )

        return mrzResponse
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.chipAuthenticationPublicKeyInfos(
        key: BigInteger,
        value: PublicKey
    ): Boolean {
        val responseChipAuthentication = chipAuthenticationHandler.doChipAuthentication(
            tagReader = tagReader,
            key = key,
            publicKey = value
        )

        if (responseChipAuthentication) return true
        emit(
            ReadIdCardResponse(
                status = ReadIdCardStatus.ReadIdCardFailed,
                message = "Chip Authentication failed ⚠️",
                data = mapOf()
            )
        )
        return false
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup14(): Map<BigInteger, PublicKey>? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG14)
        if (data == null) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "DataGroup14 is empty ⚠️",
                    data = mapOf()
                )
            )
            return null
        }
        val dg14File = DG14File(data.inputStream())
        return dg14File.chipAuthenticationPublicKeyInfos
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup2(): String? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG2)
        if (data == null) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "DataGroup2 is empty ⚠️",
                    data = mapOf()
                )
            )
            return null
        }

        val dg2File = DG2File(data.inputStream())
        val faceInfos = dg2File.faceInfos[0]
        val faceImageInfos = faceInfos.faceImageInfos[0]

        val inputStream: InputStream = faceImageInfos.imageInputStream ?: return null
        val imageBytes: ByteArray = getImageBytes(inputStream) ?: return null

        val directory = File(context.filesDir, facePathStorage!!)
        if (directory.exists()) directory.mkdirs()

        val file = File(directory, "${System.currentTimeMillis() / 1000}.png")
        if (file.exists()) file.delete()
        writeImageBytesToFile(imageBytes, file.path)
        return file.path
    }

    private fun writeImageBytesToFile(imageBytes: ByteArray, path: String): Boolean {
        val file = File(path)
        val directory = file.parentFile ?: return false
        if (!directory.exists()) directory.mkdirs()
        if (!file.exists()) file.createNewFile()
        file.outputStream().use {
            it.write(imageBytes)
        }
        return true
    }

    private fun getImageBytes(inputStream: InputStream, quality: Int = 100): ByteArray? {
        val bitmap = getBitmap(inputStream)
        return if (bitmap != null) {
            getImageBytes(bitmap, quality)
        } else {
            null
        }
    }

    private fun getImageBytes(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    private fun getBitmap(inputStream: InputStream): Bitmap? {
        return try {
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup1(): MRZInfo? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG1)
        if (data == null) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.ReadIdCardFailed,
                    message = "DataGroup1 is empty ⚠️",
                    data = mapOf()
                )
            )
            return null
        }
        val dg1File = DG1File(data.inputStream())
        return dg1File.mrzInfo
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.emitStartReading() {
        emit(
            ReadIdCardResponse(
                status = ReadIdCardStatus.StartReading,
                message = "Start reading passport id card ✅",
                data = mapOf()
            )
        )
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.checkStatusAndEmit(
        response: ReadIdCardResponse,
        statusExpected: ReadIdCardStatus
    ): Boolean {
        if (response.status == ReadIdCardStatus.ReadIdCardFailed) {
            emit(response)
            return false
        }

        emit(response.copy(status = statusExpected))
        return true
    }

    companion object {
        private const val BOUNCY_CASTLE_PROVIDER_POSITION = 1
    }
}