package com.angel.vocalforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.angel.vocalforge.project.VocalForgeViewModel
import com.angel.vocalforge.ui.VocalForgeApp
import com.angel.vocalforge.ui.theme.VocalForgeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<VocalForgeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VocalForgeTheme { VocalForgeApp(viewModel) } }
    }
}
