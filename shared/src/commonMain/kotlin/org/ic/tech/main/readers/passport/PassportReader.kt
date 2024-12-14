package org.ic.tech.main.readers.passport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ic.tech.main.models.BacKeyModel
import org.ic.tech.main.models.ReadIdCardResponse

class PassportReader {
    fun startReadIdCard(): Flow<ReadIdCardResponse> = flow {}
    fun updateBacKey(bacKey: BacKeyModel): Boolean {
        return true
    }
}