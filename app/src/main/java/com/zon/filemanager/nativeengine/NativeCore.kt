package com.zon.filemanager.nativeengine

import java.io.File

object NativeCore {
    init {
        runCatching {
            System.loadLibrary("zonengine")
        }.onFailure {
            it.printStackTrace()
        }
    }

    external fun getEngineVersion(): String
    external fun getFileSizeNative(filePath: String): Long
    external fun copyFileNative(srcPath: String, destPath: String): Boolean

    fun getFileSize(file: File): Long {
        return if (file.exists()) {
            getFileSizeNative(file.absolutePath)
        } else {
            -1L
        }
    }

    fun copyFile(src: File, dest: File): Boolean {
        if (!src.exists()) return false
        return copyFileNative(src.absolutePath, dest.absolutePath)
    }
}
