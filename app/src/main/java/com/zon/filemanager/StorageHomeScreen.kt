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
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun StorageHomeScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var totalBytes by remember { mutableStateOf(0L) }
    var usedBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.refreshUsbAvailability()
        try {
            val stat = StatFs(rootPath)
            totalBytes = stat.totalBytes
            usedBytes = totalBytes - stat.availableBytes
        } catch (e: Exception) {
            // leave at 0, the usage bar just won't be shown
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.app_name),
                color = ZonColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            StorageCard(
                icon = Icons.Outlined.PhoneAndroid,
                title = stringResource(R.string.internal_storage),
                subtitle = rootPath,
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                onClick = { viewModel.navigateTo(rootPath) }
            )

            if (state.usbAvailable) {
                Spacer(Modifier.height(12.dp))
                StorageCard(
                    icon = Icons.Outlined.Usb,
                    title = "USB OTG",
                    subtitle = "แตะเพื่อเลือกไดรฟ์ USB",
                    usedBytes = null,
                    totalBytes = null,
                    onClick = { usbPickerLauncher.launch(null) }
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "ทางลัด",
                color = ZonColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            ShortcutRow(Icons.Outlined.Download, "ดาวน์โหลด") {
                viewModel.navigateTo("$rootPath/Download")
            }
            ShortcutRow(Icons.Outlined.MusicNote, "เพลง") {
                viewModel.navigateTo("$rootPath/Music")
            }
            ShortcutRow(Icons.Outlined.Description, "เอกสาร") {
                viewModel.navigateTo("$rootPath/Documents")
            }
            ShortcutRow(Icons.Outlined.Image, "รูปภาพ") {
                viewModel.navigateTo("$rootPath/Pictures")
            }
            ShortcutRow(Icons.Outlined.Movie, "วิดีโอ") {
                viewModel.navigateTo("$rootPath/Movies")
            }

            Spacer(Modifier.height(24.dp))
        }
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
            .padding(vertical = 10.dp),
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
