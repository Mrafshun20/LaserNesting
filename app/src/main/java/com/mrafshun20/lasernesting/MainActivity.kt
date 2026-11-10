package com.mrafshun20.lasernesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrafshun20.lasernesting.ui.NestingViewModel
import com.mrafshun20.lasernesting.ui.PartsScreen
import com.mrafshun20.lasernesting.ui.ResultsScreen
import com.mrafshun20.lasernesting.ui.SetupScreen
import com.mrafshun20.lasernesting.ui.theme.LaserNestingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaserNestingTheme {
                val viewModel: NestingViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory
                        .getInstance(application)
                )
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "Laser Nesting",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Bystronic sheet nesting",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(Icons.Filled.List, contentDescription = "Parts")
                                },
                                label = { Text("Parts") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(Icons.Filled.Settings, contentDescription = "Setup")
                                },
                                label = { Text("Setup") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(Icons.Filled.GridView, contentDescription = "Nest")
                                },
                                label = { Text("Nest") }
                            )
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding)) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 4 }) togetherWith
                                    (fadeOut() + slideOutVertically { -it / 4 })
                            },
                            label = "tab"
                        ) { tab ->
                            when (tab) {
                                0 -> PartsScreen(viewModel)
                                1 -> SetupScreen(viewModel)
                                2 -> ResultsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
