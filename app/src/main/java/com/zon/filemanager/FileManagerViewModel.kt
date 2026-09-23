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

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    FILES,
    RECENT,
    SETTINGS,
    ABOUT,
    LICENSES,
    PREVIEW,
    TEXT_EDITOR,
    USB_OTG
}

data class FileManagerState(
    val currentScreen: Screen = Screen.FILES,
    val currentPath: String = "",
    val recent: List<FileItem> = emptyList(),
    val rootAvailable: Boolean = false,
    val rootEnabled: Boolean = false,
    val previewItem: FileItem? = null,
    val textEditorItem: FileItem? = null,
    val usbFiles: List<FileItem> = emptyList(),
    val usbLoading: Boolean = false,
    val usbAvailable: Boolean = false
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesManager = FavoritesRecentManager(application)
    private val usbOtgManager = UsbOtgManager(application)

    private val _state = MutableStateFlow(
        FileManagerState(currentPath = Environment.getExternalStorageDirectory().absolutePath)
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init {
        loadRecent()
        recheckRoot()
        refreshUsbAvailability()
    }

    // ==================== Navigation ====================

    fun setScreen(screen: Screen) {
        _state.update { it.copy(currentScreen = screen) }
    }

    fun navigateTo(path: String) {
        _state.update { it.copy(currentPath = path, currentScreen = Screen.FILES) }
    }

    fun navigateToRoot() {
        navigateTo("/")
    }

    fun navigateUp() {
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        val currentPath = _state.value.currentPath

        // Jumped to the true filesystem root "/" via the root-arrow — climbing back
        // up from there returns to the normal device-storage view.
        if (currentPath == "/") {
            navigateTo(storageRoot)
            return
        }

        val parent = File(currentPath).parentFile ?: return
        _state.update { it.copy(currentPath = parent.absolutePath) }
    }

    // ==================== Recent ====================

    fun loadRecent() {
        viewModelScope.launch {
            val recentItems = favoritesManager.getRecent()
            _state.update { it.copy(recent = recentItems) }
        }
    }

    fun openRecent() {
        loadRecent()
        setScreen(Screen.RECENT)
    }

    fun removeRecent(path: String) {
        viewModelScope.launch {
            favoritesManager.removeRecent(path)
            loadRecent()
        }
    }

    fun clearRecent() {
        viewModelScope.launch {
            favoritesManager.clearRecent()
            loadRecent()
        }
    }

    // ==================== Opening files ====================

    fun openFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (!file.exists()) return

        if (file.isDirectory) {
            navigateTo(file.absolutePath)
            return
        }

        viewModelScope.launch {
            favoritesManager.addRecent(fileItem)
            loadRecent()
        }

        when {
            fileItem.isImage() -> {
                _state.update { it.copy(previewItem = fileItem, currentScreen = Screen.PREVIEW) }
            }
            fileItem.isText() -> {
                _state.update { it.copy(textEditorItem = fileItem, currentScreen = Screen.TEXT_EDITOR) }
            }
            else -> {
                FileOpener.openFile(getApplication<Application>(), file)
            }
        }
    }

    fun openFromList(fileItem: FileItem) = openFile(fileItem)

    // ==================== Text editor ====================

    fun saveTextFile(fileItem: FileItem, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(fileItem.path).writeText(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==================== Root access ====================

    fun recheckRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = checkRootAvailable()
            _state.update { it.copy(rootAvailable = available, rootEnabled = it.rootEnabled && available) }
        }
    }

    fun toggleRoot(enabled: Boolean) {
        _state.update { it.copy(rootEnabled = enabled && it.rootAvailable) }
    }

    private fun checkRootAvailable(): Boolean {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/data/local/xbin/su", "/data/local/bin/su"
        )
        return suPaths.any { File(it).exists() }
    }

    // ==================== USB OTG ====================

    fun refreshUsbAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = try {
                val sm = getApplication<Application>()
                    .getSystemService(Context.STORAGE_SERVICE) as StorageManager
                sm.storageVolumes.any { !it.isPrimary }
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(usbAvailable = available) }
        }
    }

    fun loadUsbFiles(uri: Uri) {
        _state.update { it.copy(usbLoading = true, currentScreen = Screen.USB_OTG) }
        viewModelScope.launch {
            val files = usbOtgManager.listDocumentFiles(uri)
            _state.update { it.copy(usbFiles = files, usbLoading = false) }
        }
    }

    fun navigateUsbDirectory(uri: Uri) = loadUsbFiles(uri)

    fun closeUsbMode() {
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        _state.update {
            it.copy(
                usbFiles = emptyList(),
                usbLoading = false,
                currentPath = storageRoot,
                currentScreen = Screen.FILES
            )
        }
    }
}
