package com.masterproef.model

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 *  Represents the Gatt-service of the same name
 *  Holds the Gatt-service objects that represent the Gatt-service in the Android BLE API
 *  Defines the behaviour of the Gatt-service
 * */
class HRVService(peripheralManager: BluetoothPeripheralManager, context: Context) : BaseService(peripheralManager), SensorEventListener {

    // Objects represent the service and the characteristics within the Android Bluetooth Low Energy API
    // ppIntervalCharacteristic can be subscribed to
    private val service = BluetoothGattService(Identifiers.HRV_SERVICE_UUID, SERVICE_TYPE_PRIMARY)
    private val ppIntervalCharacteristic = BluetoothGattCharacteristic(Identifiers.PP_INTERVAL_CHARACTERISTIC_UUID, PROPERTY_INDICATE, 0)

    // Creates object for the virtual peak-to-peak interval sensor
    private val sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor = sensorManager.getDefaultSensor(65547, true)

    // Indicates if the sensor is currently active
    private var ppNotifying: Boolean = false

    init {
        ppIntervalCharacteristic.addDescriptor(getCccDescriptor())
        service.addCharacteristic(ppIntervalCharacteristic)
    }

    /*  Gets called by the distributor, PeripheralManagerCallback, when a device subscribes to a characteristic of the Gatt-service
    *   Uses the characteristic-UUID to check if the device subscribed to the ppIntervalCharacteristic
    *   If the sensor was already active and notifying, nothing happens
    *   If it wasn't already active, it gets activated
    * */
    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == Identifiers.PP_INTERVAL_CHARACTERISTIC_UUID && !ppNotifying) {
            startNotifying()
        }
    }

    // Gets called by the distributor, PeripheralManagerCallback, when a device stops its subscription to a characteristic of the Gatt-service
    // The sensor gets deactivated if it's currently active and no other device is subscribed
    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == Identifiers.PP_INTERVAL_CHARACTERISTIC_UUID && ppNotifying && noCentralsWantingNotifications(characteristic)) {
            stopNotifying()
        }
    }

    // Gets called by the distributor, PeripheralManagerCallback, when a device disconnects
    // The sensor gets deactivated if it's currently active and no other device is subscribed
    // onNotifyingDisabled isn't called when a device disconnects so this seperate function is necessary
    override fun onCentralDisconnected(central: BluetoothCentral) {
        if (ppNotifying && noCentralsWantingNotifications(ppIntervalCharacteristic)) {
            stopNotifying()
        }
    }

    // Activates the sensor
    private fun startNotifying() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        ppNotifying = true
    }

    // Disables the sensor
    private fun stopNotifying() {
        sensorManager.unregisterListener(this)
        ppNotifying = false
    }

    // Gets called when the sensor measures a new value
    // This value is sent to all subscribed devices
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == sensor.type) {
            val buffer = ByteBuffer.allocate(12) //8 bytes for timestamp [long], 4 bytes for value [float]
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(event.timestamp)
            buffer.putFloat(event.values[0])

            notifyCharacteristicChanged(buffer.array(), ppIntervalCharacteristic)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, int: Int) {
        return
    }

    // Returns the object that represents the Gatt-service within the Android BLE API
    override fun getService(): BluetoothGattService {
        return service
    }

    override fun getServiceName(): String {
        return "HRV Service"
    }
}