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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ZonColors {
    val Black = Color(0xFF000000)
    val DeepBlack = Color(0xFF0A0A0C)
    val DarkSurface = Color(0xFF131316)
    val Surface = Color(0xFF1A1A1E)
    val SurfaceElevated = Color(0xFF232328)
    val MidGray = Color(0xFF2A2A30)
    val LightGray = Color(0xFF3A3A42)
    val Separator = Color(0xFF1F1F24)

    val White = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9A9AA3)
    val TextTertiary = Color(0xFF6A6A73)
    val Gray = Color(0xFF8E8E96)

    val Accent = Color(0xFF0A84FF)
    val AccentSoft = Color(0xFF0A84FF).copy(alpha = 0.15f)

    val IconFolder = Color(0xFF0A84FF)
    val IconImage = Color(0xFFFF9F0A)
    val IconVideo = Color(0xFFFF453A)
    val IconAudio = Color(0xFFBF5AF2)
    val IconDoc = Color(0xFF30D158)
    val IconPdf = Color(0xFFFF453A)
    val IconApk = Color(0xFF32D74B)
    val IconArchive = Color(0xFFFFB340)
    val IconCode = Color(0xFF64D2FF)
    val IconFile = Color(0xFF8E8E96)

    val Danger = Color(0xFFFF453A)
    val Success = Color(0xFF30D158)
    val Warning = Color(0xFFFF9F0A)
    val Selected = Color(0xFF0A84FF).copy(alpha = 0.18f)
    val SelectedBorder = Color(0xFF0A84FF)
}

private val DarkColorScheme = darkColorScheme(
    primary = ZonColors.Accent,
    background = ZonColors.DeepBlack,
    surface = ZonColors.Surface,
    onPrimary = ZonColors.White,
    onBackground = ZonColors.TextPrimary,
    onSurface = ZonColors.TextPrimary
)

@Composable
fun ZonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
