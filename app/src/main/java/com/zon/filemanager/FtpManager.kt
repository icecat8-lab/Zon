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

// app/src/main/java/com/zon/filemanager/FtpManager.kt
package com.zon.filemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Duration

data class FtpConnectionState(
    val host: String = "",
    val port: Int = 21,
    val user: String = "anonymous",
    val currentPath: String = "/",
    val connected: Boolean = false,
    val files: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class FtpManager {

    private var client: FTPClient? = null

    suspend fun connect(host: String, port: Int, user: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                disconnectInternal()
                val c = FTPClient()
                c.connectTimeout = 15000
                c.defaultTimeout = 15000
                c.setDataTimeout(Duration.ofSeconds(30))
                c.connect(host, port)
                if (!FTPReply.isPositiveCompletion(c.replyCode)) {
                    c.disconnect()
                    return@withContext false
                }
                val ok = c.login(user, password)
                if (!ok) {
                    try { c.logout() } catch (e: Exception) {}
                    try { c.disconnect() } catch (e: Exception) {}
                    return@withContext false
                }
                c.enterLocalPassiveMode()
                c.setFileType(FTP.BINARY_FILE_TYPE)
                client = c
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal() {
        try {
            client?.let {
                if (it.isConnected) {
                    try { it.logout() } catch (e: Exception) {}
                    try { it.disconnect() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        client = null
    }

    suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptyList()
        try {
            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)
            val files = c.listFiles(path)
            files.map { f ->
                FileItem(
                    name = f.name,
                    path = if (path.endsWith("/")) "$path${f.name}" else "$path/${f.name}",
                    isDirectory = f.isDirectory,
                    size = f.size,
                    lastModified = f.timestamp?.timeInMillis ?: 0L
                )
            }.filter { it.name != "." && it.name != ".." }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun changeDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try { c.changeWorkingDirectory(path) } catch (e: Exception) { false }
    }

    suspend fun download(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try {
            localFile.parentFile?.mkdirs()
            FileOutputStream(localFile).use { out ->
                c.retrieveFile(remotePath, out)
            }
        } catch (e: Exception) { false }
    }

    suspend fun upload(localFile: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try {
            FileInputStream(localFile).use { inp ->
                c.storeFile(remotePath, inp)
            }
        } catch (e: Exception) { false }
    }

    suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try {
            val ok = c.deleteFile(remotePath)
            if (!ok) c.removeDirectory(remotePath) else ok
        } catch (e: Exception) { false }
    }

    suspend fun mkdir(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try { c.makeDirectory(remotePath) } catch (e: Exception) { false }
    }

    suspend fun rename(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        try { c.rename(oldPath, newPath) } catch (e: Exception) { false }
    }

    fun isConnected(): Boolean = client?.isConnected == true
}
