package com.mrafshun20.lasernesting

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrafshun20.lasernesting.R
import com.mrafshun20.lasernesting.ui.NestingViewModel
import com.mrafshun20.lasernesting.ui.PartsScreen
import com.mrafshun20.lasernesting.ui.ResultsScreen
import com.mrafshun20.lasernesting.ui.SetupScreen
import com.mrafshun20.lasernesting.ui.theme.LaserNestingTheme

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.app_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { selectedTab = 2 }) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.action_settings)
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
                                    Icon(Icons.Filled.List, contentDescription = stringResource(R.string.tab_parts))
                                },
                                label = { Text(stringResource(R.string.tab_parts)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.tab_nest))
                                },
                                label = { Text(stringResource(R.string.tab_nest)) }
                            )
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding)) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { fullHeight: Int -> fullHeight / 4 }) togetherWith
                                    (fadeOut() + slideOutVertically { fullHeight: Int -> -fullHeight / 4 })
                            },
                            label = "tab"
                        ) { tab ->
                            when (tab) {
                                0 -> PartsScreen(viewModel)
                                1 -> ResultsScreen(viewModel)
                                else -> SetupScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
