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

// app/src/main/java/com/zon/filemanager/FileManagerViewModel.kt
package com.zon.filemanager

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class SortBy { NAME, SIZE, DATE }
enum class ClipboardMode { NONE, COPY, CUT }
enum class Screen {
    FILES, SETTINGS, PREVIEW, TEXT_EDITOR, STORAGE_ANALYZER,
    DUP_FINDER, APP_MANAGER, ARCHIVE_PREVIEW, USB_OTG,
    FAVORITES, RECENT, FTP, ABOUT, LICENSES
}

val INTERNAL_STORAGE_ROOT: String = Environment.getExternalStorageDirectory().absolutePath

data class TabState(
    val id: Int,
    val path: String,
    val history: List<String> = emptyList()
)

data class FileManagerState(
    val screen: Screen = Screen.FILES,
    val tabs: List<TabState> = listOf(TabState(0, INTERNAL_STORAGE_ROOT)),
    val activeTabId: Int = 0,
    val currentPath: String = INTERNAL_STORAGE_ROOT,
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isDualPane: Boolean = false,
    val secondPanePath: String = INTERNAL_STORAGE_ROOT,
    val secondPaneFiles: List<FileItem> = emptyList(),
    val secondPaneSelected: Set<String> = emptySet(),
    val clipboardFiles: List<FileItem> = emptyList(),
    val clipboardMode: ClipboardMode = ClipboardMode.NONE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val showHidden: Boolean = false,
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val archiveProgress: ArchiveProgress? = null,
    val rootEnabled: Boolean = false,
    val rootAvailable: Boolean = false,
    val previewItem: FileItem? = null,
    val textEditorItem: FileItem? = null,
    val archivePreviewItem: FileItem? = null,
    val archiveEntries: List<ArchiveEntry> = emptyList(),
    val archivePreviewLoading: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val storageScanning: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val duplicateScanning: Boolean = false,
    val selectedDuplicates: Set<String> = emptySet(),
    val installedApps: List<AppInfo> = emptyList(),
    val appsLoading: Boolean = false,
    val usbVolumes: List<UsbVolume> = emptyList(),
    val currentUsbPath: String? = null,
    val currentUsbUri: Uri? = null,
    val usbFiles: List<FileItem> = emptyList(),
    val usbLoading: Boolean = false,
    val splitArchiveInfo: SplitArchiveInfo? = null,
    val favorites: List<FileItem> = emptyList(),
    val recent: List<FileItem> = emptyList(),
    val ftpServers: List<FtpServer> = emptyList(),
    val ftpState: FtpConnectionState = FtpConnectionState()
) {
    val isSelectionMode: Boolean get() = selectedFiles.isNotEmpty()
    val hasClipboard: Boolean get() = clipboardFiles.isNotEmpty()
    val isAtInternalStorage: Boolean get() = currentPath == INTERNAL_STORAGE_ROOT
    val isAtFilesystemRoot: Boolean get() = currentPath == "/"
    val isArchiving: Boolean get() = archiveProgress?.isRunning == true
    val isInUsbMode: Boolean get() = currentUsbPath != null
    val activeTab: TabState get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val zonArchive = ZonArchive(application)
    private val duplicateFinder = DuplicateFinder()
    private val storageAnalyzer = StorageAnalyzer()
    private val appManager = AppManagerHelper(application)
    private val usbOtgManager = UsbOtgManager(application)
    private val splitManager = SplitArchiveManager()
    private val prefsManager = FavoritesRecentManager(application)
    private val ftpManager = FtpManager()
    private var archiveJob: Job? = null
    private var scanJob: Job? = null
    private var directoryJob: Job? = null
    private var searchJob: Job? = null
    private var nextTabId = 1

    init {
        loadPrefs()
        refresh()
        checkRoot()
        loadUsbVolumes()
    }

    private fun loadPrefs() {
        _state.value = _state.value.copy(
            favorites = prefsManager.getFavorites(),
            recent = prefsManager.getRecent(),
            ftpServers = prefsManager.getFtpServers()
        )
    }

    fun refresh() = loadDirectory(_state.value.currentPath)

    fun loadDirectory(path: String) {
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) throw Exception("ไม่พบโฟลเดอร์")

                val listed = dir.listFiles()

                if (listed == null && _state.value.rootEnabled && _state.value.rootAvailable) {
                    val rootList = RootHelper.listDirectoryRoot(path)
                    if (rootList.isNotEmpty()) {
                        val filtered = rootList
                            .filter { _state.value.showHidden || !it.name.startsWith(".") }
                            .filter {
                                _state.value.searchQuery.isBlank() ||
                                it.name.contains(_state.value.searchQuery, ignoreCase = true)
                            }
                        val sorted = applySort(filtered)
                        updateTabPath(path)
                        _state.value = _state.value.copy(
                            currentPath = path,
                            files = sorted,
                            selectedFiles = emptySet(),
                            isLoading = false
                        )
                        return@launch
                    }
                }

                val allFiles = listed?.map { it.toFileItem() } ?: emptyList()
                val filtered = allFiles
                    .filter { _state.value.showHidden || !it.name.startsWith(".") }
                    .filter {
                        _state.value.searchQuery.isBlank() ||
                        it.name.contains(_state.value.searchQuery, ignoreCase = true)
                    }

                val sorted = applySort(filtered)
                updateTabPath(path)
                _state.value = _state.value.copy(
                    currentPath = path,
                    files = sorted,
                    selectedFiles = emptySet(),
                    isLoading = false,
                    errorMessage = if (listed == null) "ไม่มีสิทธิ์เข้าถึงโฟลเดอร์นี้" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "เกิดข้อผิดพลาด"
                )
            }
        }
    }

    private fun updateTabPath(path: String) {
        val s = _state.value
        val updated = s.tabs.map { tab ->
            if (tab.id != s.activeTabId || tab.path == path) tab
            else tab.copy(path = path)
        }
        _state.value = s.copy(tabs = updated)
    }

    private fun pushNavigationHistory(from: String, to: String) {
        if (from == to) return
        val s = _state.value
        _state.value = s.copy(tabs = s.tabs.map { tab ->
            if (tab.id == s.activeTabId) tab.copy(path = to, history = tab.history + from) else tab
        })
    }

    private fun applySort(list: List<FileItem>): List<FileItem> {
        val s = _state.value
        val comparator = when (s.sortBy) {
            SortBy.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.DATE -> compareBy { it.lastModified }
        }
        val sorted = if (s.ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
        return sorted.sortedByDescending { it.isDirectory }
    }

    fun setSortBy(sortBy: SortBy) {
        _state.value = _state.value.copy(
            sortBy = sortBy,
            ascending = if (_state.value.sortBy == sortBy) !_state.value.ascending else true
        )
        refresh()
    }

    fun toggleHidden() {
        _state.value = _state.value.copy(showHidden = !_state.value.showHidden)
        refresh()
    }

    fun toggleViewMode() {
        _state.value = _state.value.copy(isGridView = !_state.value.isGridView)
    }

    fun setSearchQuery(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            refresh()
        }
    }

    fun toggleSelection(file: FileItem) {
        val set = _state.value.selectedFiles.toMutableSet()
        if (!set.add(file.path)) set.remove(file.path)
        _state.value = _state.value.copy(selectedFiles = set)
    }

    fun selectAll() {
        _state.value = _state.value.copy(selectedFiles = _state.value.files.map { it.path }.toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedFiles = emptySet())
    }

    fun navigateToFolder(folder: FileItem) {
        if (_state.value.isSelectionMode) { toggleSelection(folder); return }
        if (folder.isDirectory) {
            val current = _state.value.currentPath
            pushNavigationHistory(current, folder.path)
            loadDirectory(folder.path)
        }
    }

    /**
     * แตะไฟล์ → เปิดตามประเภท
     * - โฟลเดอร์ → เข้าไป
     * - archive (zip/rar/7z/tar ฯลฯ) → เปิด preview
     * - รูปภาพ/ข้อความ → เปิด preview
     * - อื่นๆ → เปิดด้วยแอปอื่น
     */
    fun openFile(file: FileItem) {
        if (_state.value.isSelectionMode) { toggleSelection(file); return }
        when {
            file.isDirectory -> {
                pushNavigationHistory(_state.value.currentPath, file.path)
                loadDirectory(file.path)
            }
            file.isArchive() -> openArchivePreview(file)
            file.isImage() || file.isText() -> openPreview(file)
            else -> {
                addRecent(file)
            }
        }
    }

    fun navigateUp(): Boolean {
        if (_state.value.isSelectionMode) { clearSelection(); return true }
        if (_state.value.searchQuery.isNotBlank()) {
            _state.value = _state.value.copy(searchQuery = "")
            refresh(); return true
        }
        if (_state.value.currentPath == "/") {
            // อยู่ root ของระบบแล้ว → ไม่ต้องทำอะไร
            return false
        }
        val history = _state.value.activeTab.history
        if (history.isNotEmpty()) {
            val prev = history.last()
            val s = _state.value
            val updated = s.tabs.map { tab ->
                if (tab.id == s.activeTabId) tab.copy(history = tab.history.dropLast(1))
                else tab
            }
            _state.value = s.copy(tabs = updated)
            loadDirectory(prev)
            return true
        }
        if (_state.value.isAtInternalStorage) {
            // อยู่ Internal Storage → ไม่ถอยไปไหน
            return false
        }
        val cur = File(_state.value.currentPath)
        val parent = cur.parentFile
        if (parent != null) {
            loadDirectory(parent.absolutePath)
            return true
        }
        return false
    }

    fun goToInternalStorage() { loadDirectory(INTERNAL_STORAGE_ROOT) }
    fun goToRoot() { loadDirectory("/") }

    private fun validChildName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed != "." && trimmed != "..") { "ชื่อไม่ถูกต้อง" }
        require(!trimmed.contains('/') && !trimmed.contains('\\') && !trimmed.contains('\u0000')) { "ชื่อมีอักขระต้องห้าม" }
        return trimmed
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(_state.value.currentPath, validChildName(name))
                if (!file.mkdirs() && !file.isDirectory) throw IOException("สร้างโฟลเดอร์ไม่สำเร็จ")
            } catch (e: Exception) { _state.value = _state.value.copy(errorMessage = e.message) }
            refresh()
        }
    }

    fun createTextFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val raw = validChildName(name)
                val fileName = if (raw.endsWith(".txt", ignoreCase = true)) raw else "$raw.txt"
                val file = File(_state.value.currentPath, fileName)
                if (file.exists()) throw IOException("มีไฟล์ชื่อนี้อยู่แล้ว")
                if (!file.createNewFile()) throw IOException("สร้างไฟล์ไม่สำเร็จ")
            } catch (e: Exception) { _state.value = _state.value.copy(errorMessage = e.message) }
            refresh()
        }
    }

    fun renameFile(file: FileItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val old = File(file.path)
                val target = File(old.parentFile ?: throw IOException("ไม่มีโฟลเดอร์แม่"), validChildName(newName))
                if (target.exists() && target.canonicalPath != old.canonicalPath) throw IOException("มีชื่อปลายทางอยู่แล้ว")
                if (!old.renameTo(target)) throw IOException("เปลี่ยนชื่อไม่สำเร็จ")
            } catch (e: Exception) { _state.value = _state.value.copy(errorMessage = e.message) }
            refresh()
        }
    }

    fun deleteFiles(paths: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { path ->
                val f = File(path)
                if (!f.deleteRecursively()) {
                    if (_state.value.rootEnabled && _state.value.rootAvailable) {
                        RootHelper.deleteAsRoot(path)
                    }
                }
            }
            clearSelection()
            refresh()
        }
    }

    fun copyToClipboard(files: List<FileItem>) {
        _state.value = _state.value.copy(clipboardFiles = files, clipboardMode = ClipboardMode.COPY)
        clearSelection()
    }

    fun cutToClipboard(files: List<FileItem>) {
        _state.value = _state.value.copy(clipboardFiles = files, clipboardMode = ClipboardMode.CUT)
        clearSelection()
    }

    fun paste() {
        val s = _state.value
        if (s.clipboardFiles.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (item in s.clipboardFiles) {
                currentCoroutineContext().ensureActive()
                val src = File(item.path)
                val dst = File(s.currentPath, item.name)
                if (src.exists()) {
                    if (src.canonicalPath == dst.canonicalPath) continue
                    if (src.isDirectory && dst.canonicalPath.startsWith(src.canonicalPath + File.separator)) {
                        _state.value = _state.value.copy(errorMessage = "ไม่สามารถวางโฟลเดอร์ไว้ภายในตัวเองได้")
                        continue
                    }
                    if (dst.exists()) {
                        _state.value = _state.value.copy(errorMessage = "มีไฟล์/โฟลเดอร์ปลายทางอยู่แล้ว: ${item.name}")
                        continue
                    }
                    if (s.clipboardMode == ClipboardMode.CUT) {
                        if (!src.renameTo(dst)) copyRecursive(src, dst).also { if (src.deleteRecursively().not()) throw IOException("ย้ายไฟล์ไม่สำเร็จ") }
                    } else {
                        copyRecursive(src, dst)
                    }
                }
            }
            _state.value = _state.value.copy(clipboardFiles = emptyList(), clipboardMode = ClipboardMode.NONE)
            refresh()
        }
    }

    /**
     * คัดลอกไฟล์/โฟลเดอร์ โดยใช้ BufferPool (64KB buffer reuse)
     * แก้ปัญหา OOM ตอนคัดลอกไฟล์ใหญ่
     */
    private suspend fun copyRecursive(src: File, dst: File) {
        currentCoroutineContext().ensureActive()
        if (src.isDirectory) {
            if (!dst.mkdirs() && !dst.isDirectory) throw IOException("สร้างปลายทางไม่สำเร็จ")
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    BufferPool.withBuffer { buf ->
                        var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            output.write(buf, 0, n)
                        }
                    }
                }
            }
        }
    }

    // ==================== Archive ====================

    fun compress(
        sources: List<FileItem>, outputName: String, format: ArchiveFormat,
        level: CompressionOption, password: String?, splitSizeMb: Int? = null
    ) {
        archiveJob?.cancel()
        val outputPath = File(_state.value.currentPath, outputName).absolutePath
        archiveJob = viewModelScope.launch {
            zonArchive.compress(sources, outputPath, format, level, password, splitSizeMb) { progress ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(archiveProgress = progress)
                }
            }
            withContext(Dispatchers.Main) { clearSelection(); refresh() }
        }
    }

    fun extract(source: FileItem, outputDir: String? = null, password: String? = null) {
        archiveJob?.cancel()
        val baseName = source.name
            .substringBeforeLast(".tar.md5").substringBeforeLast(".tar.xz")
            .substringBeforeLast(".tar.gz").substringBeforeLast(".tar.bz2")
            .substringBeforeLast(".tar.lz4").substringBeforeLast(".tgz")
            .substringBeforeLast(".tbz2").substringBeforeLast(".txz").substringBeforeLast('.')
        val targetDir = outputDir ?: File(_state.value.currentPath, baseName).absolutePath
        archiveJob = viewModelScope.launch {
            File(targetDir).mkdirs()
            zonArchive.extract(source, targetDir, password) { progress ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(archiveProgress = progress)
                }
            }
            withContext(Dispatchers.Main) { clearSelection(); refresh() }
        }
    }

    fun extractSplitArchive(file: FileItem, outputDir: String? = null, password: String? = null) {
        val info = splitManager.detectSplitArchive(file) ?: return
        val targetDir = outputDir ?: File(_state.value.currentPath, info.baseName).absolutePath
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            File(targetDir).mkdirs()
            zonArchive.extractSplitArchive(info, targetDir, password) { progress ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(archiveProgress = progress)
                }
            }
            withContext(Dispatchers.Main) { clearSelection(); refresh() }
        }
    }

    fun createSplitArchive(
        sources: List<FileItem>, outputName: String, splitSizeMb: Int,
        level: CompressionOption, password: String?
    ) {
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            zonArchive.createSplitArchive(sources, outputName, splitSizeMb, level, password) { progress ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(archiveProgress = progress)
                }
            }
            withContext(Dispatchers.Main) { clearSelection(); refresh() }
        }
    }

    fun cancelArchive() {
        archiveJob?.cancel()
        archiveJob = null
        _state.value = _state.value.copy(archiveProgress = ArchiveProgress(
            isRunning = false, message = "ยกเลิกแล้ว", canCancel = false
        ))
    }

    fun dismissArchiveProgress() {
        _state.value = _state.value.copy(archiveProgress = null)
    }

    fun openArchivePreview(file: FileItem, password: String? = null) {
        addRecent(file)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                screen = Screen.ARCHIVE_PREVIEW,
                archivePreviewItem = file,
                archivePreviewLoading = true,
                archiveEntries = emptyList()
            )
            val entries = ArchivePreview.listEntries(file, password)
            _state.value = _state.value.copy(archiveEntries = entries, archivePreviewLoading = false)
        }
    }

    fun extractArchiveEntry(entry: ArchiveEntry, password: String? = null) {
        val file = _state.value.archivePreviewItem ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val outPath = File(_state.value.currentPath, entry.name).absolutePath
            ArchivePreview.extractSingle(file, entry, outPath, password)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /**
     * แตะ entry ใน archive → ถ้าเป็นรูป/ข้อความ → แตกไป cache แล้ว preview
     */
    fun openArchiveEntry(entry: ArchiveEntry, password: String? = null) {
        val file = _state.value.archivePreviewItem ?: return
        if (entry.isDirectory) return

        val ext = entry.name.substringAfterLast('.', "").lowercase()
        val previewable = ext in listOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg",
            "txt", "md", "log", "json", "xml", "html", "css", "js",
            "kt", "java", "py", "cpp", "c", "h", "yml", "yaml", "ini", "conf", "sh", "csv"
        )

        if (!previewable) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(getApplication<Application>().cacheDir, "zon_preview")
                cacheDir.mkdirs()
                val outFile = File(cacheDir, entry.name.substringAfterLast('/'))
                if (!outFile.exists()) {
                    ArchivePreview.extractSingle(file, entry, outFile.absolutePath, password)
                }
                val previewItem = FileItem(
                    name = outFile.name,
                    path = outFile.absolutePath,
                    isDirectory = false,
                    size = outFile.length(),
                    lastModified = outFile.lastModified()
                )
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        screen = Screen.PREVIEW,
                        previewItem = previewItem
                    )
                }
            } catch (e: Exception) {
                // silently fail
            }
        }
    }

    // ==================== Preview / Text Editor ====================

    fun openPreview(file: FileItem) {
        addRecent(file)
        _state.value = _state.value.copy(screen = Screen.PREVIEW, previewItem = file)
    }

    fun openTextEditor(file: FileItem) {
        addRecent(file)
        _state.value = _state.value.copy(screen = Screen.TEXT_EDITOR, textEditorItem = file)
    }

    fun saveTextFile(file: FileItem, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(file.path).writeText(content)
            refresh()
        }
    }

    // ==================== Storage Analyzer ====================

    fun openStorageAnalyzer() {
        _state.value = _state.value.copy(screen = Screen.STORAGE_ANALYZER)
        if (_state.value.storageInfo == null) scanStorage()
    }

    fun scanStorage() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(storageScanning = true)
            val info = storageAnalyzer.analyze(INTERNAL_STORAGE_ROOT) { _, _ -> }
            _state.value = _state.value.copy(storageInfo = info, storageScanning = false)
        }
    }

    // ==================== Duplicate Finder ====================

    fun openDuplicateFinder() {
        _state.value = _state.value.copy(screen = Screen.DUP_FINDER)
    }

    fun scanDuplicates(path: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                duplicateScanning = true,
                duplicateGroups = emptyList(),
                selectedDuplicates = emptySet()
            )
            val groups = duplicateFinder.findDuplicates(path) { _, _ -> }
            _state.value = _state.value.copy(duplicateGroups = groups, duplicateScanning = false)
        }
    }

    fun toggleDuplicateSelection(path: String) {
        val set = _state.value.selectedDuplicates.toMutableSet()
        if (!set.add(path)) set.remove(path)
        _state.value = _state.value.copy(selectedDuplicates = set)
    }

    fun deleteSelectedDuplicates() {
        val paths = _state.value.selectedDuplicates.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { File(it).delete() }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(selectedDuplicates = emptySet())
                scanDuplicates(_state.value.currentPath)
            }
        }
    }

    // ==================== App Manager ====================

    fun openAppManager() {
        _state.value = _state.value.copy(screen = Screen.APP_MANAGER)
        if (_state.value.installedApps.isEmpty()) loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _state.value = _state.value.copy(appsLoading = true)
            val apps = appManager.getInstalledApps()
            _state.value = _state.value.copy(installedApps = apps, appsLoading = false)
        }
    }

    fun openApp(pkg: String) = appManager.openApp(pkg)
    fun openAppInfo(pkg: String) = appManager.openAppInfo(pkg)
    fun uninstallApp(pkg: String) = appManager.uninstall(pkg)

    fun backupApp(app: AppInfo) {
        viewModelScope.launch {
            appManager.backupApk(app, _state.value.currentPath)
        }
    }

    // ==================== Root ====================

    private fun checkRoot() {
        viewModelScope.launch {
            val available = RootHelper.isRootAvailable()
            _state.value = _state.value.copy(rootAvailable = available, rootEnabled = available)
        }
    }

    fun toggleRoot(enabled: Boolean) {
        _state.value = _state.value.copy(rootEnabled = enabled)
        if (!enabled) RootHelper.resetCache()
    }

    fun recheckRoot() {
        RootHelper.resetCache()
        checkRoot()
    }

    fun setScreen(screen: Screen) {
        _state.value = _state.value.copy(screen = screen)
    }

    // ==================== USB OTG ====================

    fun loadUsbVolumes() {
        viewModelScope.launch {
            val volumes = usbOtgManager.getStorageVolumes()
            _state.value = _state.value.copy(usbVolumes = volumes)
        }
    }

    fun openUsbVolume(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                screen = Screen.USB_OTG,
                currentUsbUri = uri,
                usbLoading = true,
                usbFiles = emptyList()
            )
            val files = usbOtgManager.listDocumentFiles(uri)
            _state.value = _state.value.copy(
                usbFiles = files,
                usbLoading = false,
                currentUsbPath = uri.toString()
            )
        }
    }

    fun navigateUsbDirectory(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(usbLoading = true)
            val files = usbOtgManager.listDocumentFiles(uri)
            _state.value = _state.value.copy(
                usbFiles = files,
                usbLoading = false,
                currentUsbPath = uri.toString()
            )
        }
    }

    fun closeUsbMode() {
        _state.value = _state.value.copy(
            screen = Screen.FILES,
            currentUsbPath = null,
            currentUsbUri = null,
            usbFiles = emptyList()
        )
    }

    fun copyFileToUsb(sourcePath: String, destParentUri: Uri) {
        viewModelScope.launch {
            val sourceUri = Uri.fromFile(File(sourcePath))
            usbOtgManager.copyDocumentFile(sourceUri, destParentUri)
            _state.value.currentUsbUri?.let { navigateUsbDirectory(it) }
        }
    }

    // ==================== Tabs ====================

    fun switchTab(tabId: Int) {
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        _state.value = _state.value.copy(activeTabId = tabId)
        loadDirectory(tab.path)
    }

    fun addTab() {
        val id = nextTabId++
        val newTab = TabState(id, INTERNAL_STORAGE_ROOT)
        _state.value = _state.value.copy(
            tabs = _state.value.tabs + newTab,
            activeTabId = id
        )
        loadDirectory(INTERNAL_STORAGE_ROOT)
    }

    fun closeTab(tabId: Int) {
        val s = _state.value
        if (s.tabs.size <= 1) return
        val remaining = s.tabs.filter { it.id != tabId }
        val newActiveId = if (s.activeTabId == tabId) {
            remaining.firstOrNull()?.id ?: remaining.first().id
        } else s.activeTabId
        _state.value = s.copy(tabs = remaining, activeTabId = newActiveId)
        val newTab = remaining.first { it.id == newActiveId }
        loadDirectory(newTab.path)
    }

    // ==================== Dual Pane ====================

    fun toggleDualPane() {
        val enabled = !_state.value.isDualPane
        _state.value = _state.value.copy(isDualPane = enabled)
        if (enabled) loadSecondPane(_state.value.secondPanePath)
    }

    fun loadSecondPane(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) return@launch
                val files = dir.listFiles()?.map { it.toFileItem() }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        secondPanePath = path,
                        secondPaneFiles = files,
                        secondPaneSelected = emptySet()
                    )
                }
            } catch (e: Exception) { }
        }
    }

    fun navigateSecondPane(folder: FileItem) {
        if (folder.isDirectory) loadSecondPane(folder.path)
    }

    fun navigateSecondPaneUp() {
        val cur = File(_state.value.secondPanePath)
        val parent = cur.parentFile
        if (parent != null) loadSecondPane(parent.absolutePath)
    }

    fun toggleSecondPaneSelection(file: FileItem) {
        val set = _state.value.secondPaneSelected.toMutableSet()
        if (!set.add(file.path)) set.remove(file.path)
        _state.value = _state.value.copy(secondPaneSelected = set)
    }

    fun copyFromPane1ToPane2() {
        val src = _state.value.selectedFiles.toList()
        if (src.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            src.forEach { path ->
                val srcFile = File(path)
                val dst = File(_state.value.secondPanePath, srcFile.name)
                if (srcFile.exists()) copyRecursive(srcFile, dst)
            }
            withContext(Dispatchers.Main) {
                clearSelection()
                loadSecondPane(_state.value.secondPanePath)
            }
        }
    }

    fun copyFromPane2ToPane1() {
        val src = _state.value.secondPaneSelected.toList()
        if (src.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            src.forEach { path ->
                val srcFile = File(path)
                val dst = File(_state.value.currentPath, srcFile.name)
                if (srcFile.exists()) copyRecursive(srcFile, dst)
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(secondPaneSelected = emptySet())
                refresh()
            }
        }
    }

    // ==================== Favorites / Recent ====================

    fun addFavorite(item: FileItem) {
        prefsManager.addFavorite(item)
        loadPrefs()
    }

    fun removeFavorite(path: String) {
        prefsManager.removeFavorite(path)
        loadPrefs()
    }

    fun toggleFavorite(item: FileItem) {
        prefsManager.toggleFavorite(item)
        loadPrefs()
    }

    fun isFavorite(path: String): Boolean = _state.value.favorites.any { it.path == path }

    fun addRecent(item: FileItem) {
        prefsManager.addRecent(item)
        loadPrefs()
    }

    fun clearRecent() {
        prefsManager.clearRecent()
        loadPrefs()
    }

    fun removeRecent(path: String) {
        prefsManager.removeRecent(path)
        loadPrefs()
    }

    fun openFavorites() {
        _state.value = _state.value.copy(screen = Screen.FAVORITES, favorites = prefsManager.getFavorites())
    }

    fun openRecent() {
        _state.value = _state.value.copy(screen = Screen.RECENT, recent = prefsManager.getRecent())
    }

    fun openFromList(item: FileItem) {
        addRecent(item)
        if (item.isDirectory) {
            _state.value = _state.value.copy(screen = Screen.FILES)
            loadDirectory(item.path)
        } else if (item.isArchive()) {
            openArchivePreview(item)
        } else if (item.isImage() || item.isText()) {
            openPreview(item)
        } else {
            _state.value = _state.value.copy(screen = Screen.FILES)
        }
    }

    // ==================== FTP ====================

    fun connectFtp(server: FtpServer) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                ftpState = FtpConnectionState(
                    host = server.host, port = server.port, user = server.user,
                    connected = false, loading = true
                )
            )
            val ok = ftpManager.connect(server.host, server.port, server.user, server.password)
            if (ok) {
                prefsManager.saveFtpServer(server)
                loadPrefs()
                val files = ftpManager.listFiles("/")
                _state.value = _state.value.copy(
                    ftpState = FtpConnectionState(
                        host = server.host, port = server.port, user = server.user,
                        currentPath = "/", connected = true, files = files, loading = false
                    )
                )
            } else {
                _state.value = _state.value.copy(
                    ftpState = FtpConnectionState(
                        host = server.host, port = server.port, user = server.user,
                        connected = false, loading = false,
                        error = "เชื่อมต่อไม่สำเร็จ"
                    )
                )
            }
        }
    }

    fun disconnectFtp() {
        viewModelScope.launch {
            ftpManager.disconnect()
            _state.value = _state.value.copy(ftpState = FtpConnectionState())
        }
    }

    fun ftpNavigate(folder: FileItem) {
        if (!folder.isDirectory) return
        viewModelScope.launch {
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(loading = true))
            val files = ftpManager.listFiles(folder.path)
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(
                currentPath = folder.path, files = files, loading = false
            ))
        }
    }

    fun ftpNavigateUp() {
        val cur = _state.value.ftpState.currentPath
        if (cur == "/" || cur.isBlank()) return
        val parent = cur.substringBeforeLast('/', "/").ifBlank { "/" }
        viewModelScope.launch {
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(loading = true))
            val files = ftpManager.listFiles(parent)
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(
                currentPath = parent, files = files, loading = false
            ))
        }
    }

    fun ftpDownload(item: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val localFile = File(INTERNAL_STORAGE_ROOT, item.name)
            ftpManager.download(item.path, localFile)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun ftpDelete(item: FileItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(loading = true))
            ftpManager.delete(item.path)
            val files = ftpManager.listFiles(_state.value.ftpState.currentPath)
            _state.value = _state.value.copy(ftpState = _state.value.ftpState.copy(files = files, loading = false))
        }
    }

    fun saveFtpServer(server: FtpServer) {
        prefsManager.saveFtpServer(server)
        loadPrefs()
    }

    fun removeFtpServer(host: String, port: Int) {
        prefsManager.removeFtpServer(host, port)
        loadPrefs()
    }

    fun openFtp() {
        _state.value = _state.value.copy(screen = Screen.FTP, ftpServers = prefsManager.getFtpServers())
    }

    fun openAbout() {
        _state.value = _state.value.copy(screen = Screen.ABOUT)
    }

    fun openLicenses() {
        _state.value = _state.value.copy(screen = Screen.LICENSES)
    }
}
