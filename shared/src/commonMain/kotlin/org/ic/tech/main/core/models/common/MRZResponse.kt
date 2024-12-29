package org.ic.tech.main.core.models.common

data class MRZResponse(
    val personalNumber: String,
    val documentType: Int,
    val documentCode: String,
    val documentNumber: String,
    val name: String,
    val dateOfBirth: String,
    val dateOfExpiry: String,
    val gender: String,
    val nationality: String,
    val issuingState: String,
    var facePath: String? = null,
) {
    fun updateFacePath(facePath: String) {
        this.facePath = facePath
    }

    fun toMap(): Map<String, Any>? {
        if (facePath.isNullOrEmpty()) return null
        return mapOf(
            "personalNumber" to personalNumber,
            "documentType" to documentType,
            "documentCode" to documentCode,
            "documentNumber" to documentNumber,
            "name" to name,
            "dateOfBirth" to dateOfBirth,
            "dateOfExpiry" to dateOfExpiry,
            "gender" to gender,
            "nationality" to nationality,
            "issuingState" to issuingState,
            "facePath" to facePath!!,
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): MRZResponse {
            return MRZResponse(
                personalNumber = map["personalNumber"] as String,
                documentType = map["documentType"] as Int,
                documentCode = map["documentCode"] as String,
                documentNumber = map["documentNumber"] as String,
                name = map["name"] as String,
                dateOfBirth = map["dateOfBirth"] as String,
                dateOfExpiry = map["dateOfExpiry"] as String,
                gender = map["gender"] as String,
                nationality = map["nationality"] as String,
                issuingState = map["issuingState"] as String,
                facePath = map["facePath"] as String?,
            )
        }
    }
}