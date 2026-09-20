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

// app/src/main/java/com/zon/filemanager/AppManagerScreen.kt
package com.zon.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var contextApp by remember { mutableStateOf<AppInfo?>(null) }
    var backupResult by remember { mutableStateOf<String?>(null) }

    val filteredApps = remember(state.installedApps, selectedTab, searchQuery) {
        state.installedApps
            .filter { if (selectedTab == 0) !it.isSystem else it.isSystem }
            .filter { searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.DeepBlack)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.menu_app_manager),
            onBack = { viewModel.setScreen(Screen.SETTINGS) }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint), color = ZonColors.TextTertiary) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = ZonColors.TextSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ZonColors.TextPrimary,
                unfocusedTextColor = ZonColors.TextPrimary,
                focusedBorderColor = ZonColors.Accent,
                unfocusedBorderColor = ZonColors.Separator,
                focusedContainerColor = ZonColors.Surface,
                unfocusedContainerColor = ZonColors.Surface
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = ZonColors.Accent,
            divider = { }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "${stringResource(R.string.app_user)} (${state.installedApps.count { !it.isSystem }})",
                        color = if (selectedTab == 0) ZonColors.Accent else ZonColors.TextSecondary,
                        fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "${stringResource(R.string.app_system)} (${state.installedApps.count { it.isSystem }})",
                        color = if (selectedTab == 1) ZonColors.Accent else ZonColors.TextSecondary,
                        fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }

        when {
            state.appsLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(app = app, onClick = { contextApp = app })
                }
            }
        }
    }

    contextApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { contextApp = null },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app, size = 52)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = ZonColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(app.packageName, color = ZonColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("เวอร์ชัน ${app.versionName}", color = ZonColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                AppActionGroup {
                    AppAction(Icons.Outlined.OpenInNew, stringResource(R.string.app_open), ZonColors.TextPrimary) {
                        contextApp = null
                        viewModel.openApp(app.packageName)
                    }
                    AppAction(Icons.Outlined.Info, stringResource(R.string.app_info), ZonColors.TextPrimary) {
                        contextApp = null
                        viewModel.openAppInfo(app.packageName)
                    }
                    AppAction(Icons.Outlined.Save, stringResource(R.string.app_backup), ZonColors.Accent) {
                        contextApp = null
                        viewModel.backupApp(app)
                        backupResult = "บันทึก APK แล้ว: ${app.appName}_${app.versionName}.apk"
                    }
                    AppAction(Icons.Outlined.Delete, stringResource(R.string.app_uninstall), ZonColors.Danger) {
                        contextApp = null
                        viewModel.uninstallApp(app.packageName)
                    }
                }
            }
        }
    }

    backupResult?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            backupResult = null
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 32.dp),
                color = ZonColors.SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    msg,
                    color = ZonColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app, size = 42)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, color = ZonColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${app.versionName} · ${formatSize(app.size)}", color = ZonColors.TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
        if (app.isSystem) {
            Surface(color = ZonColors.Warning.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                Text("ระบบ", color = ZonColors.Warning, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, size: Int) {
    val bitmap = remember(app.packageName) {
        try { app.icon?.toBitmap(size * 3, size * 3)?.asImageBitmap() } catch (e: Exception) { null }
    }
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap, contentDescription = null,
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp))
            )
        } else {
            Box(
                modifier = Modifier.size(size.dp).background(ZonColors.Surface, RoundedCornerShape((size / 4).dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Android, null, tint = ZonColors.IconApk, modifier = Modifier.size((size / 2).dp))
            }
        }
    }
}

@Composable
fun AppActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(16.dp)
    ) { Column(content = content) }
}

@Composable
fun AppAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
