package com.masterproef.shared

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.*
import androidx.core.app.NotificationCompat
import com.welie.blessed.*
import java.util.UUID

/*
*   Represents the foregroundservice that provides the context and lifecycle used by the whole model
*   Stays active, even when the app gets closed
*   Only one object of this class can exist at the same time
* */
class BluetoothForegroundService : android.app.Service() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var peripheralManager: BluetoothPeripheralManager

    private var servicesByUUID: MutableMap<UUID, BaseService> = mutableMapOf()

    /*
    *   Runs only if it's the first foregroundservice created by a particular intent
    *   i.e. If a user presses the start/apply button multiple times, this function will only be
    *   called the first time. The function will only be called again if the button is pressed after
    *   closing and opening the app
    *
    *   Creates the permanent notification, sets up the Gatt-services and starts advertising
    * */
    override fun onCreate() {
        super.onCreate()

        // Add permanent notification
        val notificationChannel = NotificationChannel(
            BluetoothForegroundService::class.java.simpleName,
            "Background operations",
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationService = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, BluetoothForegroundService::class.java.simpleName)
                .setContentTitle("Masterproef Wearable")
                .setContentText("Service is running in the background")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).build()

        // Start foregroundservice on the permanent notification
        // The hardcoded id ensures that only 1 instance of this service can exist at one time

        startForeground(4929, notification)


        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        peripheralManager = BluetoothPeripheralManager(this, bluetoothManager, PeripheralManagerCallback(servicesByUUID))
        peripheralManager.removeAllServices()

        // Create identification-service object
        val identificationService = IdentificationService(peripheralManager)
        servicesByUUID[Identifiers.IDENTIFICATION_SERVICE_UUID] = identificationService

        // Create HRV-service object if the device has a heartratesensor
        try {
            val hrvService = HRVService(peripheralManager, this)
            servicesByUUID[Identifiers.HRV_SERVICE_UUID] = hrvService
        } catch (_: Exception) {}

        setupServices()
        startAdvertising()
    }

    /*
    *   Runs right after onCreate() but does run every time the foregroundservice is created,
    *   no matter if the intent was already used before
    *   i.e. The function runs every time the button is pressed
    *
    *   This function has access to the intent that created the service so only
    *   here can the deviceId be set
    * */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Set deviceId in the identification-service
        val deviceId: Int = intent?.getIntExtra("deviceId", -1) ?: -1
        (servicesByUUID[Identifiers.IDENTIFICATION_SERVICE_UUID] as IdentificationService).setDeviceId(deviceId)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        peripheralManager.close()
    }

    /*
    *   All Gatt-service objects of the Android API are extracted from the Gatt-service objects
    *   defined in this codebase. These objects are added to the peripheralmanager to create
    *   the Gatt-services within the Bluetooth Low Energy API of Android
    * */
    private fun setupServices() {
        for (service in servicesByUUID.values) {
            peripheralManager.add(service.getService())
        }
    }

    private fun startAdvertising() {
        val advertiseSettings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true).setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()

        val advertiseData = AdvertiseData.Builder().addServiceUuid(ParcelUuid(Identifiers.IDENTIFICATION_SERVICE_UUID)).build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true).build()

        peripheralManager.startAdvertising(advertiseSettings, advertiseData, scanResponse)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}