package org.ic.tech.main.readers.passport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.ic.tech.main.core.TagReader
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardStatus
import org.ic.tech.main.models.ReadIdCardResponse

/**
 * @name PassportReader
 * @author AuroraStudio
 * @version 1.0
 * @date 2024/12/14
 *
 * Class for reading passport id card (CCCD VietNam)
 */
class PassportReader(
    private val tagReader: TagReader,
    private val bacHandler: BacHandler
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
        if (bacKey.documentNumber.isEmpty()) return ReadIdCardResponse(
            ReadIdCardStatus.Failed,
            "Document number is empty"
        )


        if (bacKey.expireDate.isEmpty()) return ReadIdCardResponse(
            ReadIdCardStatus.Failed,
            "Expire date is empty"
        )


        if (bacKey.birthDate.isEmpty()) return ReadIdCardResponse(
            ReadIdCardStatus.Failed,
            "Birth date is empty"
        )


        _passportState.update {
            it.copy(
                documentNumber = bacKey.documentNumber,
                expireDate = bacKey.expireDate,
                birthDate = bacKey.birthDate
            )
        }

        return ReadIdCardResponse(ReadIdCardStatus.Success, "Bac key updated")
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
        checkStatusAndEmit(initResponse, ReadIdCardStatus.InitializeSuccess)

        val response = tagReader.selectPassportApplication()
        checkStatusAndEmit(response, ReadIdCardStatus.SelectPassportApplicationSuccess)

        val bacKey = makeBacKey()
        println("BacKey: $bacKey")
        val responseDoBac = bacHandler.doBACAuthentication(tagReader, bacKey)
        println("ResponseDoBac: $responseDoBac")

        tagReader.finalize()
    }

    private fun makeBacKey(): BacKey {
        return BacKey(
            documentNumber = _passportState.value.documentNumber,
            expireDate = _passportState.value.expireDate,
            birthDate = _passportState.value.birthDate
        )
    }

    private suspend fun FlowCollector<ReadIdCardResponse>.checkStatusAndEmit(
        response: ReadIdCardResponse,
        statusExpected: ReadIdCardStatus
    ) {
        if (response.status == ReadIdCardStatus.Failed) {
            emit(response)
            return
        }

        emit(response.copy(status = statusExpected))
    }
}