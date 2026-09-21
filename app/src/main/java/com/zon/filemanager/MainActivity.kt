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
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zon.filemanager.ui.theme.ZonTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "file_manager"
                    ) {
                        composable("file_manager") {
                            FileManagerScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }

                        composable(
                            route = "preview?path={path}",
                            arguments = listOf(navArgument("path") {
                                type = NavType.StringType
                                nullable = true
                            })
                        ) { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("path") ?: ""
                            PreviewScreen(
                                filePath = path,
                                navController = navController
                            )
                        }

                        composable(
                            route = "archive_preview?path={path}",
                            arguments = listOf(navArgument("path") {
                                type = NavType.StringType
                                nullable = true
                            })
                        ) { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("path") ?: ""
                            ArchivePreviewScreen(
                                filePath = path,
                                navController = navController
                            )
                        }

                        composable("settings") {
                            SettingsScreen(navController = navController)
                        }

                        composable("about") {
                            AboutScreen(navController = navController)
                        }

                        composable("favorites") {
                            FavoritesScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }

                        composable("recent") {
                            RecentScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
