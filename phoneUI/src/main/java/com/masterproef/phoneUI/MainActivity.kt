package com.masterproef.phoneUI

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.masterproef.shared.BluetoothForegroundService

class MainActivity : ComponentActivity() {

    // The same intent is used, every time a user pushes the button
    // This makes sure that the onCreate function of the foregroundservice gets called only once
    private lateinit var startServiceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneApp(activity = this) }

        startServiceIntent =  Intent(this, BluetoothForegroundService::class.java)
    }

    fun startAdvertising(deviceId: Int) {
        if (!checkPermissions()){
            askPermissions()
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // Does device support bluetooth-advertising? If not, toast
        if (bluetoothManager.adapter == null || !bluetoothManager.adapter.isMultipleAdvertisementSupported) {
            Toast.makeText(this, "Device not supported", Toast.LENGTH_SHORT).show()
            return
        }

        // Is bluetooth-adapter turned on? If not, ask to turn on
        if (!bluetoothManager.adapter.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            // Listens for the answer, proceeds if positive, toasts if negative
            val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            startAdvertisingService(deviceId)
                        } else {
                            Toast.makeText(this, "Bluetooth-adapter needs to be turned on.", Toast.LENGTH_SHORT).show()
                        }
            }

            resultLauncher.launch(enableBtIntent)

        }else{ startAdvertisingService(deviceId) }
    }

    private fun checkPermissions(): Boolean {
        val permissionChecks = mutableListOf(
            ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK),
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        )

        // Only check these permissions for newer versions of Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionChecks.add(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT))
            permissionChecks.add(ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE))
            permissionChecks.add(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE))

        // Only check these permissions for older versions of Android
        } else {
            permissionChecks.add(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN))
        }

        for (el in permissionChecks) {
            if (el != PERMISSION_GRANTED) { return false }
        }

        return true
    }

    private fun askPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECORD_AUDIO
        )

        // Only ask for these permissions in newer versions of Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)

        // Only ask for these permissions in older versions of Android
        } else {
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
    }

    // Starts a foregroundservice and passes it the deviceId in the intent
    private fun startAdvertisingService(deviceId: Int){
        startServiceIntent.putExtra("deviceId", deviceId)
        startForegroundService(startServiceIntent)
    }
}

@Composable
fun PhoneApp(activity: MainActivity) {
    var deviceId by remember { mutableStateOf(1) }

    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

            Text(text = "device id:  $deviceId", softWrap = false, style = TextStyle(fontWeight = FontWeight.Bold))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {

                Button(onClick = { if (deviceId > 1) { deviceId-- } }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray), modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(text = "-", style = TextStyle(fontWeight = FontWeight.Bold))
                }

                Button(onClick = { deviceId++ }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray), modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(text = "+", style = TextStyle(fontWeight = FontWeight.Bold))
                }
            }

            Button(onClick = { activity.startAdvertising(deviceId) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)) {
                Text(text = "Start / Apply", style = TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White))
            }
        }
    }
}