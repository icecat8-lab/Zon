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

// app/src/main/java/com/zon/filemanager/ArchivePreviewScreen.kt
package com.zon.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePreviewScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val file = state.archivePreviewItem

    if (file == null) {
        viewModel.setScreen(Screen.FILES)
        return
    }

    var contextEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var pendingEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }

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
                IconButton(onClick = { viewModel.setScreen(Screen.FILES) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color = ZonColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (state.archivePreviewLoading) "กำลังโหลด..."
                        else stringResource(R.string.preview_archive_entries, state.archiveEntries.size),
                        color = ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
                if (state.archiveEntries.isNotEmpty()) {
                    IconButton(onClick = { viewModel.extract(file) }) {
                        Icon(Icons.Outlined.Unarchive, null, tint = ZonColors.Accent)
                    }
                }
            }
        }

        when {
            state.archivePreviewLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                }
            }
            state.archiveEntries.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.FolderZip, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("ไม่สามารถอ่านไฟล์ได้", color = ZonColors.TextSecondary, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("อาจต้องใช้รหัสผ่านหรือไฟล์เสียหาย", color = ZonColors.TextTertiary, fontSize = 12.sp)
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.archiveEntries, key = { "${it.index}_${it.name}" }) { entry ->
                    ArchiveEntryRow(
                        entry = entry,
                        onClick = {
                            // แตะ entry → ถ้า previewable → เปิด preview; ถ้าไม่ → เปิด context menu
                            val ext = entry.name.substringAfterLast('.', "").lowercase()
                            val previewable = ext in listOf(
                                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg",
                                "txt", "md", "log", "json", "xml", "html", "css", "js",
                                "kt", "java", "py", "cpp", "c", "h", "yml", "yaml", "ini", "conf", "sh", "csv"
                            )
                            if (!entry.isDirectory && previewable) {
                                viewModel.openArchiveEntry(entry)
                            } else {
                                contextEntry = entry
                            }
                        },
                        onLongClick = { contextEntry = entry }
                    )
                }
            }
        }
    }

    contextEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { contextEntry = null },
            containerColor = ZonColors.DeepBlack,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ZonColors.LightGray)
                )
            }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).background(ZonColors.IconArchive.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.InsertDriveFile, null, tint = ZonColors.IconArchive, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, color = ZonColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(entry.getReadableSize(), color = ZonColors.TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = ZonColors.Surface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val e = entry
                                    contextEntry = null
                                    pendingEntry = e
                                    showPasswordPrompt = true
                                }
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.FileDownload, null, tint = ZonColors.Accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Text(
                                stringResource(R.string.preview_extract_this),
                                color = ZonColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPasswordPrompt) {
        AlertDialog(
            onDismissRequest = {
                showPasswordPrompt = false
                pendingEntry = null
                passwordInput = ""
            },
            title = {
                Text(
                    "แตกไฟล์",
                    color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column {
                    Text(pendingEntry?.name ?: "", color = ZonColors.TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text("รหัสผ่าน (ถ้ามี)", color = ZonColors.TextTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ZonColors.TextPrimary, unfocusedTextColor = ZonColors.TextPrimary,
                            focusedBorderColor = ZonColors.Accent, unfocusedBorderColor = ZonColors.LightGray,
                            focusedContainerColor = ZonColors.Surface, unfocusedContainerColor = ZonColors.Surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingEntry?.let { viewModel.extractArchiveEntry(it, if (passwordInput.isBlank()) null else passwordInput) }
                    showPasswordPrompt = false
                    pendingEntry = null
                    passwordInput = ""
                }) { Text("แตกไฟล์", color = ZonColors.Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordPrompt = false
                    pendingEntry = null
                    passwordInput = ""
                }) { Text("ยกเลิก", color = ZonColors.TextSecondary) }
            },
            containerColor = ZonColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveEntryRow(
    entry: ArchiveEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
            null,
            tint = if (entry.isDirectory) ZonColors.IconFolder else ZonColors.IconFile,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = ZonColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDirectory) {
                Spacer(Modifier.height(2.dp))
                Text(entry.getReadableSize(), color = ZonColors.TextTertiary, fontSize = 11.sp)
            }
        }
    }
}
