package com.parkhub.printerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.parkhub.printerapp.ui.theme.PrinterAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrinterAppTheme {
                RequirePrinterPermissions {
                    ConnectPrinterScreen()
                }
            }
        }
    }
}
