package com.swipesort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                when (vm.screen) {
                    Screen.SETUP -> SetupScreen(vm)
                    Screen.SWIPE -> SwipeScreen(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions (e.g. coming back from the "media management" settings page)
        vm.onPermissionResult()
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current)
    else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
