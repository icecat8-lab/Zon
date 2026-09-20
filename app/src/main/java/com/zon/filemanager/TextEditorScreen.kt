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

// app/src/main/java/com/zon/filemanager/TextEditorScreen.kt
package com.zon.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun TextEditorScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val file = state.textEditorItem

    if (file == null) {
        viewModel.setScreen(Screen.FILES)
        return
    }

    val f = remember(file.path) { File(file.path) }
    var content by remember(file.path) {
        mutableStateOf(
            try {
                if (f.exists()) f.readText() else ""
            } catch (e: Exception) { "" }
        )
    }
    var originalContent by remember(file.path) { mutableStateOf(content) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val hasChanges = content != originalContent

    BackHandler(enabled = true) {
        if (hasChanges) showSaveDialog = true
        else viewModel.setScreen(Screen.FILES)
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
                    if (hasChanges) showSaveDialog = true
                    else viewModel.setScreen(Screen.FILES)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name, color = ZonColors.TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (hasChanges) "ยังไม่บันทึก" else "บันทึกแล้ว",
                        color = if (hasChanges) ZonColors.Warning else ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.saveTextFile(file, content)
                        originalContent = content
                    },
                    enabled = hasChanges
                ) {
                    Icon(
                        Icons.Outlined.Save, null,
                        tint = if (hasChanges) ZonColors.Accent else ZonColors.TextTertiary
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp),
            color = ZonColors.Surface,
            shape = RoundedCornerShape(14.dp)
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ZonColors.TextPrimary,
                    unfocusedTextColor = ZonColors.TextPrimary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = ZonColors.Accent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    "บันทึกการเปลี่ยนแปลง?",
                    color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "คุณมีการแก้ไขที่ยังไม่บันทึก ต้องการบันทึกหรือไม่?",
                    color = ZonColors.TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveTextFile(file, content)
                    showSaveDialog = false
                    viewModel.setScreen(Screen.FILES)
                }) { Text("บันทึก", color = ZonColors.Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog = false
                        viewModel.setScreen(Screen.FILES)
                    }) { Text("ไม่บันทึก", color = ZonColors.Danger) }
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("ยกเลิก", color = ZonColors.TextSecondary)
                    }
                }
            },
            containerColor = ZonColors.SurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
