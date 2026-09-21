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

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRecentManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("zon_prefs", Context.MODE_PRIVATE)

    // ==================== Favorites ====================
    fun getFavorites(): List<FileItem> {
        val raw = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        return parseItems(raw)
    }

    fun addFavorite(item: FileItem) {
        val list = getFavorites().filter { it.path != item.path }.toMutableList()
        list.add(0, item)
        saveItems(KEY_FAVORITES, list)
    }

    fun removeFavorite(path: String) {
        val list = getFavorites().filter { it.path != path }
        saveItems(KEY_FAVORITES, list)
    }

    fun isFavorite(path: String): Boolean = getFavorites().any { it.path == path }

    fun toggleFavorite(item: FileItem): Boolean {
        return if (isFavorite(item.path)) {
            removeFavorite(item.path)
            false
        } else {
            addFavorite(item)
            true
        }
    }

    // ==================== Recent ====================
    fun getRecent(): List<FileItem> {
        val raw = prefs.getString(KEY_RECENT, "[]") ?: "[]"
        return parseItems(raw)
    }

    fun addRecent(item: FileItem) {
        val list = getRecent().filter { it.path != item.path }.toMutableList()
        list.add(0, item)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        saveItems(KEY_RECENT, list)
    }

    fun clearRecent() {
        prefs.edit().putString(KEY_RECENT, "[]").apply()
    }

    fun removeRecent(path: String) {
        val list = getRecent().filter { it.path != path }
        saveItems(KEY_RECENT, list)
    }

    // ==================== Helpers ====================
    private fun parseItems(raw: String): List<FileItem> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("path", "")
                val name = o.optString("name", "")
                val isDir = o.optBoolean("isDir", false)
                val size = o.optLong("size", 0L)
                val lastModified = o.optLong("lastModified", 0L)
                if (path.isBlank()) null
                else FileItem(name, path, isDir, size, lastModified)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveItems(key: String, items: List<FileItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val o = JSONObject()
            o.put("name", item.name)
            o.put("path", item.path)
            o.put("isDir", item.isDirectory)
            o.put("size", item.size)
            o.put("lastModified", item.lastModified)
            arr.put(o)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // ==================== FTP History ====================
    fun getFtpServers(): List<FtpServer> {
        val raw = prefs.getString(KEY_FTP, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                FtpServer(
                    host = o.optString("host", ""),
                    port = o.optInt("port", 21),
                    user = o.optString("user", "anonymous"),
                    password = o.optString("password", ""),
                    name = o.optString("name", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveFtpServer(server: FtpServer) {
        val list = getFtpServers().filter { it.host != server.host || it.port != server.port }.toMutableList()
        list.add(0, server)
        while (list.size > MAX_FTP) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("host", s.host); o.put("port", s.port)
            o.put("user", s.user); o.put("password", s.password)
            o.put("name", s.name)
            arr.put(o)
        }
        prefs.edit().putString(KEY_FTP, arr.toString()).apply()
    }

    fun removeFtpServer(host: String, port: Int) {
        val list = getFtpServers().filter { !(it.host == host && it.port == port) }
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("host", s.host); o.put("port", s.port)
            o.put("user", s.user); o.put("password", s.password)
            o.put("name", s.name)
            arr.put(o)
        }
        prefs.edit().putString(KEY_FTP, arr.toString()).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent"
        private const val KEY_FTP = "ftp_servers"
        private const val MAX_RECENT = 50
        private const val MAX_FTP = 20
    }
}

data class FtpServer(
    val host: String,
    val port: Int = 21,
    val user: String = "anonymous",
    val password: String = "",
    val name: String = ""
)
