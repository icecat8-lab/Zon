/*
 * Copyright (C) 2026 Zon File Manager
 */

package com.zon.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LicensesScreen(viewModel: FileManagerViewModel) {
    BackHandler { viewModel.setScreen(Screen.ABOUT) }

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
                Text(
                    "Open Source Licenses",
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
                .padding(16.dp)
        ) {
            LicenseCard("Jetpack Compose", "Apache License 2.0")
            Spacer(Modifier.height(12.dp))
            LicenseCard("Kotlin Coroutines", "Apache License 2.0")
            Spacer(Modifier.height(12.dp))
            LicenseCard("Material Design 3", "Apache License 2.0")
        }
    }
}

@Composable
fun LicenseCard(name: String, license: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, color = ZonColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(license, color = ZonColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
