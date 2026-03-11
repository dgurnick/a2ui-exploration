package com.dgurnick.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dgurnick.android.BuildConfig
import com.dgurnick.android.ui.theme.A2uiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: A2uiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.init(BuildConfig.BFF_BASE_URL)
        setContent {
            A2uiTheme {
                A2uiApp(viewModel = viewModel)
            }
        }
    }
}
