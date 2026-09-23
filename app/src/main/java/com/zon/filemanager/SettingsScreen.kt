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

// app/src/main/java/com/zon/filemanager/SettingsScreen.kt
package com.zon.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.settings_title),
            onBack = { viewModel.setScreen(Screen.FILES) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_root)) {
                SettingsItem(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.settings_root),
                    subtitle = if (state.rootAvailable) stringResource(R.string.settings_root_granted)
                               else stringResource(R.string.settings_root_denied),
                    tint = if (state.rootAvailable) ZonColors.Success else ZonColors.TextSecondary,
                    trailing = {
                        Switch(
                            checked = state.rootEnabled && state.rootAvailable,
                            onCheckedChange = { viewModel.toggleRoot(it) },
                            enabled = state.rootAvailable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ZonColors.White,
                                checkedTrackColor = ZonColors.Accent,
                                uncheckedThumbColor = ZonColors.TextSecondary,
                                uncheckedTrackColor = ZonColors.MidGray
                            )
                        )
                    },
                    onClick = { if (state.rootAvailable) viewModel.toggleRoot(!state.rootEnabled) }
                )
                SettingsItem(
                    icon = Icons.Outlined.Refresh,
                    title = stringResource(R.string.settings_root_check),
                    subtitle = "ตรวจสอบสถานะ Root อีกครั้ง",
                    tint = ZonColors.Accent,
                    onClick = { viewModel.recheckRoot() }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("เครื่องมือ") {
                SettingsItem(
                    icon = Icons.Outlined.History,
                    title = "เปิดล่าสุด",
                    subtitle = "${state.recent.size} รายการ",
                    tint = ZonColors.IconCode,
                    onClick = { viewModel.openRecent() }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection(stringResource(R.string.settings_about)) {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = "1.0 (build 2026.09.20)",
                    tint = ZonColors.TextSecondary,
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Outlined.Gavel,
                    title = stringResource(R.string.settings_license),
                    subtitle = "Apache 2.0",
                    tint = ZonColors.Warning,
                    onClick = { viewModel.setScreen(Screen.ABOUT) }
                )
                SettingsItem(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.settings_about),
                    subtitle = "ข้อมูลนักพัฒนาและลิขสิทธิ์",
                    tint = ZonColors.Accent,
                    onClick = { viewModel.setScreen(Screen.ABOUT) }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            color = ZonColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ZonColors.Surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ZonColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = ZonColors.TextSecondary, fontSize = 12.sp, maxLines = 2)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Surface(color = ZonColors.DeepBlack) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
            }
            Text(
                title,
                color = ZonColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
