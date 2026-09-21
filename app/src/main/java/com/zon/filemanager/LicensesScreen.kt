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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LibraryLicense(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color
)

val libraries = listOf(
    LibraryLicense(
        "AndroidX Core",
        "1.12.0",
        "Apache 2.0",
        "https://developer.android.com/jetpack/androidx",
        "พื้นฐานของ Android",
        Icons.Outlined.Android,
        ZonColors.IconApk
    ),
    LibraryLicense(
        "Jetpack Compose",
        "2024.02.00",
        "Apache 2.0",
        "https://developer.android.com/jetpack/compose",
        "UI Framework ของ Google",
        Icons.Outlined.Code,
        ZonColors.Accent
    ),
    LibraryLicense(
        "Material 3",
        "1.2.0",
        "Apache 2.0",
        "https://m3.material.io",
        "Design System ของ Google",
        Icons.Outlined.Palette,
        ZonColors.IconAudio
    ),
    LibraryLicense(
        "Kotlin",
        "1.9.22",
        "Apache 2.0",
        "https://kotlinlang.org",
        "ภาษาหลักของโปรเจกต์",
        Icons.Outlined.Code,
        ZonColors.IconImage
    ),
    LibraryLicense(
        "Kotlin Coroutines",
        "1.7.3",
        "Apache 2.0",
        "https://github.com/Kotlin/kotlinx.coroutines",
        "Async Programming",
        Icons.Outlined.Refresh,
        ZonColors.IconDoc
    ),
    LibraryLicense(
        "Zip4j",
        "2.11.5",
        "Apache 2.0",
        "https://github.com/srikanth-lingala/zip4j",
        "ZIP Archive Library",
        Icons.Outlined.FolderZip,
        ZonColors.IconArchive
    ),
    LibraryLicense(
        "junrar",
        "7.5.5",
        "UnRAR License",
        "https://github.com/junrar/junrar",
        "RAR Archive Library",
        Icons.Outlined.FolderZip,
        ZonColors.IconArchive
    ),
    LibraryLicense(
        "Apache Commons Compress",
        "1.26.0",
        "Apache 2.0",
        "https://commons.apache.org/proper/commons-compress",
        "Archive/TAR/GZIP/BZIP2",
        Icons.Outlined.FolderZip,
        ZonColors.IconArchive
    ),
    LibraryLicense(
        "Apache Commons Net",
        "3.10.0",
        "Apache 2.0",
        "https://commons.apache.org/proper/commons-net",
        "FTP Client Library",
        Icons.Outlined.Dns,
        ZonColors.Accent
    ),
    LibraryLicense(
        "Apache Commons IO",
        "2.15.1",
        "Apache 2.0",
        "https://commons.apache.org/proper/commons-io",
        "I/O Utilities",
        Icons.Outlined.Description,
        ZonColors.IconDoc
    ),
    LibraryLicense(
        "LZ4 Java",
        "1.8.0",
        "Apache 2.0",
        "https://github.com/lz4/lz4-java",
        "LZ4 Compression",
        Icons.Outlined.Straighten,
        ZonColors.IconVideo
    ),
    LibraryLicense(
        "XZ for Java",
        "1.9",
        "Public Domain",
        "https://tukaani.org/xz/java.html",
        "XZ Compression",
        Icons.Outlined.Straighten,
        ZonColors.IconVideo
    ),
    LibraryLicense(
        "Zstd JNI",
        "1.5.5-11",
        "BSD 2-Clause",
        "https://github.com/luben/zstd-jni",
        "ZSTD Compression",
        Icons.Outlined.Straighten,
        ZonColors.IconVideo
    ),
    LibraryLicense(
        "Coil",
        "2.5.0",
        "Apache 2.0",
        "https://coil-kt.github.io/coil",
        "Image Loading",
        Icons.Outlined.Image,
        ZonColors.IconImage
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(viewModel: FileManagerViewModel) {
    BackHandler(enabled = true) { viewModel.setScreen(Screen.ABOUT) }

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
                IconButton(onClick = { viewModel.setScreen(Screen.ABOUT) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Open Source Licenses",
                        color = ZonColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${libraries.size} ไลบรารี",
                        color = ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ZonColors.Surface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Zon File Manager",
                            color = ZonColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Apache License 2.0",
                            color = ZonColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "© 2026 Zon Project",
                            color = ZonColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ไลบรารีที่ใช้",
                    color = ZonColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }

            items(libraries) { lib ->
                LibraryRow(lib)
            }

            item {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ZonColors.Surface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "ไลบรารีทั้งหมดที่แสดงเป็นผลงานของนักพัฒนาต้นทาง\n" +
                        "Zon ขอขอบคุณทุกท่านที่ทำให้โปรเจกต์นี้เป็นจริงได้",
                        color = ZonColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LibraryRow(lib: LibraryLicense) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(lib.tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(lib.icon, null, tint = lib.tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lib.name,
                    color = ZonColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "v${lib.version} · ${lib.license}",
                    color = ZonColors.TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    lib.description,
                    color = ZonColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
