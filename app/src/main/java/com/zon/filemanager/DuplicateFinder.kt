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
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DuplicateGroup(
    val hash: String,
    val files: List<FileItem>,
    val totalSize: Long
)

class DuplicateFinder {

    suspend fun findDuplicates(
        rootPath: String,
        onProgress: suspend (String, Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0

        fun walk(dir: File) {
            coroutineContext.ensureActive()
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) walk(file)
                else if (file.length() > 0) {
                    sizeMap.getOrPut(file.length()) { mutableListOf() }.add(file)
                    scanned++
                    if (scanned % 100 == 0) {
                        // Progress report is throttled in the caller
                    }
                }
            }
        }

        walk(File(rootPath))

        val candidates = sizeMap.filter { it.value.size > 1 }.values.flatten()
        val hashMap = mutableMapOf<String, MutableList<File>>()

        candidates.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            val hash = computeHash(file)
            if (hash != null) {
                hashMap.getOrPut(hash) { mutableListOf() }.add(file)
            }
            onProgress(file.name, index + 1)
        }

        hashMap.filter { it.value.size > 1 }.map { (hash, files) ->
            DuplicateGroup(
                hash = hash,
                files = files.map { it.toFileItem() },
                totalSize = files.sumOf { it.length() }
            )
        }.sortedByDescending { it.totalSize }
    }

    private fun computeHash(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
