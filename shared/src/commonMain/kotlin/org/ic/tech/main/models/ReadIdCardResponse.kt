package org.ic.tech.main.models

data class ReadIdCardResponse(
    val status: ReadIdCardStatus,
    val message: String,
    val data: Map<String, Any> = mapOf()
)