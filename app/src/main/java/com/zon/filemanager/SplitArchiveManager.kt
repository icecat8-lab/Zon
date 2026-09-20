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

// app/src/main/java/com/zon/filemanager/SplitArchiveManager.kt
package com.zon.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import kotlin.coroutines.coroutineContext

data class SplitPartInfo(
    val partNumber: Int,
    val fileName: String,
    val filePath: String,
    val size: Long
)

data class SplitArchiveInfo(
    val baseName: String,
    val parts: List<SplitPartInfo>,
    val format: SplitFormat,
    val totalSize: Long
)

enum class SplitFormat { ZIP_Z01, SEVEN_Z_001, RAR_PART, RAR_R }

class SplitArchiveManager {

    fun detectSplitArchive(file: FileItem): SplitArchiveInfo? {
        val path = file.path
        val name = File(path).name.lowercase()

        if (name.endsWith(".z01")) {
            val baseName = path.removeSuffix(".z01")
            val zipFile = File("$baseName.zip")
            if (zipFile.exists()) return buildZipSplitInfo(baseName, zipFile)
        }
        if (name.endsWith(".zip")) {
            val baseName = path.removeSuffix(".zip")
            val z01 = File("$baseName.z01")
            if (z01.exists()) return buildZipSplitInfo(baseName, File(path))
        }
        if (name.endsWith(".7z.001")) {
            return build7zSplitInfo(path.removeSuffix(".001"))
        }
        if (name.endsWith(".part1.rar")) {
            return buildRarPartInfo(path.removeSuffix(".part1.rar"))
        }
        if (name.endsWith(".rar")) {
            val baseName = path.removeSuffix(".rar")
            val r00 = File("$baseName.r00")
            if (r00.exists()) return buildRarRInfo(baseName, File(path))
        }
        return null
    }

    private fun buildZipSplitInfo(baseName: String, zipFile: File): SplitArchiveInfo {
        val parts = mutableListOf<SplitPartInfo>()
        var i = 1
        while (true) {
            val zPart = File("$baseName.z${String.format("%02d", i)}")
            if (!zPart.exists()) break
            parts.add(SplitPartInfo(i, zPart.name, zPart.absolutePath, zPart.length()))
            i++
        }
        parts.add(SplitPartInfo(i, zipFile.name, zipFile.absolutePath, zipFile.length()))
        return SplitArchiveInfo(File(baseName).name, parts, SplitFormat.ZIP_Z01, parts.sumOf { it.size })
    }

    private fun build7zSplitInfo(baseName: String): SplitArchiveInfo {
        val parts = mutableListOf<SplitPartInfo>()
        var i = 1
        while (true) {
            val part = File("${baseName}.${String.format("%03d", i)}")
            if (!part.exists()) break
            parts.add(SplitPartInfo(i, part.name, part.absolutePath, part.length()))
            i++
        }
        return SplitArchiveInfo(File(baseName).name, parts, SplitFormat.SEVEN_Z_001, parts.sumOf { it.size })
    }

    private fun buildRarPartInfo(baseName: String): SplitArchiveInfo {
        val parts = mutableListOf<SplitPartInfo>()
        var i = 1
        while (true) {
            val part = File("${baseName}.part${i}.rar")
            if (!part.exists()) break
            parts.add(SplitPartInfo(i, part.name, part.absolutePath, part.length()))
            i++
        }
        return SplitArchiveInfo(File(baseName).name, parts, SplitFormat.RAR_PART, parts.sumOf { it.size })
    }

    private fun buildRarRInfo(baseName: String, rarFile: File): SplitArchiveInfo {
        val parts = mutableListOf<SplitPartInfo>()
        var i = 0
        while (true) {
            val part = File("$baseName.r${String.format("%02d", i)}")
            if (!part.exists()) break
            parts.add(SplitPartInfo(i, part.name, part.absolutePath, part.length()))
            i++
        }
        parts.add(SplitPartInfo(i, rarFile.name, rarFile.absolutePath, rarFile.length()))
        return SplitArchiveInfo(File(baseName).name, parts, SplitFormat.RAR_R, parts.sumOf { it.size })
    }

    suspend fun mergeSplitParts(
        info: SplitArchiveInfo,
        outputPath: String,
        onProgress: suspend (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(outputPath)
        val total = info.totalSize
        var processed = 0L
        tempFile.outputStream().buffered(512 * 1024).use { out ->
            info.parts.sortedBy { it.partNumber }.forEach { part ->
                File(part.filePath).inputStream().buffered(512 * 1024).use { inp ->
                    val buf = ByteArray(512 * 1024)
                    var n: Int
                    while (inp.read(buf).also { n = it } > 0) {
                        coroutineContext.ensureActive()
                        out.write(buf, 0, n)
                        processed += n
                        onProgress(processed, total)
                    }
                }
            }
        }
        tempFile
    }

    private fun collectFiles(dir: File, list: MutableList<File>) {
        dir.listFiles()?.forEach { if (it.isDirectory) collectFiles(it, list) else list.add(it) }
    }

    fun deleteSplitParts(info: SplitArchiveInfo): Boolean {
        var success = true
        info.parts.forEach { part ->
            try { File(part.filePath).delete() } catch (e: Exception) { success = false }
        }
        return success
    }

    fun getSplitArchiveInfo(file: FileItem): SplitArchiveInfo? = detectSplitArchive(file)
}
