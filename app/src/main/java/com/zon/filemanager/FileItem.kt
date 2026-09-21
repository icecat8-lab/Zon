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

import java.io.File
import java.util.Locale

data class FileItem(
    val file: File,
    val isDirectory: Boolean = file.isDirectory,
    val isSelected: Boolean = false,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified()
) {
    val extension: String
        get() = file.extension.lowercase(Locale.ROOT)

    val isArchive: Boolean
        get() = when (extension) {
            "zip", "7z", "tar", "gz", "bz2", "xz", "rar", "zip.001", "7z.001" -> true
            else -> false
        }

    val isSplitArchive: Boolean
        get() = name.matches(Regex(""".*\.(zip\.\d{3}|7z\.\d{3}|\d{3})$""", RegexOption.IGNORE_CASE))

    val isImage: Boolean
        get() = when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> true
            else -> false
        }

    val isVideo: Boolean
        get() = when (extension) {
            "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp" -> true
            else -> false
        }

    val isAudio: Boolean
        get() = when (extension) {
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> true
            else -> false
        }

    val isText: Boolean
        get() = when (extension) {
            "txt", "log", "json", "xml", "html", "css", "js", "kt", "java", "py", "md" -> true
            else -> false
        }
}
