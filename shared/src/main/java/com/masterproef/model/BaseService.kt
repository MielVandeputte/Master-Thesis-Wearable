package com.masterproef.model

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import com.welie.blessed.ReadResponse

abstract class BaseService(private val peripheralManager: BluetoothPeripheralManager) {

    // ----------------------------Callable functions----------------------------

    // Client Characteristic Configuration Descriptor
    // Descriptor is added to a characteristic if it's possible to subscribe to that characteristic
    fun getCccDescriptor(): BluetoothGattDescriptor {
        return BluetoothGattDescriptor(Identifiers.CCC_DESCRIPTOR_UUID, PERMISSION_READ or PERMISSION_WRITE)
    }

    // Characteristic User Description Descriptor
    // Descriptor can be added to a characteristic to provide a human-readable description
    fun getCudDescriptor(): BluetoothGattDescriptor {
        return BluetoothGattDescriptor(Identifiers.CUD_DESCRIPTOR_UUID, PERMISSION_READ or PERMISSION_WRITE)
    }

    // Notifies all devices, subscribed to a particular Gatt-characteristic
    // that the value has been updated to the passed in value
    fun notifyCharacteristicChanged(value: ByteArray, characteristic: BluetoothGattCharacteristic){
        peripheralManager.notifyCharacteristicChanged(value, characteristic)
    }

    // Checks if any devices are still connected
    // OnCentralDisconnected gets called before the device is actually disconnected
    // so this device needs to be neglected if the function is called from OnCentralDisconnected
    fun noCentralsConnectedExcept(central: BluetoothCentral): Boolean {
        for (per in peripheralManager.connectedCentrals) {
            if (per.address != central.address) { return false }
        }
        return true
    }

    // Checks if any devices in central mode are subscribed to a characteristic
    fun noCentralsWantingNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        return peripheralManager.getCentralsWantingNotifications(characteristic).size == 0
    }

    // ---------------------Mandatory to overwrite functions----------------------

    abstract fun getService(): BluetoothGattService

    abstract fun getServiceName(): String

    // ---------------------Optionally overridable functions---------------------

    open fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        return ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, null)
    }

    open fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        return GattStatus.WRITE_NOT_PERMITTED
    }

    open fun onCharacteristicWriteCompleted(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray) {}

    open fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse {
        return ReadResponse(GattStatus.REQUEST_NOT_SUPPORTED, null)
    }

    open fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        return GattStatus.WRITE_NOT_PERMITTED
    }

    open fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}

    open fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {}

    open fun onNotificationSent(central: BluetoothCentral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {}

    open fun onCentralConnected(central: BluetoothCentral) {}

    open fun onCentralDisconnected(central: BluetoothCentral) {}
}