package com.dgurnick.banking.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dgurnick.banking.BuildConfig
import com.dgurnick.banking.ui.theme.BankingTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BankingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.init(BuildConfig.BFF_BASE_URL)
        setContent {
            BankingTheme {
                BankingApp(viewModel = viewModel)
            }
        }
    }
}
