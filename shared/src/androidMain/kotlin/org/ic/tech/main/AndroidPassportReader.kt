package org.ic.tech.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.ic.tech.main.core.AndroidBacHandler
import org.ic.tech.main.core.AndroidTagReader
import org.ic.tech.main.core.DataGroup
import org.ic.tech.main.core.TagReader
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus
import org.ic.tech.main.readers.passport.BacHandler
import org.ic.tech.main.readers.passport.PassportState
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
    private val bacHandler: AndroidBacHandler
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
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.readDataGroup14(): Map<BigInteger, PublicKey>? {
        val data = tagReader.sendSelectFileAndReadDataGroup(dg = DataGroup.DG14)
        println("DataGroup14: $data")
        return null
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