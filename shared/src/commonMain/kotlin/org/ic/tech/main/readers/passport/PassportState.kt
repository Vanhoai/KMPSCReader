package org.ic.tech.main.readers.passport

data class PassportState(
    val documentNumber: String = "",
    val expireDate: String = "",
    val birthDate: String = "",
)