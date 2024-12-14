package org.ic.tech.main.models

enum class ReadIdCardStatus {
    StartReading,
    InitializeSuccess,
    SelectPassportApplicationSuccess,
    SendGetChallengeSuccess,
    PerformBacSuccess,
    Success,
    Failed,
}