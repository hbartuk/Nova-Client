package com.radiantbyte.novaclient.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import com.radiantbyte.novaclient.navigation.Navigation
import com.radiantbyte.novaclient.ui.theme.NovaClientTheme
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalFoundationApi::class)
    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkBatteryOptimizations()
        setContent {
            NovaClientTheme {
                Navigation()
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }
    }
}