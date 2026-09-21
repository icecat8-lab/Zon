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
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadFiles(_currentPath.value)
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentPath.value = path
            val files = withContext(Dispatchers.IO) {
                val directory = File(path)
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.map { FileItem(it) }?.sortedWith(
                        compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() }
                    ) ?: emptyList()
                } else {
                    emptyList()
                }
            }
            _fileList.value = files
            _selectedFiles.value = emptySet()
            _isLoading.value = false
        }
    }

    fun navigateTo(path: String) {
        loadFiles(path)
    }

    fun navigateUp(): Boolean {
        val currentFile = File(_currentPath.value)
        val parent = currentFile.parentFile
        return if (parent != null && parent.canRead()) {
            loadFiles(parent.absolutePath)
            true
        } else {
            false
        }
    }

    fun toggleSelection(filePath: String) {
        val currentSelection = _selectedFiles.value.toMutableSet()
        if (currentSelection.contains(filePath)) {
            currentSelection.remove(filePath)
        } else {
            currentSelection.add(filePath)
        }
        _selectedFiles.value = currentSelection
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun selectAll() {
        _selectedFiles.value = _fileList.value.map { it.path }.toSet()
    }

    fun deleteSelectedFiles(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                _selectedFiles.value.forEach { path ->
                    val file = File(path)
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
            }
            loadFiles(_currentPath.value)
            onComplete()
        }
    }

    fun createNewFolder(folderName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val newDir = File(_currentPath.value, folderName)
                if (!newDir.exists()) newDir.mkdirs() else false
            }
            if (success) {
                loadFiles(_currentPath.value)
            }
            onComplete(success)
        }
    }

    fun renameFile(oldPath: String, newName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val file = File(oldPath)
                val targetFile = File(file.parent, newName)
                if (!targetFile.exists()) file.renameTo(targetFile) else false
            }
            if (success) {
                loadFiles(_currentPath.value)
            }
            onComplete(success)
        }
    }
}
