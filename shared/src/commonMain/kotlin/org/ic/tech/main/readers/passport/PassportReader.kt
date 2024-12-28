package org.ic.tech.main.readers.passport

import kotlinx.coroutines.flow.Flow
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
class PassportReader() : IPassportReader {

    override fun startReadIdCard(tag: Any, bacKey: BacKey): Flow<ReadIdCardResponse> {
        TODO("Not yet implemented")
    }
    
}