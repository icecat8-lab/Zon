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

// app/src/main/java/com/zon/filemanager/MainActivity.kt
package com.zon.filemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxHz = window.windowManager.defaultDisplay.supportedModes.maxOfOrNull { it.refreshRate } ?: 0f
            if (maxHz > 0f) {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = maxHz
                }
            }
        }
        setContent {
            ZonTheme {
                PermissionGate {
                    val viewModel: FileManagerViewModel = viewModel()
                    ZonApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun ZonApp(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val storageManager = remember {
        context.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
    }

    val usbAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
            viewModel.openUsbVolume(uri)
        }
    }

    // ---- Centralized BackHandler: กลับทีละขั้นตาม state.screen ----
    BackHandler(enabled = true) {
        when (state.screen) {
            Screen.FILES -> {
                // อยู่ในหน้าหลัก → navigateUp (ถ้าอยู่ root จริงๆ จะไม่ทำอะไร)
                viewModel.navigateUp()
            }
            Screen.USB_OTG -> viewModel.closeUsbMode()
            Screen.FTP -> {
                if (state.ftpState.connected) viewModel.disconnectFtp()
                else viewModel.setScreen(Screen.SETTINGS)
            }
            Screen.LICENSES -> viewModel.setScreen(Screen.ABOUT)
            Screen.ABOUT -> viewModel.setScreen(Screen.SETTINGS)
            Screen.SETTINGS -> viewModel.setScreen(Screen.FILES)
            Screen.PREVIEW,
            Screen.TEXT_EDITOR,
            Screen.ARCHIVE_PREVIEW,
            Screen.STORAGE_ANALYZER,
            Screen.DUP_FINDER,
            Screen.APP_MANAGER,
            Screen.FAVORITES,
            Screen.RECENT -> viewModel.setScreen(Screen.FILES)
        }
    }

    when (state.screen) {
        Screen.FILES -> FileManagerScreen(viewModel, usbAccessLauncher, storageManager)
        Screen.SETTINGS -> SettingsScreen(viewModel)
        Screen.PREVIEW -> PreviewScreen(viewModel)
        Screen.TEXT_EDITOR -> TextEditorScreen(viewModel)
        Screen.STORAGE_ANALYZER -> StorageAnalyzerScreen(viewModel)
        Screen.DUP_FINDER -> DuplicateFinderScreen(viewModel)
        Screen.APP_MANAGER -> AppManagerScreen(viewModel)
        Screen.ARCHIVE_PREVIEW -> ArchivePreviewScreen(viewModel)
        Screen.USB_OTG -> UsbOtgScreen(viewModel)
        Screen.FAVORITES -> FavoritesScreen(viewModel)
        Screen.RECENT -> RecentScreen(viewModel)
        Screen.FTP -> FtpScreen(viewModel)
        Screen.ABOUT -> AboutScreen(viewModel)
        Screen.LICENSES -> LicensesScreen(viewModel)
    }
}

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = (context as? ComponentActivity)?.lifecycle
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasAllFilesAccess(context)) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionHelper.hasAllFilesAccess(context)
        } else {
            result.values.all { it } || PermissionHelper.hasAllFilesAccess(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = PermissionHelper.hasAllFilesAccess(context)
            }
        }
        lifecycleOwner?.addObserver(observer)
        onDispose { lifecycleOwner?.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (requestedOnce) return@LaunchedEffect
        requestedOnce = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!PermissionHelper.hasAllFilesAccess(context)) PermissionHelper.openAllFilesAccessSettings(context)
        } else {
            val perms = buildList {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (perms.isNotEmpty()) legacyPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    if (hasPermission) content()
    else PermissionScreen(onRequest = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PermissionHelper.openAllFilesAccessSettings(context)
        else legacyPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    })
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else 0.85f, tween(600), label = "s")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "a")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZonColors.DeepBlack, ZonColors.Black))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).scale(scale).alpha(alpha)
        ) {
            Box(
                modifier = Modifier.size(88.dp)
                    .background(ZonColors.Accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.FolderOpen, null, tint = ZonColors.Accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.perm_title),
                color = ZonColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.perm_desc),
                color = ZonColors.TextSecondary, fontSize = 14.sp,
                textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZonColors.Accent,
                    contentColor = ZonColors.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.perm_btn), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}
