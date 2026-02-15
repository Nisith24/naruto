package com.example.telegramlistener.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }
    }

    fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(onResult: (Boolean) -> Unit) {
        if (hasPermissions()) {
            onResult(true)
            return
        }
        
        // Simplified request logic for "senior" dev speed - in real app, handle rationale
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            onResult(allGranted)
        }
        
        launcher.launch(requiredPermissions.toTypedArray())
    }
}
