package com.example.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.countdown.theme.CountdownTheme
import com.example.countdown.theme.White
import com.example.countdown.ui.CountdownScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            CountdownTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = White,
                ) {
                    CountdownScreen()
                }
            }
        }
    }
}
