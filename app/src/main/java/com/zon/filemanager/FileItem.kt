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

// app/src/main/java/com/zon/filemanager/FileItem.kt
package com.zon.filemanager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isFtp: Boolean = false
) {
    fun getReadableSize(): String {
        if (isDirectory) return ""
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.0f KB", kb)
            else -> "$size B"
        }
    }

    fun isArchive(): Boolean {
        val lower = name.lowercase()
        val exts = listOf(
            ".zip", ".rar", ".7z", ".tar", ".tar.gz", ".tgz",
            ".tar.bz2", ".tbz2", ".tar.md5", ".tar.xz", ".txz",
            ".tar.lz4", ".gz", ".bz2", ".xz", ".lz4"
        )
        return exts.any { lower.endsWith(it) }
    }

    fun isImage(): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg", "ico")
    }

    fun isText(): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf(
            "txt", "md", "log", "json", "xml", "html", "css", "js",
            "kt", "java", "py", "cpp", "c", "h", "yml", "yaml", "ini", "conf", "sh", "csv"
        )
    }

    fun getArchiveFormat(): ArchiveFormat? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.md5") -> ArchiveFormat.TAR_MD5
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            lower.endsWith(".tar.lz4") -> ArchiveFormat.TAR_LZ4
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> ArchiveFormat.TAR_BZ2
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            lower.endsWith(".zip") -> ArchiveFormat.ZIP
            lower.endsWith(".7z") -> ArchiveFormat.SEVEN_ZIP
            lower.endsWith(".rar") -> ArchiveFormat.RAR
            lower.endsWith(".xz") -> ArchiveFormat.XZ
            lower.endsWith(".lz4") -> ArchiveFormat.LZ4
            lower.endsWith(".gz") -> ArchiveFormat.GZIP
            lower.endsWith(".bz2") -> ArchiveFormat.BZIP2
            else -> null
        }
    }

    fun getIcon(): ImageVector {
        if (isDirectory) return Icons.Outlined.Folder
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> Icons.Outlined.Image
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> Icons.Outlined.Movie
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> Icons.Outlined.MusicNote
            "txt", "md", "log", "rtf" -> Icons.Outlined.Description
            "json", "xml", "html", "css", "js", "kt", "java", "py", "cpp" -> Icons.Outlined.Code
            "pdf" -> Icons.Outlined.PictureAsPdf
            "doc", "docx" -> Icons.Outlined.Description
            "xls", "xlsx", "csv" -> Icons.Outlined.GridOn
            "ppt", "pptx" -> Icons.Outlined.Slideshow
            "apk", "aab", "xapk" -> Icons.Outlined.Android
            "zip", "rar", "7z", "tar", "gz", "bz2", "tgz", "md5", "xz", "lz4" -> Icons.Outlined.FolderZip
            else -> Icons.Outlined.InsertDriveFile
        }
    }

    fun getIconColor(): Color {
        if (isDirectory) return ZonColors.IconFolder
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> ZonColors.IconImage
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> ZonColors.IconVideo
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> ZonColors.IconAudio
            "txt", "md", "log", "rtf" -> ZonColors.IconDoc
            "json", "xml", "html", "css", "js", "kt", "java", "py", "cpp" -> ZonColors.IconCode
            "pdf" -> ZonColors.IconPdf
            "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx" -> ZonColors.IconDoc
            "apk", "aab", "xapk" -> ZonColors.IconApk
            "zip", "rar", "7z", "tar", "gz", "bz2", "tgz", "md5", "xz", "lz4" -> ZonColors.IconArchive
            else -> ZonColors.IconFile
        }
    }
}

fun File.toFileItem(): FileItem = FileItem(
    name = this.name,
    path = this.absolutePath,
    isDirectory = this.isDirectory,
    size = this.length(),
    lastModified = this.lastModified()
)
