package com.zon.filemanager.nativeengine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

fun interface NativeProgressCallback {
    fun onProgress(bytesProcessed: Long, totalBytes: Long, percent: Float)
}

object NativeCore {
    init {
        runCatching {
            System.loadLibrary("zonengine")
        }.onFailure {
            it.printStackTrace()
        }
    }

    external fun getEngineVersion(): String
    external fun cancelCurrentOperation()
    private external fun extractZipFdNative(
        srcFd: Int,
        destDirPath: String,
        callback: NativeProgressCallback
    ): Boolean

    fun extractZipSaf(
        context: Context,
        zipUri: Uri,
        destDir: File,
        callback: NativeProgressCallback
    ): Boolean {
        if (!destDir.exists()) destDir.mkdirs()

        // SAF Bypass: เปิด File Descriptor ในโหมด Read-Only ("r") เพื่อความปลอดภัยของไฟล์ Zip ต้นทาง
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(zipUri, "r")
            ?: return false

        return try {
            val fd = pfd.fd
            extractZipFdNative(
                srcFd = fd,
                destDirPath = destDir.absolutePath,
                callback = callback
            )
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pfd.close()
        }
    }
}
