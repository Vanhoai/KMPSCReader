package org.ic.tech.main.models

data class ReadIdCardResponse(
    val status: ReadIDCardStatus,
    val message: String,
    val data: Map<String, Any>
)