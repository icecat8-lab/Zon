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
import android.net.Uri
import android.os.Environment
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
    HOME,
    FILES,
    FAVORITES,
    RECENT,
    SETTINGS,
    ABOUT,
    LICENSES,
    PREVIEW,
    TEXT_EDITOR,
    USB_OTG
}

data class FtpState(
    val connected: Boolean = false,
    val host: String = ""
)

data class FileManagerState(
    val currentScreen: Screen = Screen.HOME,
    val currentPath: String = "",
    val favorites: List<FileItem> = emptyList(),
    val recent: List<FileItem> = emptyList(),
    val rootAvailable: Boolean = false,
    val rootEnabled: Boolean = false,
    val ftpState: FtpState = FtpState(),
    val previewItem: FileItem? = null,
    val textEditorItem: FileItem? = null,
    val usbFiles: List<FileItem> = emptyList(),
    val usbLoading: Boolean = false
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesManager = FavoritesRecentManager(application)
    private val usbOtgManager = UsbOtgManager(application)

    private val _state = MutableStateFlow(
        FileManagerState(currentPath = Environment.getExternalStorageDirectory().absolutePath)
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init {
        loadFavorites()
        loadRecent()
        recheckRoot()
    }

    // ==================== Navigation ====================

    fun setScreen(screen: Screen) {
        _state.update { it.copy(currentScreen = screen) }
    }

    private fun navigateTo(path: String) {
        _state.update { it.copy(currentPath = path, currentScreen = Screen.FILES) }
    }

    fun navigateUp() {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val currentPath = _state.value.currentPath
        if (currentPath == rootPath) return
        val parent = File(currentPath).parentFile ?: return
        _state.update { it.copy(currentPath = parent.absolutePath) }
    }

    // ==================== Favorites ====================

    fun loadFavorites() {
        viewModelScope.launch {
            val favs = favoritesManager.getFavorites()
            _state.update { it.copy(favorites = favs) }
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            favoritesManager.toggleFavorite(item)
            loadFavorites()
        }
    }

    fun openFavorites() {
        setScreen(Screen.FAVORITES)
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

    // ==================== Sections not built yet ====================
    // These have no dedicated screen/state yet, kept as safe no-ops so
    // SettingsScreen compiles and remains clickable without crashing.

    fun openAppManager() { /* TODO: App manager screen not implemented yet */ }
    fun openStorageAnalyzer() { /* TODO: Storage analyzer screen not implemented yet */ }
    fun openDuplicateFinder() { /* TODO: Duplicate finder screen not implemented yet */ }
    fun openFtp() { /* TODO: FTP client screen not implemented yet */ }

    // ==================== USB OTG ====================

    fun loadUsbFiles(uri: Uri) {
        _state.update { it.copy(usbLoading = true, currentScreen = Screen.USB_OTG) }
        viewModelScope.launch {
            val files = usbOtgManager.listDocumentFiles(uri)
            _state.update { it.copy(usbFiles = files, usbLoading = false) }
        }
    }

    fun navigateUsbDirectory(uri: Uri) = loadUsbFiles(uri)

    fun closeUsbMode() {
        _state.update {
            it.copy(usbFiles = emptyList(), usbLoading = false, currentScreen = Screen.FILES)
        }
    }
}
