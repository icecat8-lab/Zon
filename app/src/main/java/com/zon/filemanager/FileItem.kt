/*
 * Copyright (C) 2026 Zon File Manager
 */

package com.zon.filemanager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val extension: String = ""
) {
    fun getIcon(): ImageVector {
        if (isDirectory) return Icons.Outlined.Folder
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif" -> Icons.Outlined.Image
            "mp4", "mkv", "avi" -> Icons.Outlined.Movie
            "mp3", "wav", "flac" -> Icons.Outlined.MusicNote
            "pdf", "doc", "docx", "txt" -> Icons.Outlined.Description
            "zip", "rar", "7z", "tar", "gz" -> Icons.Outlined.FolderZip
            "apk" -> Icons.Outlined.Android
            else -> Icons.Outlined.InsertDriveFile
        }
    }

    fun getIconColor(): Color {
        if (isDirectory) return ZonColors.Accent
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif" -> ZonColors.IconImage
            "mp4", "mkv", "avi" -> ZonColors.IconVideo
            "mp3", "wav", "flac" -> ZonColors.IconAudio
            "pdf", "doc", "docx", "txt" -> ZonColors.IconDoc
            "zip", "rar", "7z", "tar", "gz" -> ZonColors.IconArchive
            "apk" -> ZonColors.IconApk
            else -> ZonColors.TextSecondary
        }
    }

    fun getReadableSize(): String {
        if (isDirectory || size <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
