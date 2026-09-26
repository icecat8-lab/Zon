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

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** Copy / move / delete / rename for plain files and folders, cancellation-safe. */
object FileOperations {

    private const val BUFFER_SIZE = 256 * 1024

    suspend fun copy(source: File, destDir: File, onProgress: (String) -> Unit): File {
        val target = uniqueTarget(File(destDir, source.name))
        copyRecursive(source, target, onProgress)
        return target
    }

    /** Tries a same-filesystem rename first (instant); falls back to copy+delete across volumes. */
    suspend fun move(source: File, destDir: File, onProgress: (String) -> Unit): File {
        val target = uniqueTarget(File(destDir, source.name))
        if (source.renameTo(target)) return target
        copyRecursive(source, target, onProgress)
        deleteRecursive(source)
        return target
    }

    private suspend fun copyRecursive(source: File, target: File, onProgress: (String) -> Unit) {
        coroutineContext.ensureActive()
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyRecursive(child, File(target, child.name), onProgress)
            }
        } else {
            onProgress(source.name)
            target.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun rename(file: File, newName: String): File {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("กรุณาใส่ชื่อไฟล์")
        val target = File(file.parentFile, trimmed)
        if (target.exists()) throw IllegalArgumentException("มีไฟล์หรือโฟลเดอร์ชื่อนี้อยู่แล้ว")
        if (!file.renameTo(target)) throw IllegalStateException("เปลี่ยนชื่อไม่สำเร็จ")
        return target
    }

    /** Recursively sums the size of a file or folder, for progress totals. */
    fun sizeOf(file: File): Long {
        if (!file.isDirectory) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += sizeOf(it) }
        return total
    }

    private fun uniqueTarget(preferred: File): File {
        if (!preferred.exists()) return preferred
        val base = preferred.nameWithoutExtension
        val ext = preferred.extension
        var i = 1
        while (true) {
            val candidate = if (ext.isBlank()) {
                File(preferred.parentFile, "$base ($i)")
            } else {
                File(preferred.parentFile, "$base ($i).$ext")
            }
            if (!candidate.exists()) return candidate
            i++
        }
    }
}
