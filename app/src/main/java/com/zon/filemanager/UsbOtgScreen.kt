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

// app/src/main/java/com/zon/filemanager/UsbOtgScreen.kt
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
fun UsbOtgScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = true) {
        viewModel.closeUsbMode()
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
                IconButton(onClick = { viewModel.closeUsbMode() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "USB OTG",
                        color = ZonColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${state.usbFiles.size} รายการ",
                        color = ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = { viewModel.closeUsbMode() }) {
                    Icon(Icons.Outlined.Close, null, tint = ZonColors.TextPrimary)
                }
            }
        }

        when {
            state.usbLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                }
            }
            state.usbFiles.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Usb, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("ไม่พบไฟล์ใน USB", color = ZonColors.TextSecondary, fontSize = 15.sp)
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.usbFiles, key = { it.path }) { file ->
                    UsbFileRow(
                        file = file,
                        onClick = {
                            if (file.isDirectory) {
                                val uri = android.net.Uri.parse(file.path)
                                viewModel.navigateUsbDirectory(uri)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UsbFileRow(file: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
                color = ZonColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        if (file.isDirectory) {
            Icon(Icons.Filled.ChevronRight, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
