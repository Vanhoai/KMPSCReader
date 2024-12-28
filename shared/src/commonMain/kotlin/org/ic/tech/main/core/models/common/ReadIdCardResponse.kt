package org.ic.tech.main.core.models.common

data class ReadIdCardResponse(
    val status: ReadIdCardStatus,
    val message: String,
    val data: Map<String, Any> = mapOf()
)