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

// app/src/main/java/com/zon/filemanager/ZonArchive.kt
package com.zon.filemanager

import android.content.Context
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel as ZipLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.io.input.BoundedInputStream
import org.tukaani.xz.LZMA2Options
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import kotlin.coroutines.coroutineContext

object BufferPool {
    private const val BUFFER_SIZE = 64 * 1024
    private const val POOL_SIZE = 16

    private val pool = ArrayBlockingQueue<ByteArray>(POOL_SIZE)

    init {
        repeat(POOL_SIZE) {
            pool.offer(ByteArray(BUFFER_SIZE))
        }
    }

    fun acquire(): ByteArray = pool.poll() ?: ByteArray(BUFFER_SIZE)

    fun release(buffer: ByteArray) {
        if (buffer.size == BUFFER_SIZE) {
            pool.offer(buffer)
        }
    }

    suspend fun <T> withBuffer(block: suspend (ByteArray) -> T): T {
        val buf = acquire()
        try {
            return block(buf)
        } finally {
            release(buf)
        }
    }
}

enum class ArchiveFormat(
    val ext: String,
    val displayName: String,
    val canCompress: Boolean,
    val canPassword: Boolean
) {
    ZIP("zip", "ZIP", true, true),
    SEVEN_ZIP("7z", "7z", true, false),
    TAR("tar", "TAR", true, false),
    TAR_GZ("tar.gz", "TAR.GZ", true, false),
    TAR_BZ2("tar.bz2", "TAR.BZ2", true, false),
    TAR_XZ("tar.xz", "TAR.XZ", true, false),
    TAR_LZ4("tar.lz4", "TAR.LZ4", true, false),
    TAR_MD5("tar.md5", "TAR.MD5", true, false),
    GZIP("gz", "GZIP", true, false),
    BZIP2("bz2", "BZIP2", true, false),
    XZ("xz", "XZ", true, false),
    LZ4("lz4", "LZ4", true, false),
    RAR("rar", "RAR", false, false),
    MD5("md5", "MD5", true, false)
}

enum class CompressionOption(val displayName: String, val level: Int) {
    STORE("ไม่บีบอัด", 0),
    FAST("เร็ว", 1),
    NORMAL("ปกติ", 5),
    MAXIMUM("สูงสุด", 7),
    ULTRA("สูงสุดพิเศษ", 9)
}

data class ArchiveProgress(
    val isRunning: Boolean = false,
    val currentFile: String = "",
    val processed: Long = 0,
    val total: Long = 0,
    val message: String = "",
    val isError: Boolean = false,
    val isCompleted: Boolean = false,
    val canCancel: Boolean = true
) {
    val percent: Int
        get() = if (total > 0) ((processed * 100) / total).toInt().coerceIn(0, 100) else 0
}

class ZonArchive(private val context: Context) {

    private val splitManager = SplitArchiveManager()

    private fun applyPassword(params: ZipParameters, pwdChars: CharArray) {
        try {
            val method = ZipParameters::class.java.getMethod("setPassword", CharArray::class.java)
            method.invoke(params, pwdChars)
        } catch (e: Exception) {
            try {
                val method = ZipParameters::class.java.getMethod("setPassword", String::class.java)
                method.invoke(params, String(pwdChars))
            } catch (e2: Exception) {
                android.util.Log.e("ZonArchive", "Cannot set password: ${e2.message}")
            }
        }
    }

    suspend fun compress(
        sources: List<FileItem>,
        outputPath: String,
        format: ArchiveFormat,
        level: CompressionOption,
        password: String?,
        splitSizeMb: Int? = null,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalBytes = sources.sumOf {
            if (it.isDirectory) getDirectorySize(File(it.path)) else it.size
        }
        var processed = 0L
        try {
            onProgress(ArchiveProgress(isRunning = true, message = "กำลังบีบอัด...", total = totalBytes))
            var lastReported = 0L
            val cb: suspend (String, Long) -> Unit = { name, bytes ->
                processed += bytes
                val threshold = maxOf(1024L * 1024L, totalBytes / 200)
                if (processed - lastReported >= threshold || processed >= totalBytes) {
                    lastReported = processed
                    onProgress(ArchiveProgress(isRunning = true, currentFile = name, processed = processed, total = totalBytes, message = "กำลังบีบอัด..."))
                }
                coroutineContext.ensureActive()
            }
            when (format) {
                ArchiveFormat.ZIP -> compressZip(sources, outputPath, level, password, cb)
                ArchiveFormat.SEVEN_ZIP -> compress7z(sources, outputPath, cb)
                ArchiveFormat.TAR -> compressTar(sources, outputPath, null, cb)
                ArchiveFormat.TAR_GZ -> compressTar(sources, outputPath, "gz", cb)
                ArchiveFormat.TAR_BZ2 -> compressTar(sources, outputPath, "bz2", cb)
                ArchiveFormat.TAR_XZ -> compressTar(sources, outputPath, "xz", cb)
                ArchiveFormat.TAR_LZ4 -> compressTar(sources, outputPath, "lz4", cb)
                ArchiveFormat.TAR_MD5 -> compressTarMd5(sources, outputPath, onProgress)
                ArchiveFormat.GZIP -> compressSingle(sources.first(), outputPath, "gz", cb)
                ArchiveFormat.BZIP2 -> compressSingle(sources.first(), outputPath, "bz2", cb)
                ArchiveFormat.XZ -> compressSingle(sources.first(), outputPath, "xz", cb)
                ArchiveFormat.LZ4 -> compressSingle(sources.first(), outputPath, "lz4", cb)
                ArchiveFormat.MD5 -> generateMd5(sources.first(), outputPath, cb)
                ArchiveFormat.RAR -> throw Exception("ไม่สามารถสร้างไฟล์ RAR ได้")
            }
            if (splitSizeMb != null && splitSizeMb > 0) {
                splitFile(outputPath, splitSizeMb * 1024L * 1024L) { c, t ->
                    onProgress(ArchiveProgress(isRunning = true, currentFile = "Split", processed = c, total = t, message = "กำลังแบ่งไฟล์..."))
                }
            }
            onProgress(ArchiveProgress(isRunning = false, isCompleted = true, processed = totalBytes, total = totalBytes, message = "เสร็จสิ้น", canCancel = false))
        } catch (e: kotlinx.coroutines.CancellationException) {
            File(outputPath).delete()
            onProgress(ArchiveProgress(isRunning = false, isError = true, message = "ยกเลิกแล้ว", canCancel = false))
            throw e
        } catch (e: Exception) {
            File(outputPath).delete()
            onProgress(ArchiveProgress(isRunning = false, isError = true, message = "เกิดข้อผิดพลาด: ${e.message}", canCancel = false))
        }
    }

    suspend fun extract(
        source: FileItem, outputDir: String, password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val src = File(source.path)
        val name = src.name.lowercase()
        try {
            onProgress(ArchiveProgress(isRunning = true, message = "กำลังตรวจสอบ...", currentFile = src.name, total = src.length()))
            val actualSrc = checkAndJoinSplit(src, onProgress)
            try {
                when {
                name.endsWith(".zip") -> extractZipWithProgress(actualSrc, outputDir, password, onProgress)
                name.endsWith(".7z") -> extract7z(actualSrc, outputDir, password, onProgress)
                name.endsWith(".rar") -> extractRar(actualSrc, outputDir, password, onProgress)
                name.endsWith(".tar.md5") -> extractTarMd5(actualSrc, outputDir, onProgress)
                name.endsWith(".tar.xz") || name.endsWith(".txz") -> extractTarStream(actualSrc, outputDir, "xz", onProgress)
                name.endsWith(".tar.lz4") -> extractTarStream(actualSrc, outputDir, "lz4", onProgress)
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTarStream(actualSrc, outputDir, "gz", onProgress)
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> extractTarStream(actualSrc, outputDir, "bz2", onProgress)
                name.endsWith(".tar") -> extractTarStream(actualSrc, outputDir, null, onProgress)
                name.endsWith(".xz") -> extractSingle(actualSrc, outputDir, "xz", onProgress)
                name.endsWith(".lz4") -> extractSingle(actualSrc, outputDir, "lz4", onProgress)
                name.endsWith(".gz") -> extractSingle(actualSrc, outputDir, "gz", onProgress)
                name.endsWith(".bz2") -> extractSingle(actualSrc, outputDir, "bz2", onProgress)
                else -> throw Exception("ไม่รองรับ")
                }
            } finally {
                if (actualSrc.absolutePath != src.absolutePath && actualSrc.parentFile == context.cacheDir) actualSrc.delete()
            }
            onProgress(ArchiveProgress(isRunning = false, isCompleted = true, message = "เสร็จสิ้น", canCancel = false))
        } catch (e: kotlinx.coroutines.CancellationException) {
            onProgress(ArchiveProgress(isRunning = false, isError = true, message = "ยกเลิกแล้ว", canCancel = false))
            throw e
        } catch (e: Exception) {
            onProgress(ArchiveProgress(isRunning = false, isError = true, message = "เกิดข้อผิดพลาด: ${e.message}", canCancel = false))
        }
    }

    suspend fun extractSplitArchive(
        splitInfo: SplitArchiveInfo,
        outputDir: String,
        password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "zon_split_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            onProgress(ArchiveProgress(isRunning = true, message = "กำลังรวมไฟล์ที่แบ่งไว้...", total = splitInfo.totalSize))
            val mergedFile = splitManager.mergeSplitParts(
                splitInfo,
                File(tempDir, splitInfo.baseName).absolutePath
            ) { current, total ->
                onProgress(ArchiveProgress(isRunning = true, currentFile = "Merge", processed = current, total = total, message = "กำลังรวมไฟล์ที่แบ่งไว้..."))
            }
            val mergedItem = FileItem(
                name = mergedFile.name, path = mergedFile.absolutePath,
                isDirectory = false, size = mergedFile.length(),
                lastModified = mergedFile.lastModified()
            )
            extract(mergedItem, outputDir, password, onProgress)
            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            false
        }
    }

    suspend fun createSplitArchive(
        sources: List<FileItem>, outputName: String, splitSizeMb: Int,
        level: CompressionOption, password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalBytes = sources.sumOf {
            if (it.isDirectory) getDirectorySize(File(it.path)) else it.size
        }
        try {
            onProgress(ArchiveProgress(isRunning = true, message = "กำลังบีบอัดแบบแบ่งส่วน...", total = totalBytes))
            val outputPath = File(context.cacheDir, outputName).absolutePath
            val zipFile = ZipFile(outputPath)
            val pwdChars: CharArray? = if (!password.isNullOrEmpty()) password.toCharArray() else null
            val params = ZipParameters()
            params.compressionMethod = if (level == CompressionOption.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
            params.compressionLevel = when (level) {
                CompressionOption.STORE -> ZipLevel.NO_COMPRESSION
                CompressionOption.FAST -> ZipLevel.FASTEST
                CompressionOption.NORMAL -> ZipLevel.NORMAL
                CompressionOption.MAXIMUM -> ZipLevel.MAXIMUM
                CompressionOption.ULTRA -> ZipLevel.MAXIMUM
            }
            if (pwdChars != null) {
                params.isEncryptFiles = true
                params.encryptionMethod = EncryptionMethod.AES
                params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                applyPassword(params, pwdChars)
            }
            val filesToAdd = mutableListOf<File>()
            sources.forEach { item ->
                val file = File(item.path)
                if (file.isDirectory) collectFiles(file, filesToAdd) else filesToAdd.add(file)
            }
            zipFile.createSplitZipFile(filesToAdd, params, true, splitSizeMb.toLong() * 1024 * 1024)
            onProgress(ArchiveProgress(isRunning = false, isCompleted = true, message = "เสร็จสิ้น", canCancel = false))
        } catch (e: Exception) {
            onProgress(ArchiveProgress(isRunning = false, isError = true, message = "เกิดข้อผิดพลาด: ${e.message}", canCancel = false))
        }
    }

    private suspend fun splitFile(path: String, chunkSize: Long, onProgress: suspend (Long, Long) -> Unit) {
        val src = File(path)
        val total = src.length()
        if (total <= chunkSize) return
        var partNumber = 1
        var bytesRead = 0L
        src.inputStream().buffered(64 * 1024).use { input ->
            while (bytesRead < total) {
                val outFile = File("$path.${String.format("%03d", partNumber)}")
                outFile.outputStream().buffered(64 * 1024).use { output ->
                    var written = 0L
                    val tempBuf = ByteArray(64 * 1024)
                    while (written < chunkSize && bytesRead < total) {
                        coroutineContext.ensureActive()
                        val toRead = minOf(64 * 1024L, chunkSize - written, total - bytesRead).toInt()
                        val n = input.read(tempBuf, 0, toRead)
                        if (n <= 0) break
                        output.write(tempBuf, 0, n); written += n; bytesRead += n
                        onProgress(bytesRead, total)
                    }
                }
                partNumber++
            }
        }
        src.delete()
    }

    private suspend fun checkAndJoinSplit(src: File, onProgress: suspend (ArchiveProgress) -> Unit): File {
        if (!src.name.endsWith(".001")) return src
        val basePath = src.absolutePath.removeSuffix(".001")
        val baseFile = File(basePath)
        if (baseFile.exists()) return baseFile
        val parts = mutableListOf<File>()
        var i = 1
        while (true) {
            val part = File("$basePath.${String.format("%03d", i)}")
            if (!part.exists()) break
            parts.add(part); i++
        }
        if (parts.isEmpty()) return src
        val total = parts.sumOf { it.length() }
        onProgress(ArchiveProgress(isRunning = true, currentFile = "รวมไฟล์...", processed = 0, total = total, message = "กำลังรวมไฟล์..."))
        val temp = File(context.cacheDir, "zon_joined_${System.currentTimeMillis()}")
        var processed = 0L
        temp.outputStream().buffered(64 * 1024).use { out ->
            parts.forEach { part ->
                part.inputStream().buffered(64 * 1024).use { inp ->
                    BufferPool.withBuffer { buf ->
                        var n: Int
                        while (inp.read(buf).also { n = it } > 0) {
                            coroutineContext.ensureActive()
                            out.write(buf, 0, n); processed += n
                            if (processed % (4 * 1024 * 1024) < buf.size) {
                                onProgress(ArchiveProgress(isRunning = true, currentFile = part.name, processed = processed, total = total, message = "กำลังรวมไฟล์..."))
                            }
                        }
                    }
                }
            }
        }
        return temp
    }

    /** Resolve an archive entry strictly inside the selected extraction directory.
     * Rejects absolute paths, traversal, and link-like archive entries.
     */
    private fun safeExtractionFile(outputDir: File, entryName: String): File {
        if (entryName.isBlank()) throw IOException("ชื่อไฟล์ใน archive ว่าง")
        val normalizedName = entryName.replace('\\', '/')
        if (normalizedName.startsWith("/") || normalizedName.startsWith("\\") || Regex("^[A-Za-z]:/.*").matches(normalizedName)) {
            throw SecurityException("Archive entry เป็น absolute path: $entryName")
        }
        val root = outputDir.canonicalFile
        val target = File(root, normalizedName).canonicalFile
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        if (target.path != root.path && !target.path.startsWith(rootPath)) {
            throw SecurityException("Archive entry พยายามเขียนนอกโฟลเดอร์ปลายทาง: $entryName")
        }
        return target
    }

    private fun ensureRegularArchiveEntry(isDirectory: Boolean, isSymlink: Boolean, isLink: Boolean = false) {
        if (!isDirectory && (isSymlink || isLink)) {
            throw SecurityException("ไม่อนุญาต symbolic/hard link ใน archive")
        }
    }

    private suspend fun compressZip(
        sources: List<FileItem>, outputPath: String, level: CompressionOption,
        pwd: String?, onProgress: suspend (String, Long) -> Unit
    ) {
        val zipFile = ZipFile(outputPath)
        val pwdChars: CharArray? = if (!pwd.isNullOrEmpty()) pwd.toCharArray() else null
        val params = ZipParameters()
        params.compressionMethod = if (level == CompressionOption.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
        params.compressionLevel = when (level) {
            CompressionOption.STORE -> ZipLevel.NO_COMPRESSION
            CompressionOption.FAST -> ZipLevel.FASTEST
            CompressionOption.NORMAL -> ZipLevel.NORMAL
            CompressionOption.MAXIMUM -> ZipLevel.MAXIMUM
            CompressionOption.ULTRA -> ZipLevel.MAXIMUM
        }
        if (pwdChars != null) {
            params.isEncryptFiles = true
            params.encryptionMethod = EncryptionMethod.AES
            params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            applyPassword(params, pwdChars)
        }
        sources.forEach { item ->
            coroutineContext.ensureActive()
            val file = File(item.path)
            if (file.isDirectory) {
                zipFile.addFolder(file, params)
                onProgress(file.name, getDirectorySize(file))
            } else {
                zipFile.addFile(file, params)
                onProgress(file.name, file.length())
            }
        }
    }

    /**
     * Extract ZIP พร้อม ProgressMonitor ของ zip4j
     * ใช้ poll ทุก 100ms เพื่ออัปเดต UI แบบไม่ให้ recomposition ถี่เกิน
     */
    private suspend fun extractZipWithProgress(
        src: File, outputDir: String, password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val root = File(outputDir).canonicalFile.apply { mkdirs() }
        val zipFile = if (password.isNullOrEmpty()) ZipFile(src) else ZipFile(src, password.toCharArray())
        if (zipFile.isEncrypted && password.isNullOrEmpty()) throw Exception("ไฟล์นี้ต้องใช้รหัสผ่าน")
        var processed = 0L
        val total = zipFile.fileHeaders.sumOf { maxOf(0L, it.uncompressedSize) }
        zipFile.use { zf ->
            zf.fileHeaders.forEach { header ->
                coroutineContext.ensureActive()
                val outFile = safeExtractionFile(root, header.fileName)
                if (header.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(header).use { input ->
                        outFile.outputStream().buffered(64 * 1024).use { output ->
                            BufferPool.withBuffer { buf ->
                                var n: Int
                                while (input.read(buf).also { n = it } > 0) {
                                    coroutineContext.ensureActive()
                                    output.write(buf, 0, n)
                                    processed += n
                                    onProgress(ArchiveProgress(true, header.fileName, processed, total, "กำลังแตกไฟล์ ZIP..."))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun compress7z(
        sources: List<FileItem>, outputPath: String,
        onProgress: suspend (String, Long) -> Unit
    ) {
        SevenZOutputFile(File(outputPath)).use { out ->
            sources.forEach { item ->
                coroutineContext.ensureActive()
                val file = File(item.path)
                if (file.isDirectory) addDirTo7z(out, file, file.name, onProgress)
                else addFileTo7z(out, file, file.name, onProgress)
            }
            out.finish()
        }
    }

    private suspend fun addDirTo7z(out: SevenZOutputFile, dir: File, entryName: String, onProgress: suspend (String, Long) -> Unit) {
        val e = SevenZArchiveEntry().apply { name = entryName; isDirectory = true }
        out.putArchiveEntry(e); out.closeArchiveEntry()
        dir.listFiles()?.forEach { child ->
            coroutineContext.ensureActive()
            val childName = "$entryName/${child.name}"
            if (child.isDirectory) addDirTo7z(out, child, childName, onProgress)
            else addFileTo7z(out, child, childName, onProgress)
        }
    }

    private suspend fun addFileTo7z(out: SevenZOutputFile, file: File, entryName: String, onProgress: suspend (String, Long) -> Unit) {
        val e = SevenZArchiveEntry().apply { name = entryName; size = file.length() }
        out.putArchiveEntry(e)
        FileInputStream(file).use { input ->
            BufferPool.withBuffer { buf ->
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    coroutineContext.ensureActive(); out.write(buf, 0, n)
                }
            }
        }
        out.closeArchiveEntry()
        onProgress(file.name, file.length())
    }

    private suspend fun extract7z(
        src: File, outputDir: String, password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val archive = if (password.isNullOrEmpty()) SevenZFile(src) else SevenZFile(src, password.toCharArray())
        archive.use { arch ->
            var entry: SevenZArchiveEntry? = arch.nextEntry
            var processed = 0L
            val total = src.length()
            var lastReported = 0L
            while (entry != null) {
                coroutineContext.ensureActive()
                val current = entry ?: break
                ensureRegularArchiveEntry(current.isDirectory, false)
                val outFile = safeExtractionFile(File(outputDir).apply { mkdirs() }, current.name)
                if (current.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        BufferPool.withBuffer { buf ->
                            var n: Int
                            while (arch.read(buf).also { n = it } > 0) {
                                output.write(buf, 0, n); processed += n
                            }
                        }
                    }
                    onProgress(ArchiveProgress(isRunning = true, currentFile = current.name, processed = processed, total = total, message = "กำลังแตกไฟล์..."))
                    lastReported = processed
                }
                entry = arch.nextEntry
            }
        }
    }

    private fun collectFiles(dir: File, list: MutableList<File>) {
        dir.listFiles()?.forEach { if (it.isDirectory) collectFiles(it, list) else list.add(it) }
    }

    private suspend fun compressTar(
        sources: List<FileItem>, outputPath: String, compression: String?,
        onProgress: suspend (String, Long) -> Unit
    ) {
        val fileOut = FileOutputStream(outputPath).buffered(64 * 1024)
        val compressedOut: OutputStream = when (compression) {
            "gz" -> GzipCompressorOutputStream(fileOut)
            "bz2" -> BZip2CompressorOutputStream(fileOut)
            "xz" -> XZCompressorOutputStream(fileOut, LZMA2Options.PRESET_MAX)
            "lz4" -> net.jpountz.lz4.LZ4FrameOutputStream(fileOut)
            else -> fileOut
        }
        TarArchiveOutputStream(compressedOut).use { tarOut ->
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            sources.forEach { item ->
                coroutineContext.ensureActive()
                val file = File(item.path)
                if (file.isDirectory) addDirToTar(tarOut, file, file.name, onProgress)
                else addFileToTar(tarOut, file, file.name, onProgress)
            }
        }
    }

    private suspend fun addDirToTar(tarOut: TarArchiveOutputStream, dir: File, entryName: String, onProgress: suspend (String, Long) -> Unit) {
        val entry = TarArchiveEntry(dir, "$entryName/")
        tarOut.putArchiveEntry(entry); tarOut.closeArchiveEntry()
        dir.listFiles()?.forEach { child ->
            coroutineContext.ensureActive()
            val childName = "$entryName/${child.name}"
            if (child.isDirectory) addDirToTar(tarOut, child, childName, onProgress)
            else addFileToTar(tarOut, child, childName, onProgress)
        }
    }

    private suspend fun addFileToTar(tarOut: TarArchiveOutputStream, file: File, entryName: String, onProgress: suspend (String, Long) -> Unit) {
        val entry = TarArchiveEntry(file, entryName)
        tarOut.putArchiveEntry(entry)
        FileInputStream(file).use { input ->
            BufferPool.withBuffer { buf ->
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    coroutineContext.ensureActive(); tarOut.write(buf, 0, n)
                }
            }
        }
        tarOut.closeArchiveEntry()
        onProgress(file.name, file.length())
    }

    private suspend fun extractTarStream(
        src: File, outputDir: String, compression: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val fileIn = FileInputStream(src).buffered(64 * 1024)
        val decompressedIn: InputStream = when (compression) {
            "gz" -> GzipCompressorInputStream(fileIn)
            "bz2" -> BZip2CompressorInputStream(fileIn)
            "xz" -> XZCompressorInputStream(fileIn)
            "lz4" -> net.jpountz.lz4.LZ4FrameInputStream(fileIn)
            else -> fileIn
        }
        var processed = 0L
        val total = src.length()
        TarArchiveInputStream(decompressedIn).use { tarIn ->
            var entry: TarArchiveEntry? = tarIn.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val current = entry ?: break
                ensureRegularArchiveEntry(current.isDirectory, current.isSymbolicLink, current.isLink)
                val outFile = safeExtractionFile(File(outputDir).apply { mkdirs() }, current.name)
                if (current.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        BufferPool.withBuffer { buf ->
                            var n: Int
                            while (tarIn.read(buf).also { n = it } > 0) {
                                output.write(buf, 0, n); processed += n
                            }
                        }
                    }
                    onProgress(ArchiveProgress(isRunning = true, currentFile = current.name, processed = processed, total = total, message = "กำลังแตกไฟล์ TAR..."))
                }
                entry = tarIn.nextEntry
            }
        }
    }

    private suspend fun compressTarMd5(
        sources: List<FileItem>, outputPath: String,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val totalBytes = sources.sumOf { if (it.isDirectory) getDirectorySize(File(it.path)) else it.size }
        val tempFile = File(context.cacheDir, "zon_tar_${System.currentTimeMillis()}.tmp")
        try {
            var p1 = 0L
            val p1Cb: suspend (String, Long) -> Unit = { name, bytes ->
                p1 += bytes
                onProgress(ArchiveProgress(isRunning = true, currentFile = name, processed = (p1 * 60) / 100, total = totalBytes, message = "สร้าง TAR..."))
                coroutineContext.ensureActive()
            }
            val fileOut = FileOutputStream(tempFile).buffered(64 * 1024)
            TarArchiveOutputStream(fileOut).use { tarOut ->
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                sources.forEach { item ->
                    coroutineContext.ensureActive()
                    val file = File(item.path)
                    if (file.isDirectory) addDirToTar(tarOut, file, file.name, p1Cb)
                    else addFileToTar(tarOut, file, file.name, p1Cb)
                }
            }
            onProgress(ArchiveProgress(isRunning = true, currentFile = "MD5", processed = totalBytes * 60 / 100, total = totalBytes, message = "คำนวณ MD5..."))
            val md5 = MessageDigest.getInstance("MD5")
            FileInputStream(tempFile).use { input ->
                BufferPool.withBuffer { buf ->
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        coroutineContext.ensureActive(); md5.update(buf, 0, n)
                    }
                }
            }
            val md5Bytes = md5.digest()
            FileOutputStream(outputPath).use { out ->
                FileInputStream(tempFile).use { inp ->
                    BufferPool.withBuffer { buf ->
                        var n: Int
                        while (inp.read(buf).also { n = it } > 0) {
                            coroutineContext.ensureActive(); out.write(buf, 0, n)
                        }
                    }
                }
                out.write(md5Bytes); out.write("MD5".toByteArray())
            }
        } finally { tempFile.delete() }
    }

    private suspend fun extractTarMd5(
        src: File, outputDir: String,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val fileSize = src.length()
        if (fileSize < 19) throw Exception("ไฟล์เสียหาย")
        val hasSuffix = RandomAccessFile(src, "r").use { raf ->
            raf.seek(fileSize - 3); val tail = ByteArray(3); raf.readFully(tail)
            String(tail, Charsets.US_ASCII) == "MD5"
        }
        val trailing = if (hasSuffix) 19L else 16L
        val tarLength = fileSize - trailing
        val rawIn = FileInputStream(src).buffered(64 * 1024)
        val bounded = BoundedInputStream(rawIn, tarLength)
        var processed = 0L
        TarArchiveInputStream(bounded).use { tarIn ->
            var entry: TarArchiveEntry? = tarIn.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val current = entry ?: break
                ensureRegularArchiveEntry(current.isDirectory, current.isSymbolicLink, current.isLink)
                val outFile = safeExtractionFile(File(outputDir).apply { mkdirs() }, current.name)
                if (current.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        BufferPool.withBuffer { buf ->
                            var n: Int
                            while (tarIn.read(buf).also { n = it } > 0) {
                                output.write(buf, 0, n); processed += n
                            }
                        }
                    }
                    onProgress(ArchiveProgress(isRunning = true, currentFile = current.name, processed = processed, total = tarLength, message = "กำลังแตกไฟล์ TAR.MD5..."))
                }
                entry = tarIn.nextEntry
            }
        }
    }

    private suspend fun compressSingle(
        source: FileItem, outputPath: String, type: String,
        onProgress: suspend (String, Long) -> Unit
    ) {
        val src = File(source.path)
        val fileOut = FileOutputStream(outputPath).buffered(64 * 1024)
        val compressedOut: OutputStream = when (type) {
            "gz" -> GzipCompressorOutputStream(fileOut)
            "bz2" -> BZip2CompressorOutputStream(fileOut)
            "xz" -> XZCompressorOutputStream(fileOut, LZMA2Options.PRESET_MAX)
            "lz4" -> net.jpountz.lz4.LZ4FrameOutputStream(fileOut)
            else -> fileOut
        }
        FileInputStream(src).use { input ->
            compressedOut.use { output ->
                BufferPool.withBuffer { buf ->
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        coroutineContext.ensureActive(); output.write(buf, 0, n)
                    }
                }
            }
        }
        onProgress(src.name, src.length())
    }

    private suspend fun extractSingle(
        src: File, outputDir: String, type: String,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val outName = when (type) {
            "gz" -> src.name.removeSuffix(".gz")
            "bz2" -> src.name.removeSuffix(".bz2")
            "xz" -> src.name.removeSuffix(".xz")
            "lz4" -> src.name.removeSuffix(".lz4")
            else -> src.name
        }
        val outFile = safeExtractionFile(File(outputDir).apply { mkdirs() }, outName)
        val total = src.length()
        var processed = 0L
        val fileIn = FileInputStream(src).buffered(64 * 1024)
        val input: InputStream = when (type) {
            "gz" -> GzipCompressorInputStream(fileIn)
            "bz2" -> BZip2CompressorInputStream(fileIn)
            "xz" -> XZCompressorInputStream(fileIn)
            "lz4" -> net.jpountz.lz4.LZ4FrameInputStream(fileIn)
            else -> fileIn
        }
        input.use { inp ->
            FileOutputStream(outFile).use { output ->
                BufferPool.withBuffer { buf ->
                    var n: Int
                    while (inp.read(buf).also { n = it } > 0) {
                        coroutineContext.ensureActive(); output.write(buf, 0, n); processed += n
                    }
                }
            }
        }
        onProgress(ArchiveProgress(isRunning = false, isCompleted = true, message = "เสร็จสิ้น", canCancel = false))
    }

    private suspend fun extractRar(
        src: File, outputDir: String, password: String?,
        onProgress: suspend (ArchiveProgress) -> Unit
    ) {
        val archive = if (password.isNullOrEmpty()) Archive(src) else Archive(src, password)
        if (archive.isEncrypted && password.isNullOrEmpty()) throw Exception("ไฟล์นี้ต้องใช้รหัสผ่าน")
        archive.use { arch ->
            val headers = arch.fileHeaders
            var processed = 0L
            val total = src.length()
            headers.forEach { header ->
                coroutineContext.ensureActive()
                val outFile = safeExtractionFile(File(outputDir).apply { mkdirs() }, header.fileName)
                if (header.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        arch.extractFile(header, output)
                    }
                    processed += header.fullUnpackSize
                    onProgress(ArchiveProgress(isRunning = true, currentFile = header.fileName, processed = processed, total = total, message = "กำลังแตกไฟล์ RAR..."))
                }
            }
        }
    }

    private suspend fun generateMd5(
        source: FileItem, outputPath: String,
        onProgress: suspend (String, Long) -> Unit
    ) {
        val src = File(source.path)
        val md = MessageDigest.getInstance("MD5")
        var processed = 0L
        FileInputStream(src).use { input ->
            BufferPool.withBuffer { buf ->
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    coroutineContext.ensureActive(); md.update(buf, 0, n); processed += n
                }
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        File(outputPath).writeText("$hash  ${src.name}\n")
        onProgress(src.name, processed)
    }

    private fun getDirectorySize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { size += if (it.isDirectory) getDirectorySize(it) else it.length() }
        return size
    }
}
