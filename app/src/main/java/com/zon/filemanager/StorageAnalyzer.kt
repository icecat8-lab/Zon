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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class CategoryUsage(
    val name: String,
    val size: Long,
    val count: Int,
    val color: androidx.compose.ui.graphics.Color
)

data class StorageInfo(
    val total: Long,
    val free: Long,
    val used: Long,
    val categories: List<CategoryUsage>,
    val largestFiles: List<FileItem>,
    val largestFolders: List<Pair<String, Long>>
)

class StorageAnalyzer {

    suspend fun analyze(
        rootPath: String,
        onProgress: suspend (String, Int) -> Unit
    ): StorageInfo = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val total = root.totalSpace
        val free = root.freeSpace
        val used = total - free

        var imageSize = 0L; var imageCount = 0
        var videoSize = 0L; var videoCount = 0
        var audioSize = 0L; var audioCount = 0
        var docSize = 0L; var docCount = 0
        var archiveSize = 0L; var archiveCount = 0
        var apkSize = 0L; var apkCount = 0
        var otherSize = 0L; var otherCount = 0

        val allFiles = mutableListOf<File>()
        val folderSizes = mutableMapOf<String, Long>()
        var fileCount = 0

        fun walk(dir: File, topFolder: String) {
            coroutineContext.ensureActive()
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    walk(file, topFolder)
                } else {
                    allFiles.add(file)
                    folderSizes[topFolder] = (folderSizes[topFolder] ?: 0L) + file.length()
                    fileCount++
                }
            }
        }

        root.listFiles()?.forEach { top ->
            if (top.isDirectory) walk(top, top.name)
            else {
                allFiles.add(top)
                folderSizes["(root)"] = (folderSizes["(root)"] ?: 0L) + top.length()
            }
        }

        allFiles.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            val ext = file.extension.lowercase()
            val size = file.length()
            when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> {
                    imageSize += size; imageCount++
                }
                "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> {
                    videoSize += size; videoCount++
                }
                "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> {
                    audioSize += size; audioCount++
                }
                "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "md", "csv", "json", "xml" -> {
                    docSize += size; docCount++
                }
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "lz4", "zst" -> {
                    archiveSize += size; archiveCount++
                }
                "apk", "aab", "xapk" -> {
                    apkSize += size; apkCount++
                }
                else -> {
                    otherSize += size; otherCount++
                }
            }
            if (index % 200 == 0) onProgress(file.name, index)
        }

        val categories = listOf(
            CategoryUsage("วิดีโอ", videoSize, videoCount, ZonColors.IconVideo),
            CategoryUsage("รูปภาพ", imageSize, imageCount, ZonColors.IconImage),
            CategoryUsage("เสียง", audioSize, audioCount, ZonColors.IconAudio),
            CategoryUsage("เอกสาร", docSize, docCount, ZonColors.IconDoc),
            CategoryUsage("Archive", archiveSize, archiveCount, ZonColors.IconArchive),
            CategoryUsage("APK", apkSize, apkCount, ZonColors.IconApk),
            CategoryUsage("อื่นๆ", otherSize, otherCount, ZonColors.IconFile)
        ).filter { it.size > 0 }.sortedByDescending { it.size }

        val largest = allFiles.sortedByDescending { it.length() }.take(20).map { it.toFileItem() }
        val topFolders = folderSizes.entries.sortedByDescending { it.value }.take(20)
            .map { it.key to it.value }

        StorageInfo(
            total = total,
            free = free,
            used = used,
            categories = categories,
            largestFiles = largest,
            largestFolders = topFolders
        )
    }
}
