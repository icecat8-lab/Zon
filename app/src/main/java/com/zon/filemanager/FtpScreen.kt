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

// app/src/main/java/com/zon/filemanager/FtpScreen.kt
package com.zon.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (state.ftpState.connected) {
            viewModel.disconnectFtp()
        } else {
            viewModel.setScreen(Screen.SETTINGS)
        }
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
                IconButton(onClick = {
                    if (state.ftpState.connected) viewModel.disconnectFtp()
                    else viewModel.setScreen(Screen.SETTINGS)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.ftpState.connected) "FTP: ${state.ftpState.host}" else "FTP Client",
                        color = ZonColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.ftpState.connected) {
                        Text(
                            state.ftpState.currentPath,
                            color = ZonColors.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!state.ftpState.connected) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, null, tint = ZonColors.Accent)
                    }
                } else {
                    IconButton(onClick = { viewModel.ftpNavigateUp() }) {
                        Icon(Icons.Outlined.ArrowUpward, null, tint = ZonColors.TextPrimary)
                    }
                }
            }
        }

        when {
            state.ftpState.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp)
                }
            }
            state.ftpState.connected -> {
                if (state.ftpState.files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("โฟลเดอร์ว่างเปล่า", color = ZonColors.TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.ftpState.files, key = { it.path }) { file ->
                            FtpFileRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) viewModel.ftpNavigate(file)
                                    else viewModel.ftpDownload(file)
                                },
                                onDelete = { viewModel.ftpDelete(file) }
                            )
                        }
                    }
                }
            }
            else -> {
                if (state.ftpServers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Dns, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(44.dp))
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("ยังไม่มี FTP Server", color = ZonColors.TextSecondary, fontSize = 15.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("กดปุ่ม + เพื่อเพิ่มเซิร์ฟเวอร์", color = ZonColors.TextTertiary, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.ftpServers) { server ->
                            FtpServerRow(
                                server = server,
                                onConnect = { viewModel.connectFtp(server) },
                                onDelete = { viewModel.removeFtpServer(server.host, server.port) }
                            )
                        }
                    }
                }
            }
        }

        state.ftpState.error?.let { err ->
            Surface(
                color = ZonColors.Danger.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    err,
                    color = ZonColors.Danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    if (showAddDialog) {
        FtpAddDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { server ->
                viewModel.saveFtpServer(server)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FtpServerRow(server: FtpServer, onConnect: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onConnect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(ZonColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Dns, null, tint = ZonColors.Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                server.name.ifBlank { server.host },
                color = ZonColors.TextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${server.host}:${server.port}",
                color = ZonColors.TextTertiary, fontSize = 11.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, null, tint = ZonColors.Danger, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FtpFileRow(file: FileItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(file.getIconColor().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = ZonColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                if (file.isDirectory) "โฟลเดอร์" else file.getReadableSize(),
                color = ZonColors.TextSecondary, fontSize = 12.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
        if (file.isDirectory) {
            Icon(Icons.Filled.ChevronRight, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FtpAddDialog(onDismiss: () -> Unit, onConfirm: (FtpServer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("anonymous") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "เพิ่ม FTP Server",
                color = ZonColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column {
                FtpField("ชื่อ (optional)", name, { name = it })
                Spacer(Modifier.height(8.dp))
                FtpField("Host / IP", host, { host = it })
                Spacer(Modifier.height(8.dp))
                FtpField("Port", port, { port = it.filter { c -> c.isDigit() } }, keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(8.dp))
                FtpField("Username", user, { user = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = ZonColors.TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null, tint = ZonColors.TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ZonColors.TextPrimary,
                        unfocusedTextColor = ZonColors.TextPrimary,
                        focusedBorderColor = ZonColors.Accent,
                        unfocusedBorderColor = ZonColors.LightGray,
                        focusedContainerColor = ZonColors.Surface,
                        unfocusedContainerColor = ZonColors.Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (host.isNotBlank()) {
                        onConfirm(FtpServer(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 21,
                            user = user.ifBlank { "anonymous" },
                            password = password,
                            name = name.ifBlank { host.trim() }
                        ))
                    }
                }
            ) { Text("บันทึก", color = ZonColors.Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = ZonColors.TextSecondary)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun FtpField(
    label: String, value: String, onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = ZonColors.TextTertiary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ZonColors.TextPrimary,
            unfocusedTextColor = ZonColors.TextPrimary,
            focusedBorderColor = ZonColors.Accent,
            unfocusedBorderColor = ZonColors.LightGray,
            focusedContainerColor = ZonColors.Surface,
            unfocusedContainerColor = ZonColors.Surface
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
