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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ZonTheme lives in this same package (com.zon.filemanager), so no import needed.
            ZonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()

                    when (state.currentScreen) {
                        Screen.HOME, Screen.FILES -> FileManagerScreen(viewModel = viewModel)
                        Screen.FAVORITES -> FavoritesScreen(viewModel = viewModel)
                        Screen.RECENT -> RecentScreen(viewModel = viewModel)
                        Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                        Screen.ABOUT -> AboutScreen(viewModel = viewModel)
                        Screen.LICENSES -> LicensesScreen(viewModel = viewModel)
                        Screen.PREVIEW -> PreviewScreen(viewModel = viewModel)
                        Screen.TEXT_EDITOR -> TextEditorScreen(viewModel = viewModel)
                        Screen.USB_OTG -> UsbOtgScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
