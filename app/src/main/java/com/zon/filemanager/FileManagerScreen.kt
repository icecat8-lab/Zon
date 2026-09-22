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
import android.os.StatFs
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val atRoot = state.currentPath == rootPath

    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var totalBytes by remember { mutableStateOf(0L) }
    var usedBytes by remember { mutableStateOf(0L) }

    // "All files access" (MANAGE_EXTERNAL_STORAGE) is declared in the manifest but Android
    // never grants it automatically on API 30+ — the user has to flip it on in Settings.
    // Without it, File.listFiles() on real folders like Documents/Download comes back
    // empty or missing items.
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

    val usbPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // some providers don't support persistable permissions; safe to ignore
            }
            viewModel.loadUsbFiles(it)
        }
    }

    LaunchedEffect(atRoot) {
        if (atRoot) {
            viewModel.refreshUsbAvailability()
            try {
                val stat = StatFs(rootPath)
                totalBytes = stat.totalBytes
                usedBytes = totalBytes - stat.availableBytes
            } catch (e: Exception) {
                // leave at 0, the usage bar just won't be shown
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

    // At the device-storage root there's nowhere left to go "up" to inside the app —
    // let the system handle back (exits the app), same as the reference file manager.
    BackHandler(enabled = !atRoot) {
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
                if (!atRoot) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    text = when (state.currentPath) {
                        "/" -> "/"
                        rootPath -> stringResource(R.string.app_name)
                        else -> File(state.currentPath).name
                    },
                    color = ZonColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state.currentPath != "/") {
                    IconButton(onClick = { viewModel.navigateToRoot() }) {
                        Icon(Icons.Filled.ArrowDropDown, "ไปที่ราก /", tint = ZonColors.TextPrimary)
                    }
                }
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
                    PermissionGate(onGrant = { requestAllFilesAccess() })
                }
                isLoading -> {
                    CircularProgressIndicator(
                        color = ZonColors.Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                fileList.isEmpty() && !atRoot -> {
                    EmptyFolder()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (atRoot) {
                            item {
                                StorageCard(
                                    icon = Icons.Outlined.PhoneAndroid,
                                    title = stringResource(R.string.internal_storage),
                                    subtitle = rootPath,
                                    usedBytes = usedBytes,
                                    totalBytes = totalBytes,
                                    onClick = {}
                                )
                            }
                            if (state.usbAvailable) {
                                item { Spacer(Modifier.height(8.dp)) }
                                item {
                                    StorageCard(
                                        icon = Icons.Outlined.Usb,
                                        title = "USB OTG",
                                        subtitle = "แตะเพื่อเลือกไดรฟ์ USB",
                                        usedBytes = null,
                                        totalBytes = null,
                                        onClick = { usbPickerLauncher.launch(null) }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                            item {
                                ShortcutRow(Icons.Outlined.Download, "ดาวน์โหลด") {
                                    viewModel.navigateTo("$rootPath/Download")
                                }
                            }
                            item {
                                ShortcutRow(Icons.Outlined.MusicNote, "เพลง") {
                                    viewModel.navigateTo("$rootPath/Music")
                                }
                            }
                            item {
                                ShortcutRow(Icons.Outlined.Description, "เอกสาร") {
                                    viewModel.navigateTo("$rootPath/Documents")
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }

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
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(ZonColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(40.dp))
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
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = ZonColors.Accent)
        ) {
            Text("อนุญาตการเข้าถึงไฟล์ทั้งหมด")
        }
    }
}

@Composable
private fun EmptyFolder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(ZonColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty), color = ZonColors.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.empty_desc), color = ZonColors.TextTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun StorageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    usedBytes: Long?,
    totalBytes: Long?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZonColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZonColors.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = ZonColors.Accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ZonColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = ZonColors.TextTertiary, fontSize = 12.sp)
            }
        }
        if (totalBytes != null && totalBytes > 0 && usedBytes != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ZonColors.DeepBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(ZonColors.Accent)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                color = ZonColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ShortcutRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ZonColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ZonColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = ZonColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
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
