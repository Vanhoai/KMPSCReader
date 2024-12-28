package org.ic.tech.main.readers

import kotlinx.coroutines.flow.Flow
import org.ic.tech.main.core.models.common.BacKey
import org.ic.tech.main.core.models.common.ReadIdCardResponse

interface IPassportReader {
    fun startReadIdCard(tag: Any, bacKey: BacKey): Flow<ReadIdCardResponse>
}