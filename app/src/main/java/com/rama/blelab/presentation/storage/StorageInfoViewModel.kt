package com.rama.blelab.presentation.storage

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageInfoState(
    val isLoading: Boolean = false,
    val hasPermissions: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdatedMillis: Long? = null
) {
    val usedBytes: Long = (totalBytes - availableBytes).coerceAtLeast(0L)
}

data class StorageCategory(
    val type: StorageCategoryType,
    val count: Int,
    val bytes: Long,
    val files: List<StorageFileItem> = emptyList()
)

data class StorageFileItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedMillis: Long?
)

enum class StorageCategoryType {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOWNLOADS,
    PDF,
    WORD,
    EXCEL,
    POWERPOINT,
    TEXT,
    ARCHIVES,
    APK,
    OTHER_FILES
}

class StorageInfoViewModel(
    private val context: Context
) : ViewModel() {

    private val contentResolver: ContentResolver = context.contentResolver

    private val _state = MutableStateFlow(StorageInfoState())
    val state: StateFlow<StorageInfoState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val hasPermissions = hasStoragePermissions()
            val hasAllFilesAccess = hasAllFilesAccess()
            val storageTotals = readStorageTotals()
            val categories = withContext(Dispatchers.IO) {
                if (hasPermissions || hasAllFilesAccess) readCategories() else emptyList()
            }
            _state.value = StorageInfoState(
                isLoading = false,
                hasPermissions = hasPermissions,
                hasAllFilesAccess = hasAllFilesAccess,
                totalBytes = storageTotals.totalBytes,
                availableBytes = storageTotals.availableBytes,
                categories = categories,
                errorMessage = if (hasPermissions || hasAllFilesAccess) null else "Allow storage access to inspect files.",
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }
    }

    fun hasStoragePermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun readStorageTotals(): StorageTotals {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return StorageTotals(
            totalBytes = statFs.totalBytes,
            availableBytes = statFs.availableBytes
        )
    }

    private fun readCategories(): List<StorageCategory> {
        return listOf(
            mediaCategory(StorageCategoryType.IMAGES, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            mediaCategory(StorageCategoryType.VIDEOS, MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            mediaCategory(StorageCategoryType.AUDIO, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
            mediaCategory(StorageCategoryType.DOWNLOADS, downloadsUri()),
            categoryForFilePatterns(StorageCategoryType.PDF, arrayOf("application/pdf"), arrayOf(".pdf")),
            categoryForFilePatterns(
                StorageCategoryType.WORD,
                arrayOf(
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ),
                arrayOf(".doc", ".docx")
            ),
            categoryForFilePatterns(
                StorageCategoryType.EXCEL,
                arrayOf(
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ),
                arrayOf(".xls", ".xlsx")
            ),
            categoryForFilePatterns(
                StorageCategoryType.POWERPOINT,
                arrayOf(
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                ),
                arrayOf(".ppt", ".pptx")
            ),
            categoryForFilePatterns(
                StorageCategoryType.TEXT,
                arrayOf("text/plain", "text/csv", "application/json", "text/html"),
                arrayOf(".txt", ".csv", ".json", ".html", ".xml", ".log")
            ),
            categoryForFilePatterns(
                StorageCategoryType.ARCHIVES,
                arrayOf(
                    "application/zip",
                    "application/x-rar-compressed",
                    "application/x-7z-compressed",
                    "application/gzip",
                    "application/x-tar"
                ),
                arrayOf(".zip", ".rar", ".7z", ".gz", ".tar")
            ),
            categoryForFilePatterns(
                StorageCategoryType.APK,
                arrayOf("application/vnd.android.package-archive"),
                arrayOf(".apk")
            ),
            otherFilesCategory()
        ).sortedWith(
            compareByDescending<StorageCategory> { it.bytes }
                .thenBy { it.type.ordinal }
        )
    }

    private fun mediaCategory(type: StorageCategoryType, uri: Uri): StorageCategory {
        val files = queryFiles(uri, selection = null, args = null)
        return StorageCategory(
            type = type,
            count = files.size,
            bytes = files.sumOf { it.sizeBytes },
            files = files
        )
    }

    private fun categoryForFilePatterns(
        type: StorageCategoryType,
        mimeTypes: Array<String>,
        extensions: Array<String>
    ): StorageCategory {
        val selection = mimeOrExtensionSelection(mimeTypes.size, extensions.size)
        val args = arrayOf(*mimeTypes, *extensions.map { "%$it" }.toTypedArray())
        val files = queryFiles(MediaStore.Files.getContentUri("external"), selection, args)
        return StorageCategory(
            type = type,
            count = files.size,
            bytes = files.sumOf { it.sizeBytes },
            files = files
        )
    }

    private fun otherFilesCategory(): StorageCategory {
        val knownMimeTypes = allKnownMimeTypes()
        val knownExtensions = allKnownExtensions()
        val mediaTypeColumn = MediaStore.Files.FileColumns.MEDIA_TYPE
        val displayNameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME
        val selection = """
            $mediaTypeColumn != ? AND
            $mediaTypeColumn != ? AND
            $mediaTypeColumn != ? AND
            (${MediaStore.Files.FileColumns.MIME_TYPE} IS NULL OR ${MediaStore.Files.FileColumns.MIME_TYPE} NOT IN (${knownMimeTypes.joinToString(",") { "?" }})) AND
            (${knownExtensions.joinToString(" AND ") { "$displayNameColumn NOT LIKE ?" }})
        """.trimIndent()
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
            *knownMimeTypes,
            *knownExtensions.map { "%$it" }.toTypedArray()
        )
        val files = queryFiles(MediaStore.Files.getContentUri("external"), selection, args)
        return StorageCategory(
            type = StorageCategoryType.OTHER_FILES,
            count = files.size,
            bytes = files.sumOf { it.sizeBytes },
            files = files
        )
    }

    private fun queryFiles(
        uri: Uri,
        selection: String?,
        args: Array<String>?
    ): List<StorageFileItem> {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                ),
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        add(
                            StorageFileItem(
                                id = id,
                                name = cursor.getString(nameIndex).orEmpty().ifBlank { "Unknown file" },
                                uri = ContentUris.withAppendedId(uri, id),
                                sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                                mimeType = cursor.getString(mimeIndex),
                                modifiedMillis = cursor.getLong(modifiedIndex).takeIf { it > 0L }?.times(1000L)
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun mimeOrExtensionSelection(mimeCount: Int, extensionCount: Int): String {
        val mimePart = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (${List(mimeCount) { "?" }.joinToString(",")})"
        val displayNameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME
        val extensionPart = List(extensionCount) { "$displayNameColumn LIKE ?" }.joinToString(" OR ")
        return "($mimePart OR $extensionPart)"
    }

    private fun allKnownMimeTypes(): Array<String> {
        return arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "application/json",
            "text/html",
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar",
            "application/vnd.android.package-archive"
        )
    }

    private fun allKnownExtensions(): Array<String> {
        return arrayOf(
            ".pdf",
            ".doc",
            ".docx",
            ".xls",
            ".xlsx",
            ".ppt",
            ".pptx",
            ".txt",
            ".csv",
            ".json",
            ".html",
            ".xml",
            ".log",
            ".zip",
            ".rar",
            ".7z",
            ".gz",
            ".tar",
            ".apk"
        )
    }

    private fun downloadsUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private data class StorageTotals(
        val totalBytes: Long,
        val availableBytes: Long
    )

    companion object {
        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
