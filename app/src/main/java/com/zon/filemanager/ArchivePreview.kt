package com.zon.filemanager

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.input.BoundedInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class ArchiveEntry(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)

object ArchivePreview {

    fun getArchiveEntries(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        val name = file.name.lowercase()

        try {
            when {
                name.endsWith(".zip") -> {
                    ZipFile(file).use { zipFile ->
                        val zipEntries = zipFile.entries
                        while (zipEntries.hasMoreElements()) {
                            val entry = zipEntries.nextElement()
                            entries.add(
                                ArchiveEntry(
                                    name = entry.name,
                                    size = entry.size,
                                    compressedSize = entry.compressedSize,
                                    isDirectory = entry.isDirectory
                                )
                            )
                        }
                    }
                }
                name.endsWith(".7z") -> {
                    SevenZFile(file).use { sevenZFile ->
                        var entry = sevenZFile.nextEntry
                        while (entry != null) {
                            entries.add(
                                ArchiveEntry(
                                    name = entry.name ?: "",
                                    size = entry.size,
                                    compressedSize = entry.compressedSize,
                                    isDirectory = entry.isDirectory
                                )
                            )
                            entry = sevenZFile.nextEntry
                        }
                    }
                }
                name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") ||
                        name.endsWith(".tar.bz2") || name.endsWith(".tar.xz") -> {
                    getTarStream(file).use { tarInput ->
                        var entry = tarInput.nextTarEntry
                        while (entry != null) {
                            entries.add(
                                ArchiveEntry(
                                    name = entry.name ?: "",
                                    size = entry.size,
                                    compressedSize = entry.size,
                                    isDirectory = entry.isDirectory
                                )
                            )
                            entry = tarInput.nextTarEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return entries
    }

    private fun getTarStream(file: File): TarArchiveInputStream {
        val inputStream: InputStream = BufferedInputStream(FileInputStream(file))
        val name = file.name.lowercase()

        val decompressedStream = when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(inputStream)
            name.endsWith(".tar.bz2") -> BZip2CompressorInputStream(inputStream)
            name.endsWith(".tar.xz") -> XZCompressorInputStream(inputStream)
            else -> inputStream
        }

        return TarArchiveInputStream(decompressedStream)
    }

    fun extractSingleFile(archiveFile: File, entryName: String, outputStream: java.io.OutputStream) {
        val name = archiveFile.name.lowercase()
        try {
            when {
                name.endsWith(".zip") -> {
                    ZipFile(archiveFile).use { zip ->
                        val entry = zip.getEntry(entryName)
                        if (entry != null) {
                            zip.getInputStream(entry).use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                    }
                }
                name.endsWith(".7z") -> {
                    SevenZFile(archiveFile).use { sevenZFile ->
                        var entry = sevenZFile.nextEntry
                        while (entry != null) {
                            if (entry.name == entryName) {
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (sevenZFile.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                                break
                            }
                            entry = sevenZFile.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
