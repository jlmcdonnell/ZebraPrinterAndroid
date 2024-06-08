package com.parkhub.printerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun RequirePrinterPermissions(onGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions: Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }


    var bluetoothGranted by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }

    val launchBluetoothPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { resultMap ->
            bluetoothGranted = resultMap.all { (permission, result) ->
                println("$permission -> $result")
                result
            }
        },
    )

    val launchLocationPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { result ->
            locationGranted = result
        },
    )

    LaunchedEffect(bluetoothGranted) {
        if (!bluetoothGranted) {
            if (
                permissions.all { permission ->
                    val result = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    result.also { println("$permission -> $result") }
                }
            ) {
                bluetoothGranted = true
            } else {
                launchBluetoothPermissionRequest.launch(permissions)
            }
        }
    }

    LaunchedEffect(bluetoothGranted, locationGranted) {
        if (bluetoothGranted && !locationGranted) {
            launchLocationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (bluetoothGranted && locationGranted) {
        onGranted()
    }
}
