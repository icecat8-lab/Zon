/*
 * Copyright (C) 2026 Zon File Manager
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    fun isImage(): Boolean = extension.lowercase() in IMAGE_EXTENSIONS

    fun isText(): Boolean = extension.lowercase() in TEXT_EXTENSIONS

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "log", "csv", "ini", "conf", "yaml", "yml",
            "kt", "kts", "java", "py", "js", "ts", "html", "css", "c", "cpp", "h",
            "sh", "gradle", "properties", "toml"
        )
    }
}
