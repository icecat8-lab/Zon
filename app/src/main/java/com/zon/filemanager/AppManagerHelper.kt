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

// app/src/main/java/com/zon/filemanager/AppManagerHelper.kt
package com.zon.filemanager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val size: Long,
    val icon: Drawable?,
    val apkPath: String
)

class AppManagerHelper(private val context: Context) {

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps.mapNotNull { app ->
            try {
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val apkPath = app.sourceDir ?: return@mapNotNull null
                val apkFile = File(apkPath)

                val versionNameStr: String = try {
                    val info = pm.getPackageInfo(app.packageName, 0)
                    info.versionName ?: ""
                } catch (e: Exception) {
                    ""
                }

                val versionCodeLong: Long = try {
                    val info = pm.getPackageInfo(app.packageName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode.toLong()
                    }
                } catch (e: Exception) {
                    0L
                }

                AppInfo(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    versionName = versionNameStr,
                    versionCode = versionCodeLong,
                    isSystem = isSystem,
                    size = apkFile.length(),
                    icon = try { pm.getApplicationIcon(app.packageName) } catch (e: Exception) { null },
                    apkPath = apkPath
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun backupApk(app: AppInfo, destDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(app.apkPath)
            val dst = File(destDir, "${app.appName}_${app.versionName}.apk")
            src.copyTo(dst, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
