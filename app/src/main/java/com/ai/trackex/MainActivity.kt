package com.ai.trackex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.ai.trackex.ui.navigation.AppNavigation
import com.ai.trackex.ui.theme.TrackExAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackExAiTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}