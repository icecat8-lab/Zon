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

import com.github.junrar.Archive as RarArchive
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Every format this engine knows about. RAR has no CREATE path: the RAR compression
 * format is proprietary to RARLab/WinRAR, so no open-source library (including this
 * one) can legally write .rar files — only read them. ISO and Zstandard are not
 * handled yet; both need dedicated follow-up work to do reliably.
 */
enum class ArchiveFormat {
    ZIP, SEVEN_Z, RAR, TAR, GZIP, BZIP2, XZ, TAR_GZ, TAR_BZ2, TAR_XZ, UNKNOWN
}

object ArchiveEngine {

    private const val BUFFER_SIZE = 256 * 1024

    fun detectFormat(file: File): ArchiveFormat {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz") -> ArchiveFormat.TAR_BZ2
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            name.endsWith(".tar") -> ArchiveFormat.TAR
            name.endsWith(".zip") || name.endsWith(".cbz") -> ArchiveFormat.ZIP
            name.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            name.endsWith(".rar") || name.endsWith(".cbr") -> ArchiveFormat.RAR
            name.endsWith(".gz") -> ArchiveFormat.GZIP
            name.endsWith(".bz2") -> ArchiveFormat.BZIP2
            name.endsWith(".xz") -> ArchiveFormat.XZ
            else -> ArchiveFormat.UNKNOWN
        }
    }

    fun isArchive(file: File): Boolean = !file.isDirectory && detectFormat(file) != ArchiveFormat.UNKNOWN

    fun canCreate(format: ArchiveFormat): Boolean =
        format != ArchiveFormat.RAR && format != ArchiveFormat.UNKNOWN

    // ==================== Extract ====================
    // Extraction only ever WRITES into destDir; the source archive file itself is
    // opened read-only and is never touched. So if the coroutine is cancelled
    // mid-way, the original archive is always still complete and untouched —
    // only the partially-written destination files are incomplete.

    suspend fun extract(archive: File, destDir: File, onProgress: (String) -> Unit) {
        destDir.mkdirs()
        when (detectFormat(archive)) {
            ArchiveFormat.ZIP -> extractZip(archive, destDir, onProgress)
            ArchiveFormat.SEVEN_Z -> extractSevenZ(archive, destDir, onProgress)
            ArchiveFormat.RAR -> extractRar(archive, destDir, onProgress)
            ArchiveFormat.TAR -> extractTar(BufferedInputStream(FileInputStream(archive)), destDir, onProgress)
            ArchiveFormat.TAR_GZ -> extractTar(
                GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive))), destDir, onProgress
            )
            ArchiveFormat.TAR_BZ2 -> extractTar(
                BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))), destDir, onProgress
            )
            ArchiveFormat.TAR_XZ -> extractTar(
                XZCompressorInputStream(BufferedInputStream(FileInputStream(archive))), destDir, onProgress
            )
            ArchiveFormat.GZIP -> extractSingleStream(
                GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive))),
                destDir, stripExtension(archive.name, ".gz"), onProgress
            )
            ArchiveFormat.BZIP2 -> extractSingleStream(
                BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))),
                destDir, stripExtension(archive.name, ".bz2"), onProgress
            )
            ArchiveFormat.XZ -> extractSingleStream(
                XZCompressorInputStream(BufferedInputStream(FileInputStream(archive))),
                destDir, stripExtension(archive.name, ".xz"), onProgress
            )
            ArchiveFormat.UNKNOWN -> throw IllegalArgumentException("ไม่รู้จักรูปแบบไฟล์นี้")
        }
    }

    private fun stripExtension(name: String, ext: String): String =
        if (name.endsWith(ext, ignoreCase = true)) name.substring(0, name.length - ext.length) else name

    /** Guards against "zip slip": an entry name like "../../evil" escaping destDir. */
    private fun safeOutputFile(destDir: File, entryName: String): File {
        val cleaned = entryName.replace('\\', '/').trimStart('/')
        val outFile = File(destDir, cleaned)
        val destCanonical = destDir.canonicalFile
        val outCanonical = outFile.canonicalFile
        if (outCanonical != destCanonical &&
            !outCanonical.path.startsWith(destCanonical.path + File.separator)
        ) {
            throw SecurityException("รายการในไฟล์บีบอัดพยายามเขียนนอกโฟลเดอร์ปลายทาง: $entryName")
        }
        return outFile
    }

    private suspend fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
    }

    private suspend fun extractSingleStream(
        input: InputStream, destDir: File, outName: String, onProgress: (String) -> Unit
    ) {
        input.use { inp ->
            onProgress(outName)
            val outFile = safeOutputFile(destDir, outName)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { out -> copyStream(inp, out) }
        }
    }

    private suspend fun extractZip(archive: File, destDir: File, onProgress: (String) -> Unit) {
        CommonsZipFile(archive).use { zip ->
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                coroutineContext.ensureActive()
                val entry = entries.nextElement()
                onProgress(entry.name)
                val outFile = safeOutputFile(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output -> copyStream(input, output) }
                    }
                }
            }
        }
    }

    private suspend fun extractSevenZ(archive: File, destDir: File, onProgress: (String) -> Unit) {
        SevenZFile(archive).use { sevenZFile ->
            var entry = sevenZFile.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val name = entry.name ?: "unnamed"
                onProgress(name)
                val outFile = safeOutputFile(destDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = sevenZFile.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
                entry = sevenZFile.nextEntry
            }
        }
    }

    private suspend fun extractTar(rawInput: InputStream, destDir: File, onProgress: (String) -> Unit) {
        TarArchiveInputStream(rawInput).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val name = entry.name ?: "unnamed"
                onProgress(name)
                val outFile = safeOutputFile(destDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> copyStream(tarInput, output) }
                }
                entry = tarInput.nextTarEntry
            }
        }
    }

    private suspend fun extractRar(archive: File, destDir: File, onProgress: (String) -> Unit) {
        val rar = RarArchive(FileInputStream(archive))
        try {
            var header = rar.nextFileHeader()
            while (header != null) {
                coroutineContext.ensureActive()
                val name = header.getFileNameString().replace('\\', '/').trim()
                onProgress(name)
                val outFile = safeOutputFile(destDir, name)
                if (header.isDirectory()) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> rar.extractFile(header, out) }
                }
                header = rar.nextFileHeader()
            }
        } finally {
            rar.close()
        }
    }

    // ==================== Compress ====================
    // We write into a hidden temp file next to the destination and only rename it
    // onto the real output name once every entry has been written successfully.
    // If the job is cancelled or an error is thrown, the temp file is deleted and
    // the destination file is never created — and since compress only READS the
    // selected source files, they are never modified either way.

    suspend fun compress(
        sources: List<File>,
        output: File,
        format: ArchiveFormat,
        onProgress: (String) -> Unit
    ) {
        if (!canCreate(format)) throw IllegalArgumentException("รูปแบบนี้สร้างไฟล์ไม่ได้")
        val tempFile = File(output.parentFile, ".${output.name}.tmp")
        try {
            when (format) {
                ArchiveFormat.ZIP -> compressZip(sources, tempFile, onProgress)
                ArchiveFormat.SEVEN_Z -> compressSevenZ(sources, tempFile, onProgress)
                ArchiveFormat.TAR -> compressTar(sources, BufferedOutputStream(FileOutputStream(tempFile)), onProgress)
                ArchiveFormat.TAR_GZ -> compressTar(
                    sources, GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                ArchiveFormat.TAR_BZ2 -> compressTar(
                    sources, BZip2CompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                ArchiveFormat.TAR_XZ -> compressTar(
                    sources, XZCompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                ArchiveFormat.GZIP -> compressSingle(
                    sources.first(), GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                ArchiveFormat.BZIP2 -> compressSingle(
                    sources.first(), BZip2CompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                ArchiveFormat.XZ -> compressSingle(
                    sources.first(), XZCompressorOutputStream(BufferedOutputStream(FileOutputStream(tempFile))), onProgress
                )
                else -> throw IllegalArgumentException("รูปแบบนี้สร้างไฟล์ไม่ได้")
            }
            if (!tempFile.renameTo(output)) {
                tempFile.copyTo(output, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private suspend fun compressZip(sources: List<File>, tempFile: File, onProgress: (String) -> Unit) {
        ZipArchiveOutputStream(tempFile).use { zos ->
            for (source in sources) addToZip(zos, source, source.name, onProgress)
        }
    }

    private suspend fun addToZip(
        zos: ZipArchiveOutputStream, file: File, entryName: String, onProgress: (String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (file.isDirectory) {
            zos.putArchiveEntry(ZipArchiveEntry("$entryName/"))
            zos.closeArchiveEntry()
            file.listFiles()?.forEach { child -> addToZip(zos, child, "$entryName/${child.name}", onProgress) }
        } else {
            onProgress(entryName)
            zos.putArchiveEntry(ZipArchiveEntry(file, entryName))
            FileInputStream(file).use { input -> copyStream(input, zos) }
            zos.closeArchiveEntry()
        }
    }

    private suspend fun compressSevenZ(sources: List<File>, tempFile: File, onProgress: (String) -> Unit) {
        SevenZOutputFile(tempFile).use { out ->
            for (source in sources) addToSevenZ(out, source, source.name, onProgress)
        }
    }

    private suspend fun addToSevenZ(
        sevenZOut: SevenZOutputFile, file: File, entryName: String, onProgress: (String) -> Unit
    ) {
        coroutineContext.ensureActive()
        sevenZOut.putArchiveEntry(sevenZOut.createArchiveEntry(file, entryName))
        if (file.isFile) {
            onProgress(entryName)
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    sevenZOut.write(buffer, 0, read)
                }
            }
        }
        sevenZOut.closeArchiveEntry()
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> addToSevenZ(sevenZOut, child, "$entryName/${child.name}", onProgress) }
        }
    }

    private suspend fun compressTar(sources: List<File>, rawOut: OutputStream, onProgress: (String) -> Unit) {
        TarArchiveOutputStream(rawOut).use { taos ->
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            for (source in sources) addToTar(taos, source, source.name, onProgress)
        }
    }

    private suspend fun addToTar(
        taos: TarArchiveOutputStream, file: File, entryName: String, onProgress: (String) -> Unit
    ) {
        coroutineContext.ensureActive()
        taos.putArchiveEntry(TarArchiveEntry(file, entryName))
        if (file.isFile) {
            onProgress(entryName)
            FileInputStream(file).use { input -> copyStream(input, taos) }
        }
        taos.closeArchiveEntry()
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> addToTar(taos, child, "$entryName/${child.name}", onProgress) }
        }
    }

    private suspend fun compressSingle(source: File, rawOut: OutputStream, onProgress: (String) -> Unit) {
        if (source.isDirectory) throw IllegalArgumentException("รูปแบบนี้บีบอัดได้เฉพาะไฟล์เดียว ไม่ใช่โฟลเดอร์")
        onProgress(source.name)
        rawOut.use { out -> FileInputStream(source).use { input -> copyStream(input, out) } }
    }
}
