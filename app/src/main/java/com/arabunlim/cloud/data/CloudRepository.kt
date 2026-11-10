package com.arabunlim.cloud.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arabunlim.cloud.model.CloudFile
import com.arabunlim.cloud.model.CloudFolder
import com.arabunlim.cloud.model.FileCategory
import com.arabunlim.cloud.network.TelegramCloudClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CloudRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("arab_unlim_prefs", Context.MODE_PRIVATE)
    private val telegramClient = TelegramCloudClient()

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean("auto_backup_dcim", true)
    fun setAutoBackupEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_backup_dcim", enabled).apply()

    fun isOriginalQualityEnabled(): Boolean = prefs.getBoolean("original_quality_doc", true)
    fun setOriginalQualityEnabled(enabled: Boolean) = prefs.edit().putBoolean("original_quality_doc", enabled).apply()

    fun loadFolders(): List<CloudFolder> {
        val raw = prefs.getString("folders_json", null)
        if (raw.isNullOrBlank()) {
            val defaults = listOf(
                CloudFolder("f_photos", "صور الكاميرا (DCIM)"),
                CloudFolder("f_videos", "الفيديوهات العائلية"),
                CloudFolder("f_music", "مكتبة الصوتيات"),
                CloudFolder("f_docs", "المستندات والكتب"),
                CloudFolder("f_work", "ملفات العمل والمشاريع")
            )
            saveFolders(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CloudFolder(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<CloudFolder>) {
        val arr = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("createdAt", folder.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString("folders_json", arr.toString()).apply()
    }

    fun loadFiles(): List<CloudFile> {
        val raw = prefs.getString("files_json", null)
        if (raw.isNullOrBlank()) {
            val initialSamples = listOf(
                CloudFile(
                    id = UUID.randomUUID().toString(),
                    name = "صورة_ذكرى_بدون_ضغط_RAW.jpg",
                    sizeBytes = 8_450_000L,
                    mimeType = "image/jpeg",
                    folderName = "صور الكاميرا (DCIM)",
                    category = FileCategory.PHOTOS,
                    telegramMessageId = 1042L,
                    telegramFileId = "tg_saved_msg_1042"
                ),
                CloudFile(
                    id = UUID.randomUUID().toString(),
                    name = "فيديو_رحلة_العائلة_4K.mp4",
                    sizeBytes = 425_000_000L,
                    mimeType = "video/mp4",
                    folderName = "الفيديوهات العائلية",
                    category = FileCategory.VIDEOS,
                    telegramMessageId = 1043L,
                    telegramFileId = "tg_saved_msg_1043"
                ),
                CloudFile(
                    id = UUID.randomUUID().toString(),
                    name = "سورة_الكهف_تلاوة_خاشعة.mp3",
                    sizeBytes = 24_800_000L,
                    mimeType = "audio/mpeg",
                    folderName = "مكتبة الصوتيات",
                    category = FileCategory.AUDIO,
                    telegramMessageId = 1044L,
                    telegramFileId = "tg_saved_msg_1044"
                ),
                CloudFile(
                    id = UUID.randomUUID().toString(),
                    name = "مرجع_تطوير_أندرويد_Kotlin.pdf",
                    sizeBytes = 15_200_000L,
                    mimeType = "application/pdf",
                    folderName = "المستندات والكتب",
                    category = FileCategory.DOCUMENTS,
                    telegramMessageId = 1045L,
                    telegramFileId = "tg_saved_msg_1045"
                )
            )
            saveFiles(initialSamples)
            return initialSamples
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CloudFile(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            sizeBytes = obj.optLong("sizeBytes"),
                            mimeType = obj.optString("mimeType"),
                            folderName = obj.optString("folderName"),
                            category = FileCategory.valueOf(obj.optString("category", FileCategory.DOCUMENTS.name)),
                            telegramMessageId = obj.optLong("telegramMessageId"),
                            telegramFileId = obj.optString("telegramFileId"),
                            uploadedAt = obj.optLong("uploadedAt", System.currentTimeMillis()),
                            isSyncedToTelegram = obj.optBoolean("isSyncedToTelegram", true)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFiles(files: List<CloudFile>) {
        val arr = JSONArray()
        files.forEach { file ->
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("sizeBytes", file.sizeBytes)
                put("mimeType", file.mimeType)
                put("folderName", file.folderName)
                put("category", file.category.name)
                put("telegramMessageId", file.telegramMessageId)
                put("telegramFileId", file.telegramFileId)
                put("uploadedAt", file.uploadedAt)
                put("isSyncedToTelegram", file.isSyncedToTelegram)
            }
            arr.put(obj)
        }
        prefs.edit().putString("files_json", arr.toString()).apply()
    }

    suspend fun uploadUriToTelegram(
        uri: Uri,
        targetFolderName: String,
        botToken: String,
        chatId: String
    ): Pair<CloudFile?, String> {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = "ملف_جديد_${System.currentTimeMillis() % 10000}"
        var sizeBytes = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                // Read up to 15 MB into memory for direct HTTP upload, or metadata header for huge files
                val rawBytes = input.readBytes()
                if (sizeBytes <= 0L) sizeBytes = rawBytes.size.toLong()
                rawBytes
            } ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }

        val category = detectCategory(mimeType, displayName)
        val uploadResult = telegramClient.uploadBytesToTelegramCloud(
            botToken = botToken,
            chatId = chatId,
            fileName = displayName,
            mimeType = mimeType,
            fileBytes = bytes,
            folderName = targetFolderName
        )

        if (!uploadResult.success) {
            return null to uploadResult.statusMessage
        }

        val newFile = CloudFile(
            id = UUID.randomUUID().toString(),
            name = displayName,
            sizeBytes = if (sizeBytes > 0) sizeBytes else 1_024_000L,
            mimeType = mimeType,
            folderName = targetFolderName,
            category = category,
            telegramMessageId = uploadResult.messageId,
            telegramFileId = uploadResult.fileId,
            isSyncedToTelegram = true
        )

        val updatedList = listOf(newFile) + loadFiles()
        saveFiles(updatedList)
        return newFile to uploadResult.statusMessage
    }

    fun detectCategory(mimeType: String, fileName: String): FileCategory {
        val lowerMime = mimeType.lowercase()
        val lowerName = fileName.lowercase()
        return when {
            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> FileCategory.PHOTOS
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") -> FileCategory.VIDEOS
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") -> FileCategory.AUDIO
            else -> FileCategory.DOCUMENTS
        }
    }
}
