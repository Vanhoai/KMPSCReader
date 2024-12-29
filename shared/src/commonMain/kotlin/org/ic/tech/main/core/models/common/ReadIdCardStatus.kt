package org.ic.tech.main.core.models.common

enum class ReadIdCardStatus {
    DetectingNFC,
    StartReading,
    InitializeSuccess,
    SelectPassportApplicationSuccess,
    PerformBasicAccessControlSuccess,
    AccessingDataGroup,
    ReadIdCardSuccess,
    ReadIdCardFailed,
}