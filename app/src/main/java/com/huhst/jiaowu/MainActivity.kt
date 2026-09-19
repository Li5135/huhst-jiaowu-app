package com.huhst.jiaowu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.huhst.jiaowu.ui.JiaowuRoot
import com.huhst.jiaowu.ui.theme.JiaowuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as JiaowuApp).container
        setContent {
            JiaowuTheme {
                JiaowuRoot(container)
            }
        }
    }
}
