package com.masterproef.shared

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/*
 *  Represents the Gatt-service of the same name
 *  Holds the Gatt-service objects that represent the Gatt-service in the Android BLE API
 *  Defines the behaviour of the Gatt-service
 * */
class IdentificationService(peripheralManager: BluetoothPeripheralManager, private val serviceScope: CoroutineScope) : BaseService(peripheralManager) {

    // Objects represent the service and the characteristics within the Android Bluetooth Low Energy API
    // deviceID can be read, ultrasonicDetected can be subscribed to
    private val service = BluetoothGattService(Identifiers.IDENTIFICATION_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
    private val deviceIdCharacteristic = BluetoothGattCharacteristic(Identifiers.DEVICE_ID_CHARACTERISTIC_UUID, PROPERTY_READ, PERMISSION_READ)
    private val ultrasonicDetectedCharacteristic = BluetoothGattCharacteristic(Identifiers.ULTRASONIC_DETECTED_CHARACTERISTIC_UUID, PROPERTY_INDICATE, 0)

    // Contains the current value of deviceId, saved as type ByteArray so it's ready to be sent
    private lateinit var deviceId: ByteArray

    // An Ultrasonicdetection-job gets created every time a device connects, to listen for the ultrasonic pattern
    // These job-objects are kept in this dictionary by device-address
    private val jobs = mutableMapOf<String, Job>()

    init {
        setDeviceId(-1)
        service.addCharacteristic(deviceIdCharacteristic)

        ultrasonicDetectedCharacteristic.addDescriptor(getCccDescriptor())
        service.addCharacteristic(ultrasonicDetectedCharacteristic)
    }

    // Gets called by the distributor, PeripheralManagerCallback, when a characteristic of the Gatt-service was read
    // Uses the characteristic-UUID to check which one was read and return a ReadResponse-object
    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        if (characteristic.uuid == Identifiers.DEVICE_ID_CHARACTERISTIC_UUID) {
            return ReadResponse(GattStatus.SUCCESS, deviceId)
        }

        return super.onCharacteristicRead(central, characteristic)
    }

    /*
    *   Gets called by the distributor, PeripheralManagerCallback, when a device connects
    *
    *   A new coroutine is started and added to the list of jobs, this makes sure that the main thread and UI isn't affected
    *   A new ultrasonicdetector-object is created and this object activates the mic to start listening for the pattern
    *
    *   All connected devices get notified if the pattern was detected or not
    *   once that pattern gets detected or once the timer runs out
    * */
    override fun onCentralConnected(central: BluetoothCentral) {
        jobs[central.address] = serviceScope.launch(Dispatchers.Default) {
            val detected = UltrasonicDetector().analyzeAudio()

            val byteBuffer = ByteBuffer.allocate(1).put(if (detected) 1.toByte() else 0.toByte())
            val detectedByteArray = byteBuffer.array()

            notifyCharacteristicChanged(detectedByteArray, ultrasonicDetectedCharacteristic)
        }
    }

    /*
    *   Gets called by the distributor, PeripheralManagerCallback, when a device disconnects
    *   The coroutine of that connected device is retrieved from the list of jobs and gets cancelled
    *   This makes sure that that coroutine stops listening and searching for the pattern
    * */
    override fun onCentralDisconnected(central: BluetoothCentral) {
        jobs[central.address]?.cancel()
        jobs.remove(central.address)
    }

    // Converts and saves deviceId as a bytearray so it's ready to be sent
    fun setDeviceId(deviceId: Int){
        val byteBuffer = ByteBuffer.allocate(4).putInt(deviceId)
        this.deviceId = byteBuffer.array()
    }

    // Returns the object that represents the Gatt-service within the Android BLE API
    override fun getService(): BluetoothGattService {
        return service
    }

    override fun getServiceName(): String {
        return "Identification Service"
    }
}