package com.claude.chat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.claude.chat.platform.initFileStorage
import com.claude.chat.presentation.ui.App

/**
 * Main Android activity
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize FileStorage with application context
        initFileStorage(applicationContext)

        setContent {
            App()
        }
    }
}
