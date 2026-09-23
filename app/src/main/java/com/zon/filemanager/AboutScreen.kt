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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = true) { viewModel.setScreen(Screen.SETTINGS) }

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
                IconButton(onClick = { viewModel.setScreen(Screen.SETTINGS) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZonColors.TextPrimary)
                }
                Text(
                    "เกี่ยวกับ",
                    color = ZonColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ZonColors.Accent, ZonColors.Accent.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Z",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Zon File Manager",
                color = ZonColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "เวอร์ชัน 1.0",
                color = ZonColors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "build 2026.09.20",
                color = ZonColors.TextTertiary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ZonColors.Surface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "แอพจัดการไฟล์ที่ทันสมัย รวดเร็ว ปลอดภัย",
                    color = ZonColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            AboutSection("ข้อมูลนักพัฒนา") {
                AboutItem(
                    icon = Icons.Outlined.Person,
                    title = "นักพัฒนา",
                    value = "Icecat",
                    tint = ZonColors.Accent
                )
                AboutItem(
                    icon = Icons.Outlined.Email,
                    title = "อีเมล",
                    value = "contact@zonfile.app",
                    tint = ZonColors.IconImage
                )
            }

            Spacer(Modifier.height(16.dp))

            AboutSection("ลิขสิทธิ์") {
                AboutItem(
                    icon = Icons.Outlined.Gavel,
                    title = "License",
                    value = "Apache 2.0",
                    tint = ZonColors.Warning
                )
            }

            Spacer(Modifier.height(16.dp))

            AboutSection("การสนับสนุน") {
                AboutItem(
                    icon = Icons.Outlined.Star,
                    title = "ให้ดาวแอพ",
                    value = "บน Google Play",
                    tint = ZonColors.Warning
                )
                AboutItem(
                    icon = Icons.Outlined.BugReport,
                    title = "รายงานบั๊ก",
                    value = "github.com/icecat8-lab/Zon",
                    tint = ZonColors.Danger
                )
                AboutItem(
                    icon = Icons.Outlined.Share,
                    title = "แชร์ให้เพื่อน",
                    value = "แชร์แอพนี้",
                    tint = ZonColors.IconDoc
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Made with ❤️ by Zon Project",
                color = ZonColors.TextTertiary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "© 2026 Zon File Manager",
                color = ZonColors.TextTertiary,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
fun AboutItem(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
            Spacer(Modifier.height(2.dp))
            Text(value, color = ZonColors.TextSecondary, fontSize = 12.sp)
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = ZonColors.TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
