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

// app/src/main/java/com/zon/filemanager/DuplicateFinderScreen.kt
package com.zon.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DuplicateFinderScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var scanPath by remember { mutableStateOf(INTERNAL_STORAGE_ROOT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.menu_duplicate_finder),
            onBack = { viewModel.setScreen(Screen.SETTINGS) }
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = ZonColors.Surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, null, tint = ZonColors.IconFolder, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    scanPath, color = ZonColors.TextPrimary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.scanDuplicates(scanPath) },
                    enabled = !state.duplicateScanning
                ) {
                    Icon(Icons.Outlined.Search, null, tint = ZonColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("สแกน", color = ZonColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when {
            state.duplicateScanning -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.dup_scanning), color = ZonColors.TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            state.duplicateGroups.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.dup_scan_hint), color = ZonColors.TextSecondary, fontSize = 15.sp)
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = ZonColors.SurfaceElevated,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = ZonColors.Warning, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.dup_found, state.duplicateGroups.size),
                                    color = ZonColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "รวม ${state.selectedDuplicates.size} ไฟล์ที่เลือก",
                                    color = ZonColors.TextSecondary, fontSize = 12.sp
                                )
                            }
                            if (state.selectedDuplicates.isNotEmpty()) {
                                TextButton(onClick = { showDeleteConfirm = true }) {
                                    Text(
                                        stringResource(R.string.dup_delete_selected),
                                        color = ZonColors.Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.duplicateGroups) { group ->
                            DuplicateGroupCard(
                                group = group,
                                selected = state.selectedDuplicates,
                                onToggle = { viewModel.toggleDuplicateSelection(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    "ลบ ${state.selectedDuplicates.size} ไฟล์?",
                    color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "การกระทำนี้ไม่สามารถย้อนกลับได้",
                    color = ZonColors.TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedDuplicates()
                    showDeleteConfirm = false
                }) { Text("ลบ", color = ZonColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ยกเลิก", color = ZonColors.TextSecondary)
                }
            },
            containerColor = ZonColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ContentCopy, null, tint = ZonColors.Warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${group.files.size} ไฟล์ซ้ำ",
                    color = ZonColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatSize(group.totalSize),
                    color = ZonColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            group.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(file.path) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (selected.contains(file.path)) ZonColors.Accent else Color.Transparent)
                            .then(
                                if (!selected.contains(file.path))
                                    Modifier.background(ZonColors.MidGray, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected.contains(file.path)) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, color = ZonColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            file.path.substringBeforeLast('/'),
                            color = ZonColors.TextTertiary, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
