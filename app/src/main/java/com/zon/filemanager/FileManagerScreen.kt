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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // "All files access" (MANAGE_EXTERNAL_STORAGE) is declared in the manifest but Android
    // never grants it automatically on API 30+ — the user has to flip it on in Settings.
    // Without it, File.listFiles() on real folders like Documents/Download comes back
    // empty or missing items, which is why the list looked incomplete.
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasAllFilesAccess(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = PermissionHelper.hasAllFilesAccess(context)
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                permissionLauncher.launch(intent)
            } catch (e: Exception) {
                permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    LaunchedEffect(state.currentPath, hasPermission) {
        if (!hasPermission) {
            fileList = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        fileList = withContext(Dispatchers.IO) {
            val dir = File(state.currentPath)
            dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { f ->
                    FileItem(
                        name = f.name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        size = if (f.isDirectory) 0L else f.length(),
                        lastModified = f.lastModified(),
                        extension = f.extension
                    )
                } ?: emptyList()
        }
        isLoading = false
    }

    BackHandler(enabled = state.currentPath != rootPath) {
        viewModel.navigateUp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        Surface(color = ZonColors.DeepBlack) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.currentPath != rootPath) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    text = if (state.currentPath == rootPath) {
                        stringResource(R.string.internal_storage)
                    } else {
                        File(state.currentPath).name
                    },
                    color = ZonColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.openRecent() }) {
                    Icon(Icons.Outlined.History, null, tint = ZonColors.TextPrimary)
                }
                IconButton(onClick = { viewModel.openFavorites() }) {
                    Icon(Icons.Outlined.Star, null, tint = ZonColors.TextPrimary)
                }
                IconButton(onClick = { viewModel.setScreen(Screen.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, null, tint = ZonColors.TextPrimary)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(ZonColors.Surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                null,
                                tint = ZonColors.TextTertiary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "ต้องขอสิทธิ์เข้าถึงไฟล์ทั้งหมดก่อน",
                            color = ZonColors.TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "ถ้าไม่เปิดสิทธิ์นี้ แอปจะเห็นไฟล์ในเครื่องไม่ครบ",
                            color = ZonColors.TextTertiary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { requestAllFilesAccess() },
                            colors = ButtonDefaults.buttonColors(containerColor = ZonColors.Accent)
                        ) {
                            Text("อนุญาตการเข้าถึงไฟล์ทั้งหมด")
                        }
                    }
                }
                isLoading -> {
                    CircularProgressIndicator(
                        color = ZonColors.Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                fileList.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(ZonColors.Surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                null,
                                tint = ZonColors.TextTertiary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.empty), color = ZonColors.TextSecondary, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.empty_desc), color = ZonColors.TextTertiary, fontSize = 12.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(fileList, key = { it.path }) { file ->
                            FileManagerRow(
                                file = file,
                                isFavorite = state.favorites.any { it.path == file.path },
                                onClick = { viewModel.openFile(file) },
                                onToggleFavorite = { viewModel.toggleFavorite(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileManagerRow(
    file: FileItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(file.getIconColor().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                color = ZonColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (file.isDirectory) "โฟลเดอร์" else file.getReadableSize(),
                color = ZonColors.TextTertiary,
                fontSize = 11.sp
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (isFavorite) ZonColors.Warning else ZonColors.TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (file.isDirectory) {
            Icon(Icons.Filled.ChevronRight, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
