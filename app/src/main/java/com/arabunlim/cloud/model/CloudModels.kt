package com.arabunlim.cloud.model

enum class FileCategory(val arabicTitle: String) {
    ALL("الكل"),
    PHOTOS("الصور"),
    VIDEOS("الفيديوهات"),
    AUDIO("الصوتيات"),
    DOCUMENTS("المستندات")
}

data class CloudFolder(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val iconName: String = "folder"
)

data class CloudFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val folderName: String,
    val category: FileCategory,
    val telegramMessageId: Long,
    val telegramFileId: String,
    val uploadedAt: Long = System.currentTimeMillis(),
    val isSyncedToTelegram: Boolean = true
) {
    fun formattedSize(): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f جيجابايت", gb)
            mb >= 1.0 -> String.format("%.2f ميجابايت", mb)
            kb >= 1.0 -> String.format("%.1f كيلوبايت", kb)
            else -> "$sizeBytes بايت"
        }
    }
}

sealed class TelegramAuthState {
    object LoggedOut : TelegramAuthState()
    data class WaitingForOtp(val phoneNumber: String, val apiId: String, val apiHash: String) : TelegramAuthState()
    data class Authenticated(
        val accountLabel: String,
        val modeDescription: String,
        val apiId: String = "",
        val apiHash: String = "",
        val botToken: String = "",
        val targetChatId: String = "me"
    ) : TelegramAuthState()
}
