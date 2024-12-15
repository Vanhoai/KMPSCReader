package org.ic.tech.main

import androidx.annotation.RequiresPermission.Read
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.ic.tech.main.core.AndroidBacHandler
import org.ic.tech.main.core.AndroidTagReader
import org.ic.tech.main.core.ChipAuthenticationHandler
import org.ic.tech.main.core.DataGroup
import org.ic.tech.main.core.extensions.toHexString
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus
import org.ic.tech.main.readers.passport.PassportState
import org.jmrtd.lds.DG14File
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.MRZInfo
import org.json.JSONObject
import java.math.BigInteger
import java.security.PublicKey

/**
 * @name PassportReader
 * @author AuroraStudio
 * @version 1.0
 * @date 2024/12/14
 *
 * Class for reading passport id card (CCCD VietNam)
 */
class AndroidPassportReader(
    private val tagReader: AndroidTagReader,
    private val bacHandler: AndroidBacHandler,
    private val chipAuthenticationHandler: ChipAuthenticationHandler
) {
    private val _passportState = MutableStateFlow(PassportState())
    private val passportState: StateFlow<PassportState> = _passportState.asStateFlow()

    /**
     * Update Bac Key
     * @param bacKey BacKeyModel
     * @return Boolean
     *
     * Function update bac key for progress reading passport id card
     * and return response if one of the field is empty or not expected
     * format
     */
    fun updateBacKey(bacKey: BacKey): ReadIdCardResponse {
        if (bacKey.documentNumber.isEmpty()) {
            return ReadIdCardResponse(
                status = ReadIdCardStatus.Failed,
                message = "Document number is empty",
                data = mapOf()
            )
        }

        if (bacKey.expireDate.isEmpty()) {
            return ReadIdCardResponse(
                status = ReadIdCardStatus.Failed,
                message = "Expire date is empty",
                data = mapOf()
            )
        }

        if (bacKey.birthDate.isEmpty()) {
            return ReadIdCardResponse(
                status = ReadIdCardStatus.Failed,
                message = "Birth date is empty",
                data = mapOf()
            )
        }

        _passportState.update {
            it.copy(
                documentNumber = bacKey.documentNumber,
                expireDate = bacKey.expireDate,
                birthDate = bacKey.birthDate
            )
        }

        return ReadIdCardResponse(
            status = ReadIdCardStatus.Success,
            message = "Bac key updated",
            data = mapOf()
        )
    }

    /**
     * Prepare to read passport id card
     * @return Boolean
     *
     * Function prepare to read passport id card
     * and return response if success or not.
     */
    private suspend fun prepareReadIdCard(): ReadIdCardResponse {
        val response = tagReader.initialize()
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
    fun startReadIdCard(): Flow<ReadIdCardResponse> = flow {
        val initResponse = prepareReadIdCard()
        if (!checkStatusAndEmit(initResponse, ReadIdCardStatus.InitializeSuccess)) return@flow
        emitStartReading()

        val response = tagReader.selectPassportApplication()
        if (!checkStatusAndEmit(
                response,
                ReadIdCardStatus.SelectPassportApplicationSuccess
            )
        ) return@flow

        val bacKey = makeBacKey()
        val responseDoBac = bacHandler.doBACAuthentication(tagReader, bacKey)
        if (!checkStatusAndEmit(responseDoBac, ReadIdCardStatus.PerformBacSuccess)) return@flow

        val dg14: Map<BigInteger, PublicKey> = readDataGroup14() ?: return@flow
        val (key, value) = dg14.entries.iterator().next()

        if (!chipAuthenticationPublicKeyInfos(key, value)) return@flow

        val mrzInfo = readDataGroup1() ?: return@flow
        val mrzData = collectStateMRZ(mrzInfo)

        println("MRZ Data: $mrzData")
    }

    private fun collectStateMRZ(mrzInfo: MRZInfo): JSONObject {
        val data = JSONObject()
        data.put("personalNumber", mrzInfo.personalNumber)
        data.put("documentType", mrzInfo.documentType)
        data.put("documentCode", mrzInfo.documentCode)
        data.put("documentNumber", mrzInfo.documentNumber)
        var name = mrzInfo.primaryIdentifier + " "

        val components: Array<String> = mrzInfo.secondaryIdentifierComponents
        for (i in components.indices) {
            name = name.plus(
                mrzInfo.secondaryIdentifierComponents[i] + " "
            )
        }

        data.put("name", name)
        data.put("dateOfBirth", mrzInfo.dateOfBirth)
        data.put("dateOfExpiry", mrzInfo.dateOfExpiry)
        data.put("gender", mrzInfo.gender)
        data.put("nationality", mrzInfo.nationality)
        data.put("issuingState", mrzInfo.issuingState)

        return data
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
                status = ReadIdCardStatus.Failed,
                message = "Chip Authentication failed ⚠️",
                data = mapOf()
            )
        )
        return false
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup14(): Map<BigInteger, PublicKey>? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG14)
        if (data.isEmpty()) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.Failed,
                    message = "DataGroup14 is empty ⚠️",
                    data = mapOf()
                )
            )
            return null
        }
        val dg14File = DG14File(data.inputStream())
        return dg14File.chipAuthenticationPublicKeyInfos
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup1(): MRZInfo? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG1)
        if (data.isEmpty() || data.size == 2) {
            emit(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.Failed,
                    message = "DataGroup1 is empty ⚠️",
                    data = mapOf()
                )
            )
            return null
        }
        val dg1File = DG1File(data.inputStream())
        return dg1File.mrzInfo
    }

    private fun makeBacKey(): BacKey {
        return BacKey(
            documentNumber = _passportState.value.documentNumber,
            expireDate = _passportState.value.expireDate,
            birthDate = _passportState.value.birthDate
        )
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
        if (response.status == ReadIdCardStatus.Failed) {
            emit(response)
            return false
        }

        emit(response.copy(status = statusExpected))
        return true
    }
}