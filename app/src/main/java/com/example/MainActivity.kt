package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.firebase.FirebaseManager
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ShopViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ShopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseManager.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
