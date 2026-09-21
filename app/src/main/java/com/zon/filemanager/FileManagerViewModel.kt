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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    SETTINGS,
    ABOUT,
    LICENSES
}

data class FileManagerState(
    val currentScreen: Screen = Screen.HOME,
    val favorites: List<FileItem> = emptyList(),
    val currentPath: String = ""
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesManager = FavoritesRecentManager(application)

    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init {
        loadFavorites()
    }

    fun setScreen(screen: Screen) {
        _state.update { it.copy(currentScreen = screen) }
    }

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

    fun openFile(fileItem: FileItem) {
        val context = getApplication<Application>()
        val file = File(fileItem.path)
        if (file.exists()) {
            if (file.isDirectory) {
                _state.update { it.copy(currentPath = file.absolutePath, currentScreen = Screen.HOME) }
            } else {
                FileOpener.openFile(context, file)
            }
        }
    }
}
