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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun StorageAnalyzerScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val info = state.storageInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.menu_storage_analyzer),
            onBack = { viewModel.setScreen(Screen.SETTINGS) }
        )

        when {
            state.storageScanning || info == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.storage_scanning),
                            color = ZonColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StorageOverviewCard(info)
                }

                if (info.categories.isNotEmpty()) {
                    item {
                        Text(
                            "แยกตามประเภท",
                            color = ZonColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    items(info.categories) { cat ->
                        CategoryRow(cat, info.used)
                    }
                }

                if (info.largestFolders.isNotEmpty()) {
                    item {
                        Text(
                            "โฟลเดอร์ใหญ่สุด",
                            color = ZonColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(info.largestFolders.take(10)) { (name, size) ->
                        FolderSizeRow(name, size, info.used)
                    }
                }

                if (info.largestFiles.isNotEmpty()) {
                    item {
                        Text(
                            "ไฟล์ใหญ่สุด",
                            color = ZonColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(info.largestFiles.take(10)) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ZonColors.Surface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(file.getIconColor().copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, color = ZonColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(file.path.substringBeforeLast('/'), color = ZonColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(file.getReadableSize(), color = ZonColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageOverviewCard(info: StorageInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 14f
                    val usedAngle = (info.used.toFloat() / info.total * 360f)
                    drawArc(
                        color = ZonColors.MidGray,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = ZonColors.Accent,
                        startAngle = -90f,
                        sweepAngle = usedAngle,
                        useCenter = false,
                        style = Stroke(width = stroke)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${((info.used.toFloat() / info.total) * 100).roundToInt()}%",
                        color = ZonColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("ใช้งาน", color = ZonColors.TextTertiary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.width(20.dp))

            Column {
                StorageStatRow(
                    label = stringResource(R.string.storage_total),
                    value = formatSize(info.total),
                    color = ZonColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                StorageStatRow(
                    label = stringResource(R.string.storage_used),
                    value = formatSize(info.used),
                    color = ZonColors.Accent
                )
                Spacer(Modifier.height(8.dp))
                StorageStatRow(
                    label = stringResource(R.string.storage_free),
                    value = formatSize(info.free),
                    color = ZonColors.Success
                )
            }
        }
    }
}

@Composable
fun StorageStatRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = ZonColors.TextTertiary, fontSize = 11.sp)
            Text(value, color = ZonColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CategoryRow(category: CategoryUsage, total: Long) {
    val pct = if (total > 0) category.size.toFloat() / total else 0f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(category.color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (category.name) {
                            "วิดีโอ" -> Icons.Outlined.Movie
                            "รูปภาพ" -> Icons.Outlined.Image
                            "เสียง" -> Icons.Outlined.MusicNote
                            "เอกสาร" -> Icons.Outlined.Description
                            "Archive" -> Icons.Outlined.FolderZip
                            "APK" -> Icons.Outlined.Android
                            else -> Icons.Outlined.InsertDriveFile
                        },
                        null,
                        tint = category.color,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, color = ZonColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${category.count} ไฟล์", color = ZonColors.TextTertiary, fontSize = 11.sp)
                }
                Text(
                    formatSize(category.size),
                    color = ZonColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZonColors.MidGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .clip(RoundedCornerShape(2.dp))
                        .background(category.color)
                )
            }
        }
    }
}

@Composable
fun FolderSizeRow(name: String, size: Long, total: Long) {
    val pct = if (total > 0) size.toFloat() / total else 0f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = ZonColors.IconFolder, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(name, color = ZonColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(formatSize(size), color = ZonColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZonColors.MidGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ZonColors.IconFolder)
                )
            }
        }
    }
}

fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1 -> String.format("%.2f TB", tb)
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.0f MB", mb)
        kb >= 1 -> String.format("%.0f KB", kb)
        else -> "$size B"
    }
}
