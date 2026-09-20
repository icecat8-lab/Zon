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

// app/src/main/java/com/zon/filemanager/RecentScreen.kt
package com.zon.filemanager

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
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
fun RecentScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { viewModel.setScreen(Screen.FILES) }

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
                Text(
                    "เปิดล่าสุด",
                    color = ZonColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (state.recent.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Outlined.Delete, null, tint = ZonColors.Danger, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (state.recent.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.History, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("ยังไม่มีไฟล์ที่เปิดล่าสุด", color = ZonColors.TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.recent, key = { it.path }) { file ->
                    ListItemRow(
                        file = file,
                        onClick = { viewModel.openFromList(file) },
                        onRemove = { viewModel.removeRecent(file.path) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "ล้างประวัติ",
                    color = ZonColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    "ลบประวัติการเปิดไฟล์ทั้งหมด?",
                    color = ZonColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRecent()
                    showClearDialog = false
                }) { Text("ล้าง", color = ZonColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("ยกเลิก", color = ZonColors.TextSecondary)
                }
            },
            containerColor = ZonColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
