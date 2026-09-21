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

// app/src/main/java/com/zon/filemanager/FileManagerScreen.kt
package com.zon.filemanager

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    usbAccessLauncher: ActivityResultLauncher<Intent>,
    storageManager: android.os.storage.StorageManager
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("d MMM yy", Locale.getDefault()) }

    var contextMenuFile by remember { mutableStateOf<FileItem?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<List<String>>(emptyList()) }
    var infoTarget by remember { mutableStateOf<FileItem?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var compressTargets by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var extractTarget by remember { mutableStateOf<FileItem?>(null) }
    var archivePreviewTarget by remember { mutableStateOf<FileItem?>(null) }
    var splitExtractTarget by remember { mutableStateOf<FileItem?>(null) }

    BackHandler(enabled = true) {
        when {
            state.isArchiving -> { }
            infoTarget != null -> infoTarget = null
            deleteTarget.isNotEmpty() -> deleteTarget = emptyList()
            renameTarget != null -> renameTarget = null
            showNewFolderDialog -> showNewFolderDialog = false
            showNewFileDialog -> showNewFileDialog = false
            showSortSheet -> showSortSheet = false
            contextMenuFile != null -> contextMenuFile = null
            compressTargets.isNotEmpty() -> compressTargets = emptyList()
            extractTarget != null -> extractTarget = null
            archivePreviewTarget != null -> archivePreviewTarget = null
            splitExtractTarget != null -> splitExtractTarget = null
            fabExpanded -> fabExpanded = false
            state.isSelectionMode -> viewModel.clearSelection()
            showSearchBar -> { showSearchBar = false; viewModel.setSearchQuery("") }
            else -> viewModel.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            Column {
                AnimatedContent(
                    targetState = state.isSelectionMode,
                    transitionSpec = {
                        fadeIn(tween(200)) + slideInVertically { -it / 2 } togetherWith
                        fadeOut(tween(150)) + slideOutVertically { -it / 2 }
                    },
                    label = "topbar"
                ) { isSelection ->
                    if (isSelection) {
                        SelectionTopBar(
                            count = state.selectedFiles.size,
                            onClose = { viewModel.clearSelection() },
                            onSelectAll = { viewModel.selectAll() },
                            onDelete = { deleteTarget = state.selectedFiles.toList() },
                            onCopy = {
                                val files = state.files.filter { state.selectedFiles.contains(it.path) }
                                viewModel.copyToClipboard(files)
                            },
                            onCut = {
                                val files = state.files.filter { state.selectedFiles.contains(it.path) }
                                viewModel.cutToClipboard(files)
                            },
                            onShare = {
                                val files = state.files.filter { state.selectedFiles.contains(it.path) }
                                FileOpener.shareFiles(context, files.map { File(it.path) })
                            },
                            onCompress = {
                                compressTargets = state.files.filter { state.selectedFiles.contains(it.path) }
                            }
                        )
                    } else {
                        NormalTopBar(
                            state = state,
                            showSearch = showSearchBar,
                            onBack = { viewModel.navigateUp() },
                            onSearchToggle = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) viewModel.setSearchQuery("")
                            },
                            onSearchQuery = { viewModel.setSearchQuery(it) },
                            onSort = { showSortSheet = true },
                            onToggleView = { viewModel.toggleViewMode() },
                            onToggleHidden = { viewModel.toggleHidden() },
                            onSelectAll = { viewModel.selectAll() },
                            onGoRoot = { viewModel.goToRoot() },
                            onGoInternal = { viewModel.goToInternalStorage() },
                            onOpenSettings = { viewModel.setScreen(Screen.SETTINGS) },
                            onOpenUsbOtg = {
                                val usbVols = state.usbVolumes.filter { it.isRemovable && !it.isPrimary }
                                if (usbVols.isNotEmpty()) {
                                    val vol = storageManager.storageVolumes.firstOrNull { it.uuid == usbVols.first().uuid }
                                    if (vol != null) {
                                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                            vol.createOpenDocumentTreeIntent()
                                        else
                                            @Suppress("DEPRECATION") vol.createAccessIntent(null)
                                        if (intent != null) usbAccessLauncher.launch(intent)
                                    }
                                }
                            },
                            onOpenFavorites = { viewModel.openFavorites() },
                            onOpenRecent = { viewModel.openRecent() },
                            onAddTab = { viewModel.addTab() },
                        )
                    }
                }

                if (!state.isSelectionMode && state.tabs.size > 1) {
                    TabBar(
                        tabs = state.tabs,
                        activeTabId = state.activeTabId,
                        onTabClick = { viewModel.switchTab(it) },
                        onTabClose = { viewModel.closeTab(it) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode && !state.isArchiving) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = state.hasClipboard,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.paste() },
                            containerColor = ZonColors.Accent,
                            contentColor = ZonColors.White,
                            modifier = Modifier.padding(bottom = 14.dp),
                            shape = RoundedCornerShape(24.dp),
                            elevation = FloatingActionButtonDefaults.elevation(6.dp, 6.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("วางที่นี่", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }

                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            PremiumSmallFab(
                                icon = Icons.Outlined.Description,
                                label = stringResource(R.string.dialog_new_file),
                                onClick = { fabExpanded = false; showNewFileDialog = true }
                            )
                            Spacer(Modifier.height(12.dp))
                            PremiumSmallFab(
                                icon = Icons.Outlined.CreateNewFolder,
                                label = stringResource(R.string.dialog_new_folder),
                                onClick = { fabExpanded = false; showNewFolderDialog = true }
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    val rotation by animateFloatAsState(
                        targetValue = if (fabExpanded) 135f else 0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                        label = "fab_rotation"
                    )
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = ZonColors.Accent,
                        contentColor = ZonColors.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp, 8.dp)
                    ) {
                        Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(28.dp).rotate(rotation))
                    }
                }
            }
        },
        containerColor = ZonColors.DeepBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ZonColors.DeepBlack, ZonColors.Black)))
        ) {

                AnimatedContent(
                    targetState = Triple(state.isLoading, state.files.isEmpty(), state.isGridView),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "content"
                ) { (loading, empty, grid) ->
                    when {
                        loading -> PremiumLoading()
                        state.errorMessage != null && empty -> ErrorState(
                            message = state.errorMessage!!,
                            onGoHome = { viewModel.goToInternalStorage() }
                        )
                        empty -> PremiumEmptyState()
                        grid -> GridView(
                            files = state.files,
                            selectedFiles = state.selectedFiles,
                            isSelectionMode = state.isSelectionMode,
                            onItemClick = { viewModel.navigateToFolder(it) },
                            onItemLongClick = { file ->
                                if (!state.isSelectionMode) {
                                    viewModel.toggleSelection(file)
                                    contextMenuFile = file
                                } else viewModel.toggleSelection(file)
                            }
                        )
                        else -> ListView(
                            files = state.files,
                            selectedFiles = state.selectedFiles,
                            isSelectionMode = state.isSelectionMode,
                            dateFormat = dateFormat,
                            onItemClick = { viewModel.navigateToFolder(it) },
                            onItemLongClick = { file ->
                                if (!state.isSelectionMode) {
                                    viewModel.toggleSelection(file)
                                    contextMenuFile = file
                                } else viewModel.toggleSelection(file)
                            }
                        )
                    }
                }
            
        }
    }

    contextMenuFile?.let { file ->
        FileContextMenu(
            file = file,
            isFavorite = viewModel.isFavorite(file.path),
            onDismiss = { contextMenuFile = null },
            onAction = { action ->
                val targets = if (state.selectedFiles.size > 1)
                    state.files.filter { state.selectedFiles.contains(it.path) }
                else listOf(file)
                contextMenuFile = null
                val f = File(file.path)
                when (action) {
                    FileAction.OPEN -> {
                        if (file.isDirectory) viewModel.navigateToFolder(file)
                        else if (file.isImage() || file.isText()) viewModel.openPreview(file)
                        else FileOpener.openFile(context, f)
                    }
                    FileAction.PREVIEW -> viewModel.openPreview(file)
                    FileAction.EDIT -> viewModel.openTextEditor(file)
                    FileAction.COPY -> viewModel.copyToClipboard(targets)
                    FileAction.CUT -> viewModel.cutToClipboard(targets)
                    FileAction.RENAME -> renameTarget = file
                    FileAction.SHARE -> {
                        if (targets.size > 1)
                            FileOpener.shareFiles(context, targets.map { File(it.path) })
                        else FileOpener.shareFile(context, f)
                    }
                    FileAction.OPEN_WITH -> FileOpener.openWith(context, f)
                    FileAction.INFO -> infoTarget = file
                    FileAction.DELETE -> deleteTarget = targets.map { it.path }
                    FileAction.COMPRESS -> compressTargets = targets
                    FileAction.EXTRACT -> extractTarget = file
                    FileAction.ARCHIVE_PREVIEW -> {
                        archivePreviewTarget = file
                        viewModel.openArchivePreview(file)
                    }
                    FileAction.SPLIT_EXTRACT -> {
                        splitExtractTarget = file
                    }
                    FileAction.TOGGLE_FAVORITE -> {
                        viewModel.toggleFavorite(file)
                    }
                }
            }
        )
    }

    if (showSortSheet) {
        SortBottomSheet(
            current = state.sortBy,
            ascending = state.ascending,
            showHidden = state.showHidden,
            onDismiss = { showSortSheet = false },
            onSort = { viewModel.setSortBy(it); showSortSheet = false },
            onToggleHidden = { viewModel.toggleHidden() }
        )
    }

    if (showNewFolderDialog) {
        PremiumTextInputDialog(
            title = stringResource(R.string.dialog_new_folder),
            subtitle = stringResource(R.string.dialog_new_folder_desc),
            placeholder = stringResource(R.string.dialog_hint_folder),
            icon = Icons.Outlined.CreateNewFolder,
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { if (it.isNotBlank()) viewModel.createFolder(it); showNewFolderDialog = false }
        )
    }

    if (showNewFileDialog) {
        PremiumTextInputDialog(
            title = stringResource(R.string.dialog_new_file),
            subtitle = stringResource(R.string.dialog_new_file_desc),
            placeholder = stringResource(R.string.dialog_hint_file),
            icon = Icons.Outlined.Description,
            onDismiss = { showNewFileDialog = false },
            onConfirm = { if (it.isNotBlank()) viewModel.createTextFile(it); showNewFileDialog = false }
        )
    }

    renameTarget?.let { file ->
        PremiumTextInputDialog(
            title = stringResource(R.string.dialog_rename),
            subtitle = file.name,
            placeholder = stringResource(R.string.dialog_hint_new_name),
            icon = Icons.Outlined.DriveFileRenameOutline,
            initialValue = file.name,
            onDismiss = { renameTarget = null },
            onConfirm = { if (it.isNotBlank()) viewModel.renameFile(file, it); renameTarget = null }
        )
    }

    if (deleteTarget.isNotEmpty()) {
        PremiumConfirmDialog(
            title = stringResource(R.string.dialog_delete_title, deleteTarget.size),
            message = stringResource(R.string.dialog_delete_desc),
            confirmText = stringResource(R.string.action_delete),
            confirmColor = ZonColors.Danger,
            icon = Icons.Outlined.Delete,
            onDismiss = { deleteTarget = emptyList() },
            onConfirm = { viewModel.deleteFiles(deleteTarget); deleteTarget = emptyList() }
        )
    }

    infoTarget?.let { file ->
        PremiumFileInfoDialog(file = file, onDismiss = { infoTarget = null })
    }

    if (compressTargets.isNotEmpty()) {
        CompressDialog(
            files = compressTargets,
            onDismiss = { compressTargets = emptyList() },
            onConfirm = { name, format, level, password, split ->
                if (split != null && split > 0) {
                    viewModel.createSplitArchive(compressTargets, name, split, level, password)
                } else {
                    viewModel.compress(compressTargets, name, format, level, password, null)
                }
                compressTargets = emptyList()
            }
        )
    }

    extractTarget?.let { file ->
        ExtractDialog(
            file = file,
            onDismiss = { extractTarget = null },
            onConfirm = { password -> viewModel.extract(file, password = password); extractTarget = null }
        )
    }

    splitExtractTarget?.let { file ->
        ExtractDialog(
            file = file,
            onDismiss = { splitExtractTarget = null },
            onConfirm = { password ->
                viewModel.extractSplitArchive(file, password = password)
                splitExtractTarget = null
            }
        )
    }

    state.archiveProgress?.let { progress ->
        PremiumArchiveProgressDialog(
            progress = progress,
            onDismiss = { if (!progress.isRunning) viewModel.dismissArchiveProgress() },
            onCancel = { viewModel.cancelArchive() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    tabs: List<TabState>,
    activeTabId: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit
) {
    Surface(color = ZonColors.Surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                val tabName = File(tab.path).name.ifEmpty {
                    if (tab.path == "/") "Root" else "Storage"
                }
                Surface(
                    color = if (isActive) ZonColors.Accent else ZonColors.MidGray,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onTabClick(tab.id) }
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tabName,
                            color = if (isActive) ZonColors.White else ZonColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.Close,
                                null,
                                tint = if (isActive) ZonColors.White else ZonColors.TextTertiary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onTabClose(tab.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePane(
    files: List<FileItem>,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    isGridView: Boolean,
    dateFormat: SimpleDateFormat,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onGoHome: () -> Unit
) {
    when {
        isLoading -> PremiumLoading()
        errorMessage != null && files.isEmpty() -> ErrorState(
            message = errorMessage,
            onGoHome = onGoHome
        )
        files.isEmpty() -> PremiumEmptyState()
        isGridView -> GridView(
            files = files,
            selectedFiles = selectedFiles,
            isSelectionMode = isSelectionMode,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick
        )
        else -> ListView(
            files = files,
            selectedFiles = selectedFiles,
            isSelectionMode = isSelectionMode,
            dateFormat = dateFormat,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
    }
}

@Composable
fun PremiumEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(ZonColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty), color = ZonColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.empty_desc), color = ZonColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun ErrorState(message: String, onGoHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(ZonColors.Danger.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, null, tint = ZonColors.Danger, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.cannot_access), color = ZonColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = ZonColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onGoHome, colors = ButtonDefaults.textButtonColors(contentColor = ZonColors.Accent)) {
            Text(stringResource(R.string.back_home), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PremiumSmallFab(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = ZonColors.SurfaceElevated, shape = RoundedCornerShape(20.dp), shadowElevation = 4.dp) {
            Text(
                text = label,
                color = ZonColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = ZonColors.SurfaceElevated,
            contentColor = ZonColors.TextPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(4.dp, 4.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalTopBar(
    state: FileManagerState,
    showSearch: Boolean,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onSort: () -> Unit,
    onToggleView: () -> Unit,
    onToggleHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onGoRoot: () -> Unit,
    onGoInternal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsbOtg: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onAddTab: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(color = ZonColors.DeepBlack.copy(alpha = 0.95f)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBackIosNew, "Back", tint = ZonColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            state.currentPath == "/" -> stringResource(R.string.root)
                            state.isAtInternalStorage -> stringResource(R.string.internal_storage)
                            else -> File(state.currentPath).name
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = ZonColors.TextPrimary,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.items_count, state.files.size),
                        color = ZonColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search, null, tint = ZonColors.TextPrimary)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreHoriz, null, tint = ZonColors.TextPrimary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(ZonColors.SurfaceElevated, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .width(260.dp)
                    ) {
                        PremiumDropdownItem(Icons.Outlined.PhoneAndroid, stringResource(R.string.internal_storage)) {
                            menuExpanded = false; onGoInternal()
                        }
                        PremiumDropdownItem(Icons.Outlined.Storage, stringResource(R.string.root)) {
                            menuExpanded = false; onGoRoot()
                        }
                        PremiumDropdownItem(Icons.Outlined.Usb, "USB OTG") {
                            menuExpanded = false; onOpenUsbOtg()
                        }
                        HorizontalDivider(color = ZonColors.Separator, modifier = Modifier.padding(vertical = 4.dp))
                        PremiumDropdownItem(Icons.Filled.Star, "รายการโปรด") {
                            menuExpanded = false; onOpenFavorites()
                        }
                        PremiumDropdownItem(Icons.Outlined.History, "เปิดล่าสุด") {
                            menuExpanded = false; onOpenRecent()
                        }
                        PremiumDropdownItem(Icons.Outlined.Tab, "เปิดแท็บใหม่") {
                            menuExpanded = false; onAddTab()
                        }
                        HorizontalDivider(color = ZonColors.Separator, modifier = Modifier.padding(vertical = 4.dp))
                        PremiumDropdownItem(
                            if (state.isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                            if (state.isGridView) stringResource(R.string.menu_list_view) else stringResource(R.string.menu_grid_view)
                        ) { menuExpanded = false; onToggleView() }
                        PremiumDropdownItem(Icons.Outlined.Sort, stringResource(R.string.menu_sort)) {
                            menuExpanded = false; onSort()
                        }
                        PremiumDropdownItem(
                            if (state.showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (state.showHidden) stringResource(R.string.menu_hide_hidden) else stringResource(R.string.menu_show_hidden)
                        ) { menuExpanded = false; onToggleHidden() }
                        PremiumDropdownItem(Icons.Outlined.SelectAll, stringResource(R.string.menu_select_all)) {
                            menuExpanded = false; onSelectAll()
                        }
                        HorizontalDivider(color = ZonColors.Separator, modifier = Modifier.padding(vertical = 4.dp))
                        PremiumDropdownItem(Icons.Outlined.Settings, stringResource(R.string.menu_settings)) {
                            menuExpanded = false; onOpenSettings()
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSearch, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQuery,
                    placeholder = { Text(stringResource(R.string.search_hint), color = ZonColors.TextTertiary) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = ZonColors.TextSecondary) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
            }

            HorizontalDivider(color = ZonColors.Separator, thickness = 0.5.dp)
        }
    }
}

@Composable
fun PremiumDropdownItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ZonColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = ZonColors.TextPrimary, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit
) {
    Surface(color = ZonColors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null, tint = ZonColors.TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.selected_count, count), color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(stringResource(R.string.selecting), color = ZonColors.Accent, fontSize = 11.sp)
            }
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, null, tint = ZonColors.TextPrimary) }
            IconButton(onClick = onCompress) { Icon(Icons.Outlined.FolderZip, null, tint = ZonColors.TextPrimary) }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, null, tint = ZonColors.TextPrimary) }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, null, tint = ZonColors.TextPrimary) }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, null, tint = ZonColors.TextPrimary) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = ZonColors.Danger) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListView(
    files: List<FileItem>,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    dateFormat: SimpleDateFormat,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files, key = { it.path }) { file ->
            PremiumListRow(
                file = file,
                dateFormat = dateFormat,
                isSelected = selectedFiles.contains(file.path),
                isSelectionMode = isSelectionMode,
                onClick = { onItemClick(file) },
                onLongClick = { onItemLongClick(file) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridView(
    files: List<FileItem>,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItems(files, key = { it.path }) { file ->
            PremiumGridItem(
                file = file,
                isSelected = selectedFiles.contains(file.path),
                isSelectionMode = isSelectionMode,
                onClick = { onItemClick(file) },
                onLongClick = { onItemLongClick(file) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumListRow(
    file: FileItem,
    dateFormat: SimpleDateFormat,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) ZonColors.Selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            PremiumCheckbox(checked = isSelected, onToggle = onLongClick)
            Spacer(Modifier.width(14.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
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
                text = if (file.isDirectory) dateFormat.format(Date(file.lastModified))
                       else "${file.getReadableSize()} · ${dateFormat.format(Date(file.lastModified))}",
                color = ZonColors.TextSecondary, fontSize = 12.sp, maxLines = 1
            )
        }
        if (file.isDirectory && !isSelectionMode) {
            Icon(Icons.Filled.ChevronRight, null, tint = ZonColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PremiumCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "cb"
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (checked) ZonColors.Accent else Color.Transparent)
            .border(
                width = if (checked) 0.dp else 1.5.dp,
                color = if (checked) Color.Transparent else ZonColors.TextTertiary,
                shape = CircleShape
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = ZonColors.White, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumGridItem(
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "gs"
    )
    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) ZonColors.Selected else ZonColors.Surface)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) ZonColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(file.getIconColor().copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                file.name,
                color = ZonColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (isSelectionMode && isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ZonColors.Accent)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = ZonColors.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

enum class FileAction {
    OPEN, PREVIEW, EDIT, COPY, CUT, RENAME, SHARE, OPEN_WITH,
    INFO, DELETE, COMPRESS, EXTRACT, ARCHIVE_PREVIEW, SPLIT_EXTRACT,
    TOGGLE_FAVORITE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenu(
    file: FileItem,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(file.getIconColor().copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, color = ZonColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        file.getReadableSize().ifEmpty { stringResource(R.string.info_type_folder) },
                        color = ZonColors.TextSecondary, fontSize = 13.sp
                    )
                }
                IconButton(onClick = { onAction(FileAction.TOGGLE_FAVORITE) }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        null,
                        tint = if (isFavorite) ZonColors.Warning else ZonColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            PremiumActionGroup {
                if (!file.isDirectory && !file.isArchive()) {
                    PremiumActionItem(Icons.Outlined.OpenInNew, stringResource(R.string.action_open), ZonColors.TextPrimary) { onAction(FileAction.OPEN) }
                    if (file.isImage() || file.isText()) {
                        PremiumActionItem(Icons.Outlined.Visibility, stringResource(R.string.action_preview), ZonColors.TextPrimary) { onAction(FileAction.PREVIEW) }
                    }
                    if (file.isText()) {
                        PremiumActionItem(Icons.Outlined.Edit, stringResource(R.string.action_edit), ZonColors.TextPrimary) { onAction(FileAction.EDIT) }
                    }
                } else if (file.isDirectory) {
                    PremiumActionItem(Icons.Outlined.OpenInNew, stringResource(R.string.action_open), ZonColors.TextPrimary) { onAction(FileAction.OPEN) }
                } else if (file.isArchive()) {
                    PremiumActionItem(Icons.Outlined.Unarchive, stringResource(R.string.action_extract), ZonColors.Accent) { onAction(FileAction.EXTRACT) }
                    PremiumActionItem(Icons.Outlined.Visibility, stringResource(R.string.action_preview), ZonColors.TextPrimary) { onAction(FileAction.ARCHIVE_PREVIEW) }
                    if (file.name.lowercase().let {
                        it.endsWith(".z01") || it.endsWith(".7z.001") || it.endsWith(".part1.rar")
                    }) {
                        PremiumActionItem(Icons.Outlined.FolderZip, "รวม+แตกไฟล์ที่แบ่งไว้", ZonColors.Warning) { onAction(FileAction.SPLIT_EXTRACT) }
                    }
                }
                PremiumActionItem(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), ZonColors.TextPrimary) { onAction(FileAction.COPY) }
                PremiumActionItem(Icons.Outlined.ContentCut, stringResource(R.string.action_cut), ZonColors.TextPrimary) { onAction(FileAction.CUT) }
                PremiumActionItem(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.action_rename), ZonColors.TextPrimary) { onAction(FileAction.RENAME) }
                PremiumActionItem(Icons.Outlined.Share, stringResource(R.string.action_share), ZonColors.TextPrimary) { onAction(FileAction.SHARE) }
                if (!file.isDirectory) {
                    PremiumActionItem(Icons.Outlined.OpenInBrowser, stringResource(R.string.action_open_with), ZonColors.TextPrimary) { onAction(FileAction.OPEN_WITH) }
                }
            }

            Spacer(Modifier.height(10.dp))

            PremiumActionGroup {
                if (file.isDirectory) {
                    PremiumActionItem(Icons.Outlined.Archive, stringResource(R.string.action_compress), ZonColors.TextPrimary) { onAction(FileAction.COMPRESS) }
                } else if (!file.isArchive()) {
                    PremiumActionItem(Icons.Outlined.Archive, stringResource(R.string.action_compress), ZonColors.TextPrimary) { onAction(FileAction.COMPRESS) }
                }
                PremiumActionItem(Icons.Outlined.Info, stringResource(R.string.action_info), ZonColors.TextPrimary) { onAction(FileAction.INFO) }
                PremiumActionItem(Icons.Outlined.Delete, stringResource(R.string.action_delete), ZonColors.Danger) { onAction(FileAction.DELETE) }
            }
        }
    }
}

@Composable
fun PremiumActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = ZonColors.Surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun PremiumActionItem(icon: ImageVector, label: String, tint: Color = ZonColors.TextPrimary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    current: SortBy,
    ascending: Boolean,
    showHidden: Boolean,
    onDismiss: () -> Unit,
    onSort: (SortBy) -> Unit,
    onToggleHidden: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(
                stringResource(R.string.sort_title),
                color = ZonColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
            )
            PremiumActionGroup {
                PremiumSortItem(Icons.Outlined.SortByAlpha, stringResource(R.string.sort_name), current == SortBy.NAME, ascending) { onSort(SortBy.NAME) }
                PremiumSortItem(Icons.Outlined.Straighten, stringResource(R.string.sort_size), current == SortBy.SIZE, ascending) { onSort(SortBy.SIZE) }
                PremiumSortItem(Icons.Outlined.Schedule, stringResource(R.string.sort_date), current == SortBy.DATE, ascending) { onSort(SortBy.DATE) }
            }
            Spacer(Modifier.height(10.dp))
            PremiumActionGroup {
                PremiumActionItem(
                    if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (showHidden) stringResource(R.string.menu_hide_hidden) else stringResource(R.string.menu_show_hidden)
                ) { onToggleHidden() }
            }
        }
    }
}

@Composable
fun PremiumSortItem(icon: ImageVector, label: String, isCurrent: Boolean, ascending: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isCurrent) ZonColors.Accent else ZonColors.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            label,
            color = if (isCurrent) ZonColors.Accent else ZonColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (isCurrent) Icon(
            if (ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            null, tint = ZonColors.Accent, modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun PremiumTextInputDialog(
    title: String,
    subtitle: String,
    placeholder: String,
    icon: ImageVector,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(52.dp).background(ZonColors.Accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = ZonColors.Accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(title, color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = ZonColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder, color = ZonColors.TextTertiary) },
                singleLine = true,
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
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.dialog_ok), color = ZonColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = ZonColors.TextSecondary)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun PremiumConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    icon: ImageVector,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(52.dp).background(confirmColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = confirmColor, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(title, color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        },
        text = {
            Text(message, color = ZonColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = ZonColors.TextSecondary)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun PremiumFileInfoDialog(file: FileItem, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var md5 by remember { mutableStateOf<String?>(null) }
    var sha1 by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(true) }

    LaunchedEffect(file.path) {
        isCalculating = true
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var m: String? = null
            var s: String? = null
            if (!file.isDirectory) {
                try {
                    val f = File(file.path)
                    if (f.length() < 100L * 1024 * 1024) {
                        val md5Digest = java.security.MessageDigest.getInstance("MD5")
                        val sha1Digest = java.security.MessageDigest.getInstance("SHA-1")
                        f.inputStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var n: Int
                            while (input.read(buf).also { n = it } > 0) {
                                md5Digest.update(buf, 0, n)
                                sha1Digest.update(buf, 0, n)
                            }
                        }
                        m = md5Digest.digest().joinToString("") { "%02x".format(it) }
                        s = sha1Digest.digest().joinToString("") { "%02x".format(it) }
                    }
                } catch (e: Exception) { }
            }
            Pair(m, s)
        }
        md5 = result.first
        sha1 = result.second
        isCalculating = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(56.dp).background(file.getIconColor().copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(file.getIcon(), null, tint = file.getIconColor(), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.info_title), color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PremiumInfoRow(stringResource(R.string.info_name), file.name)
                PremiumInfoRow(stringResource(R.string.info_path), file.path)
                PremiumInfoRow(stringResource(R.string.info_size), file.getReadableSize().ifEmpty { "—" })
                PremiumInfoRow(stringResource(R.string.info_modified), dateFormat.format(Date(file.lastModified)))
                PremiumInfoRow(stringResource(R.string.info_type), if (file.isDirectory) stringResource(R.string.info_type_folder) else stringResource(R.string.info_type_file))

                val f = File(file.path)
                if (f.exists()) {
                    val perms = buildString {
                        append(if (f.canRead()) "r" else "-")
                        append(if (f.canWrite()) "w" else "-")
                        append(if (f.canExecute()) "x" else "-")
                    }
                    PremiumInfoRow(stringResource(R.string.info_perms), perms)
                }

                if (!file.isDirectory) {
                    if (isCalculating) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("กำลังคำนวณ Checksum...", color = ZonColors.TextTertiary, fontSize = 11.sp)
                        }
                    } else {
                        md5?.let { PremiumInfoRow(stringResource(R.string.info_md5), it) }
                        sha1?.let { PremiumInfoRow(stringResource(R.string.info_sha1), it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), color = ZonColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun PremiumInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, color = ZonColors.TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = ZonColors.TextPrimary, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CompressDialog(
    files: List<FileItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, ArchiveFormat, CompressionOption, String?, Int?) -> Unit
) {
    var name by remember {
        mutableStateOf(
            if (files.size == 1) files.first().name.substringBeforeLast('.')
            else "archive_${System.currentTimeMillis()}"
        )
    }
    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var level by remember { mutableStateOf(CompressionOption.NORMAL) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var formatExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitSize by remember { mutableStateOf("50") }

    val fullName = if (splitEnabled) "$name.zip" else "$name.${format.ext}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(52.dp).background(ZonColors.IconArchive.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.FolderZip, null, tint = ZonColors.IconArchive, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.dialog_compress_title), color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.field_name), color = ZonColors.TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { name = it.substringBeforeLast('.', it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ZonColors.TextPrimary, unfocusedTextColor = ZonColors.TextPrimary,
                        focusedBorderColor = ZonColors.Accent, unfocusedBorderColor = ZonColors.LightGray,
                        focusedContainerColor = ZonColors.Surface, unfocusedContainerColor = ZonColors.Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.field_format), color = ZonColors.TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { if (!splitEnabled) formatExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !splitEnabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZonColors.TextPrimary),
                        border = BorderStroke(1.dp, ZonColors.LightGray)
                    ) {
                        Text(if (splitEnabled) "ZIP (Split)" else format.displayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        modifier = Modifier.background(ZonColors.SurfaceElevated, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                    ) {
                        ArchiveFormat.values().filter { it.canCompress }.forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text(fmt.displayName, color = ZonColors.TextPrimary) },
                                onClick = { format = fmt; formatExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.field_level), color = ZonColors.TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { levelExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZonColors.TextPrimary),
                        border = BorderStroke(1.dp, ZonColors.LightGray)
                    ) {
                        Text(level.displayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false },
                        modifier = Modifier.background(ZonColors.SurfaceElevated, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                    ) {
                        CompressionOption.values().forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.displayName, color = ZonColors.TextPrimary) },
                                onClick = { level = opt; levelExpanded = false }
                            )
                        }
                    }
                }

                if (format.canPassword || splitEnabled) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.field_password), color = ZonColors.TextTertiary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
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
                            focusedTextColor = ZonColors.TextPrimary, unfocusedTextColor = ZonColors.TextPrimary,
                            focusedBorderColor = ZonColors.Accent, unfocusedBorderColor = ZonColors.LightGray,
                            focusedContainerColor = ZonColors.Surface, unfocusedContainerColor = ZonColors.Surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = splitEnabled,
                        onCheckedChange = { splitEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ZonColors.White,
                            checkedTrackColor = ZonColors.Accent,
                            uncheckedThumbColor = ZonColors.TextSecondary,
                            uncheckedTrackColor = ZonColors.MidGray
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.split_enable), color = ZonColors.TextPrimary, fontSize = 14.sp)
                }

                if (splitEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.field_split_size), color = ZonColors.TextTertiary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = splitSize,
                        onValueChange = { splitSize = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ZonColors.TextPrimary, unfocusedTextColor = ZonColors.TextPrimary,
                            focusedBorderColor = ZonColors.Accent, unfocusedBorderColor = ZonColors.LightGray,
                            focusedContainerColor = ZonColors.Surface, unfocusedContainerColor = ZonColors.Surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(
                        name, format, level,
                        if (password.isBlank()) null else password,
                        if (splitEnabled) splitSize.toIntOrNull()?.takeIf { it > 0 } else null
                    )
                }
            }) {
                Text(stringResource(R.string.dialog_ok), color = ZonColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = ZonColors.TextSecondary)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun ExtractDialog(
    file: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val needsPassword = remember {
        val n = file.name.lowercase()
        n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(52.dp).background(ZonColors.Accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Unarchive, null, tint = ZonColors.Accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.dialog_extract_title), color = ZonColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        },
        text = {
            Column {
                Text(file.name, color = ZonColors.TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.extract_to_same), color = ZonColors.TextSecondary, fontSize = 12.sp)

                if (needsPassword) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.field_password_extract), color = ZonColors.TextTertiary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.field_password_hint), color = ZonColors.TextTertiary) },
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
                            focusedTextColor = ZonColors.TextPrimary, unfocusedTextColor = ZonColors.TextPrimary,
                            focusedBorderColor = ZonColors.Accent, unfocusedBorderColor = ZonColors.LightGray,
                            focusedContainerColor = ZonColors.Surface, unfocusedContainerColor = ZonColors.Surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (password.isBlank()) null else password) }) {
                Text(stringResource(R.string.action_extract), color = ZonColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = ZonColors.TextSecondary)
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun PremiumArchiveProgressDialog(
    progress: ArchiveProgress,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.percent / 100f,
        animationSpec = tween(300),
        label = "ap"
    )

    AlertDialog(
        onDismissRequest = { if (!progress.isRunning) onDismiss() },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (progress.isRunning) {
                    CircularProgressIndicator(color = ZonColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                } else {
                    Box(
                        modifier = Modifier.size(56.dp).background(
                            if (progress.isError) ZonColors.Danger.copy(alpha = 0.14f) else ZonColors.IconDoc.copy(alpha = 0.14f),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (progress.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                            null,
                            tint = if (progress.isError) ZonColors.Danger else ZonColors.IconDoc,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        progress.isError -> stringResource(R.string.progress_error)
                        progress.isCompleted -> stringResource(R.string.progress_done)
                        else -> progress.message
                    },
                    color = if (progress.isError) ZonColors.Danger else ZonColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column {
                if (progress.isRunning) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(ZonColors.Surface)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brush.horizontalGradient(listOf(ZonColors.Accent, ZonColors.Accent.copy(alpha = 0.7f))))
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${progress.percent}%",
                        color = ZonColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    if (progress.currentFile.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            progress.currentFile,
                            color = ZonColors.TextSecondary, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        progress.message,
                        color = if (progress.isError) ZonColors.Danger else ZonColors.TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (!progress.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_ok), color = ZonColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            } else if (progress.canCancel) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.progress_cancel), color = ZonColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = ZonColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun Modifier.statusBarsPadding(): Modifier = this.then(
    Modifier.padding(top = 0.dp)
)
