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

// app/src/main/java/com/zon/filemanager/UsbOtgManager.kt
package com.zon.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.activity.result.ActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UsbVolume(
    val uuid: String,
    val description: String,
    val path: String,
    val isMounted: Boolean,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L
)

class UsbOtgManager(private val context: Context) {

    private val storageManager: StorageManager =
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    fun getStorageVolumes(): List<UsbVolume> {
        val volumes = mutableListOf<UsbVolume>()
        storageManager.storageVolumes.forEach { volume ->
            val uuid = volume.uuid ?: "primary"
            val desc = volume.getDescription(context) ?: "Storage"
            val path = volume.directory?.absolutePath ?: return@forEach
            val isMounted = volume.state == android.os.Environment.MEDIA_MOUNTED
            val isRemovable = volume.isRemovable
            val isPrimary = volume.isPrimary
            volumes.add(UsbVolume(
                uuid = uuid,
                description = desc,
                path = path,
                isMounted = isMounted,
                isRemovable = isRemovable,
                isPrimary = isPrimary
            ))
        }
        return volumes
    }

    fun getUsbVolumes(): List<UsbVolume> = getStorageVolumes().filter { it.isRemovable && !it.isPrimary }
    fun getSdCardVolumes(): List<UsbVolume> = getStorageVolumes().filter { it.isRemovable && it.isPrimary }

    fun createAccessIntent(volume: StorageVolume): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            volume.createOpenDocumentTreeIntent()
        } else {
            @Suppress("DEPRECATION")
            volume.createAccessIntent(null)
        }
    }

    fun launchVolumeAccess(launcher: ActivityResultLauncher<Intent>, volume: StorageVolume) {
        val intent = createAccessIntent(volume) ?: return
        launcher.launch(intent)
    }

    fun getStorageVolumeForPath(path: String): StorageVolume? {
        return storageManager.storageVolumes.firstOrNull { volume ->
            volume.directory?.absolutePath?.let { dirPath ->
                path.startsWith(dirPath)
            } ?: false
        }
    }

    suspend fun listDocumentFiles(uri: Uri): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            root.listFiles().map { doc ->
                FileItem(
                    name = doc.name ?: "unknown",
                    path = doc.uri.toString(),
                    isDirectory = doc.isDirectory,
                    size = doc.length(),
                    lastModified = doc.lastModified()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun canWriteToVolume(volume: StorageVolume): Boolean {
        return volume.state == android.os.Environment.MEDIA_MOUNTED && volume.isRemovable
    }

    fun getVolumeFreeSpace(volume: StorageVolume): Long {
        return try {
            volume.directory?.let { dir ->
                android.os.StatFs(dir.absolutePath).availableBytes
            } ?: 0L
        } catch (e: Exception) {
            try {
                storageManager.getAllocatableBytes(volume.storageUuid!!)
            } catch (e2: Exception) {
                0L
            }
        }
    }

    fun getVolumeTotalSpace(volume: StorageVolume): Long {
        return try {
            volume.directory?.let { dir ->
                android.os.StatFs(dir.absolutePath).totalBytes
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun readDocumentFile(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }
    }

    suspend fun writeDocumentFile(uri: Uri, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteDocumentFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
        } catch (e: Exception) { false }
    }

    suspend fun createDocumentDirectory(parentUri: Uri, name: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return@withContext null
            parent.createDirectory(name)?.uri
        } catch (e: Exception) { null }
    }

    suspend fun copyDocumentFile(sourceUri: Uri, destParentUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return@withContext false
            val destParent = DocumentFile.fromTreeUri(context, destParentUri) ?: return@withContext false
            val dest = destParent.createFile("*/*", source.name ?: "file") ?: return@withContext false
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                    input.copyTo(output, 512 * 1024)
                }
            }
            true
        } catch (e: Exception) { false }
    }
}
