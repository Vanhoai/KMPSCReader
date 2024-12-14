package org.ic.tech.main.core

import org.ic.tech.main.models.ReadIdCardResponse

interface TagReader {
    suspend fun initialize(): ReadIdCardResponse
    suspend fun finalize(): Boolean
    suspend fun send(cmd: NFCISO7816APDU): ByteArray
    suspend fun sendGetChallenge(): ByteArray
    suspend fun selectPassportApplication(): ReadIdCardResponse
}