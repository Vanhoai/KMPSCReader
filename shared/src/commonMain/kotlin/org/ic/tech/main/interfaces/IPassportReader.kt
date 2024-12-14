package org.ic.tech.main.interfaces

import kotlinx.coroutines.flow.Flow
import org.ic.tech.main.models.ReadIdCardResponse

interface IPassportReader {
    fun startReadIdCard(): Flow<ReadIdCardResponse>
}