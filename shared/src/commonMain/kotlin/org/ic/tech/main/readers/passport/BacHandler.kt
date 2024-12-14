package org.ic.tech.main.readers.passport

import org.ic.tech.main.core.TagReader
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardResponse

interface BacHandler {
    suspend fun doBACAuthentication(
        tagReader: TagReader,
        bacKey: BacKey
    ): ReadIdCardResponse
}