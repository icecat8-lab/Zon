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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PreviewScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val file = state.previewItem
    val context = LocalContext.current

    if (file == null) {
        viewModel.setScreen(Screen.FILES)
        return
    }

    val f = remember(file.path) { File(file.path) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZonColors.Black)
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
                        file.getReadableSize(),
                        color = ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = { FileOpener.shareFile(context, f) }) {
                    Icon(Icons.Outlined.Share, null, tint = ZonColors.TextPrimary)
                }
                IconButton(onClick = { FileOpener.openWith(context, f) }) {
                    Icon(Icons.Outlined.OpenInNew, null, tint = ZonColors.TextPrimary)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                file.isImage() -> ZoomableImage(f)
                file.isText() -> TextPreview(f)
                else -> UnsupportedPreview()
            }
        }
    }
}

@Composable
fun ZoomableImage(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
fun TextPreview(file: File) {
    val content = remember(file.path) {
        try {
            val text = file.readText()
            if (text.length > 200_000) text.substring(0, 200_000) + "\n\n... (ไฟล์ยาวเกินไป)"
            else text
        } catch (e: Exception) {
            "ไม่สามารถอ่านไฟล์ได้: ${e.message}"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                content,
                color = ZonColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun UnsupportedPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(ZonColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.InsertDriveFile,
                null,
                tint = ZonColors.TextTertiary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("ไม่รองรับการแสดงตัวอย่าง", color = ZonColors.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text("กรุณาเปิดด้วยแอปอื่น", color = ZonColors.TextTertiary, fontSize = 12.sp)
    }
}
