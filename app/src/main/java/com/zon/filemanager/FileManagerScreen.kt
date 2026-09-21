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

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(navController: NavController) {
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var fileList by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentPath) {
        isLoading = true
        fileList = withContext(Dispatchers.IO) {
            val dir = File(currentPath)
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = File(currentPath).name.ifEmpty { "Root" }, maxLines = 1) }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = fileList, key = { it.absolutePath }) { file ->
                        FileRowItem(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    currentPath = file.absolutePath
                                } else if (file.extension in listOf("zip", "7z", "rar")) {
                                    navController.navigate("archive_preview/${Uri.encode(file.absolutePath)}")
                                } else if (file.extension in listOf("txt", "json", "log", "md")) {
                                    navController.navigate("text_editor/${Uri.encode(file.absolutePath)}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileRowItem(file: File, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(file.name, maxLines = 1) },
        supportingContent = { 
            Text(if (file.isDirectory) "Folder" else "${file.length() / 1024} KB") 
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
