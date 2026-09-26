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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val atRoot = state.currentPath == rootPath

    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

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

    var viewingArchive by remember { mutableStateOf<File?>(null) }

    if (viewingArchive != null) {
        ArchivePreviewScreen(file = viewingArchive!!, onBack = { viewingArchive = null })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            val viewState = when {
                !hasPermission -> "permission"
                isLoading -> "loading"
                fileList.isEmpty() && !atRoot -> "empty"
                else -> "list"
            }
            Crossfade(targetState = viewState, label = "fileManagerContent") { vs ->
                when (vs) {
                    "permission" -> {
                        PermissionGate(onGrant = { requestAllFilesAccess() })
                    }
                    "loading" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                color = ZonColors.Accent,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    "empty" -> {
                        EmptyFolder()
                    }
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refreshCurrentFolder() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (atRoot) {
                                    if (state.usbAvailable) {
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
                                        item { Spacer(Modifier.height(8.dp)) }
                                    }
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
                                        onClick = { viewModel.openFile(file) },
                                        onLongClick = { viewModel.showContextMenu(file) },
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
    }

        state.archiveMenuTarget?.let { target ->
            ArchiveMenuSheet(
                fileName = target.name,
                onDismiss = { viewModel.dismissArchiveMenu() },
                onView = {
                    viewModel.dismissArchiveMenu()
                    viewingArchive = File(target.path)
                },
                onExtractHere = { viewModel.extractHere(target) },
                onExtractToSubfolder = { viewModel.extractToSubfolder(target) }
            )
        }

        state.contextMenuTarget?.let { target ->
            ContextMenuSheet(
                fileName = target.name,
                onDismiss = { viewModel.dismissContextMenu() },
                onCompress = { viewModel.compressItem(target) },
                onInfo = { viewModel.showInfo(target) },
                onCopy = { viewModel.startCopy(target) },
                onMove = { viewModel.startMove(target) },
                onDelete = { viewModel.requestDelete(target) },
                onRename = { viewModel.requestRename(target) },
                onShare = { viewModel.shareItem(target) }
            )
        }

        state.renameTarget?.let { target ->
            RenameDialog(
                currentName = target.name,
                onDismiss = { viewModel.dismissRename() },
                onConfirm = { newName -> viewModel.confirmRename(newName) }
            )
        }

        state.deleteConfirmTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteConfirm() },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDelete() }) {
                        Text("ลบ", color = ZonColors.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDeleteConfirm() }) { Text("ยกเลิก") }
                },
                title = { Text("ลบ \"${target.name}\"?") },
                text = { Text("ไม่สามารถกู้คืนได้หลังจากลบ") }
            )
        }

        state.infoTarget?.let { target ->
            InfoDialog(file = target, onDismiss = { viewModel.dismissInfo() })
        }

        state.clipboard?.let { clip ->
            PasteBar(
                count = clip.items.size,
                isMove = clip.mode == ClipboardMode.MOVE,
                onPaste = { viewModel.pasteClipboard() },
                onCancel = { viewModel.cancelClipboard() }
            )
        }

        if (state.archiveOpRunning) {
            ArchiveProgressDialog(
                label = state.archiveOpLabel,
                fileName = state.archiveOpFile,
                percent = state.archiveOpPercent,
                onCancel = { viewModel.cancelArchiveOperation() }
            )
        }

        state.archiveOpError?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissArchiveError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissArchiveError() }) { Text("ตกลง") }
                },
                title = { Text("ทำรายการไม่สำเร็จ") },
                text = { Text(message) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveMenuSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToSubfolder: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ZonColors.Surface) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                fileName,
                color = ZonColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = ZonColors.DeepBlack)
            ArchiveMenuRow(Icons.Outlined.Visibility, "ดู") { onDismiss(); onView() }
            ArchiveMenuRow(Icons.Outlined.FileUpload, "แยกไฟล์ไว้ที่นี่") { onExtractHere() }
            ArchiveMenuRow(Icons.Outlined.CreateNewFolder, "แยกไฟล์ไว้ที่ ./${fileName.substringBeforeLast('.')}/") {
                onExtractToSubfolder()
            }
        }
    }
}

@Composable
private fun ArchiveMenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ZonColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, color = ZonColors.TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun ArchiveProgressDialog(label: String, fileName: String, percent: Int, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ZonColors.Surface)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$percent%",
                color = ZonColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ZonColors.DeepBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(ZonColors.Accent)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(label, color = ZonColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (fileName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    fileName,
                    color = ZonColors.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("ยกเลิก") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenuSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onCompress: () -> Unit,
    onInfo: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ZonColors.Surface) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                fileName,
                color = ZonColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = ZonColors.DeepBlack)
            ArchiveMenuRow(Icons.Outlined.FolderZip, "บีบอัดไฟล์...", onCompress)
            ArchiveMenuRow(Icons.Outlined.Info, "เกี่ยวกับ", onInfo)
            ArchiveMenuRow(Icons.Outlined.ContentCopy, "คัดลอก", onCopy)
            ArchiveMenuRow(Icons.Outlined.DriveFileMove, "ย้าย", onMove)
            ArchiveMenuRow(Icons.Outlined.Delete, "ลบ", onDelete)
            ArchiveMenuRow(Icons.Outlined.Edit, "เปลี่ยนชื่อ", onRename)
            ArchiveMenuRow(Icons.Outlined.Share, "แชร์", onShare)
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("ตกลง") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก") }
        },
        title = { Text("เปลี่ยนชื่อ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun InfoDialog(file: FileItem, onDismiss: () -> Unit) {
    val dateText = remember(file.lastModified) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.lastModified))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด") }
        },
        title = { Text(file.name) },
        text = {
            Column {
                InfoRow("ประเภท", if (file.isDirectory) "โฟลเดอร์" else "ไฟล์")
                if (!file.isDirectory) InfoRow("ขนาด", file.getReadableSize())
                InfoRow("แก้ไขล่าสุด", dateText)
                InfoRow("ที่อยู่", file.path)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = ZonColors.TextTertiary, fontSize = 11.sp)
        Text(value, color = ZonColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun BoxScope.PasteBar(count: Int, isMove: Boolean, onPaste: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZonColors.SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isMove) "ย้าย $count รายการ" else "คัดลอก $count รายการ",
            color = ZonColors.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onCancel) { Text("ยกเลิก") }
        Button(onClick = onPaste, colors = ButtonDefaults.buttonColors(containerColor = ZonColors.Accent)) {
            Text("วาง")
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
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "rowScale"
    )
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
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
        if (file.isDirectory) {
            Icon(Icons.Filled.ChevronRight, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
