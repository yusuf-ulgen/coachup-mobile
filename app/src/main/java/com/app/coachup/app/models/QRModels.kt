package com.app.coachup.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class EntryType(val label: String, val iconName: String) {
    QR("QR Giriş", "qr_code"),
    MANUAL("Manuel Giriş", "keyboard");
}

enum class EntryFilterType(val label: String, val iconName: String) {
    ALL("Tümü", "list"),
    QR("QR Giriş", "qr_code"),
    MANUAL("Manuel Giriş", "keyboard")
}

// ---------------------------------------------------------------------------
// Remote / DB model
// ---------------------------------------------------------------------------

@Serializable
data class EntryHistoryDB(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val code: String = "",
    val method: String = "qr",
    val location: String = "Spor Salonu",
    @SerialName("created_at") val createdAt: String = "",
    val status: String = "success"
)

// ---------------------------------------------------------------------------
// UI model
// ---------------------------------------------------------------------------

data class EntryHistory(
    val id: String,
    val date: Date,
    val time: String,
    val location: String,
    val type: EntryType
)
