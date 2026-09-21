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

import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.input.BoundedInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

data class ArchiveEntry(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val index: Int
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
}

object ArchivePreview {

    suspend fun listEntries(file: FileItem, password: String? = null): List<ArchiveEntry> =
        withContext(Dispatchers.IO) {
            val src = File(file.path)
            val lower = src.name.lowercase()
            try {
                when {
                    lower.endsWith(".zip") -> listZip(src, password)
                    lower.endsWith(".7z") -> list7z(src, password)
                    lower.endsWith(".rar") -> listRar(src, password)
                    lower.endsWith(".tar") -> listTar(src, null)
                    lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> listTar(src, "xz")
                    lower.endsWith(".tar.lz4") -> listTar(src, "lz4")
                    lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> listTar(src, "gz")
                    lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> listTar(src, "bz2")
                    lower.endsWith(".tar.md5") -> listTarMd5(src)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun listZip(src: File, password: String?): List<ArchiveEntry> {
        val zip = if (password.isNullOrEmpty()) ZipFile(src) else ZipFile(src, password.toCharArray())
        return zip.fileHeaders.mapIndexed { index, h ->
            ArchiveEntry(
                name = h.fileName,
                size = h.uncompressedSize,
                compressedSize = h.compressedSize,
                isDirectory = h.isDirectory,
                index = index
            )
        }
    }

    private fun list7z(src: File, password: String?): List<ArchiveEntry> {
        val archive = if (password.isNullOrEmpty()) SevenZFile(src) else SevenZFile(src, password.toCharArray())
        archive.use { arch ->
            val list = mutableListOf<ArchiveEntry>()
            var index = 0
            var entry = arch.nextEntry
            while (entry != null) {
                list.add(
                    ArchiveEntry(
                        name = entry.name,
                        size = entry.size,
                        compressedSize = entry.size,
                        isDirectory = entry.isDirectory,
                        index = index
                    )
                )
                index++
                entry = arch.nextEntry
            }
            return list
        }
    }

    private fun listRar(src: File, password: String?): List<ArchiveEntry> {
        val archive = if (password.isNullOrEmpty()) Archive(src) else Archive(src, password)
        archive.use { arch ->
            val list = mutableListOf<ArchiveEntry>()
            var index = 0
            arch.fileHeaders.forEach { header ->
                list.add(
                    ArchiveEntry(
                        name = header.fileName,
                        size = header.fullUnpackSize,
                        compressedSize = header.fullPackSize,
                        isDirectory = header.isDirectory,
                        index = index
                    )
                )
                index++
            }
            return list
        }
    }

    private fun listTar(src: File, compression: String?): List<ArchiveEntry> {
        val fileIn = src.inputStream().buffered(256 * 1024)
        val list = mutableListOf<ArchiveEntry>()
        var index = 0

        val input: InputStream = when (compression) {
            "gz" -> GzipCompressorInputStream(fileIn)
            "bz2" -> BZip2CompressorInputStream(fileIn)
            "xz" -> XZCompressorInputStream(fileIn)
            "lz4" -> net.jpountz.lz4.LZ4FrameInputStream(fileIn)
            else -> fileIn
        }

        input.use { wrappedStream ->
            TarArchiveInputStream(wrappedStream).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    list.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.size,
                            isDirectory = entry.isDirectory,
                            index = index
                        )
                    )
                    index++
                    entry = tar.nextEntry
                }
            }
        }
        return list
    }

    private fun listTarMd5(src: File): List<ArchiveEntry> {
        val fileSize = src.length()
        val hasSuffix = RandomAccessFile(src, "r").use { raf ->
            raf.seek(fileSize - 3)
            val tail = ByteArray(3)
            raf.readFully(tail)
            String(tail, Charsets.US_ASCII) == "MD5"
        }
        val trailing = if (hasSuffix) 19L else 16L
        val tarLength = fileSize - trailing
        val rawIn = src.inputStream().buffered(256 * 1024)
        val bounded = BoundedInputStream(rawIn, tarLength)
        val list = mutableListOf<ArchiveEntry>()
        var index = 0

        bounded.use { stream ->
            TarArchiveInputStream(stream).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    list.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.size,
                            isDirectory = entry.isDirectory,
                            index = index
                        )
                    )
                    index++
                    entry = tar.nextEntry
                }
            }
        }
        return list
    }

    private fun validateEntryName(entryName: String) {
        val name = entryName.replace('\\', '/')
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || Regex("^[A-Za-z]:/.*").matches(name) || name.split('/').any { it == ".." }) {
            throw SecurityException("ชื่อ entry ใน archive ไม่ปลอดภัย")
        }
    }

    suspend fun extractSingle(
        file: FileItem, entry: ArchiveEntry, outputPath: String, password: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val src = File(file.path)
        val lower = src.name.lowercase()
        try {
            when {
                lower.endsWith(".zip") -> extractZipSingle(src, entry, outputPath, password)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractZipSingle(
        src: File, entry: ArchiveEntry, outputPath: String, password: String?
    ): Boolean {
        val zip = if (password.isNullOrEmpty()) ZipFile(src) else ZipFile(src, password.toCharArray())
        validateEntryName(entry.name)
        val header = zip.fileHeaders.firstOrNull { it.fileName == entry.name } ?: return false
        val outFile = File(outputPath).canonicalFile
        outFile.parentFile?.mkdirs()
        zip.getInputStream(header).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        }
        return true
    }
}
