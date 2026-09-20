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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootHelper {

    @Volatile
    private var rootAvailable: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        rootAvailable?.let { return@withContext it }
        val result = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine() ?: ""
            process.waitFor()
            line.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        rootAvailable = result
        result
    }

    suspend fun execCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    suspend fun listDirectoryRoot(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val output = execCommand("ls -la '$path' 2>/dev/null")
            val lines = output.lines().drop(1).filter { it.isNotBlank() }
            lines.mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 9)
                if (parts.size < 9) return@mapNotNull null
                val name = parts[8]
                if (name == "." || name == "..") return@mapNotNull null
                val isDir = parts[0].startsWith("d")
                val size = parts[4].toLongOrNull() ?: 0L
                val fullPath = if (path == "/") "/$name" else "$path/$name"
                FileItem(
                    name = name,
                    path = fullPath,
                    isDirectory = isDir,
                    size = size,
                    lastModified = 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun copyAsRoot(src: String, dst: String): Boolean = withContext(Dispatchers.IO) {
        execCommand("cp -rf '$src' '$dst'").contains("ERROR").not()
    }

    suspend fun deleteAsRoot(path: String): Boolean = withContext(Dispatchers.IO) {
        execCommand("rm -rf '$path'").contains("ERROR").not()
    }

    suspend fun moveAsRoot(src: String, dst: String): Boolean = withContext(Dispatchers.IO) {
        execCommand("mv -f '$src' '$dst'").contains("ERROR").not()
    }

    fun resetCache() {
        rootAvailable = null
    }
}
