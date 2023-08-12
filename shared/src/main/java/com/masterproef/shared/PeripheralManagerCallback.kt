package com.masterproef.shared

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManagerCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.*
import java.util.UUID

/*
*   Distributor for the self-defined Gatt-service classes, event or request gets passed to the
*   right object using the UUID
* */
class PeripheralManagerCallback(private var serviceImplementations: Map<UUID, BaseService>): BluetoothPeripheralManagerCallback() {

    override fun onCharacteristicRead(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic): ReadResponse {
        val serviceImplementation: BaseService? = serviceImplementations[characteristic.service.uuid]

        if (serviceImplementation != null) {
             return serviceImplementation.onCharacteristicRead(central, characteristic)
        } else {
            return super.onCharacteristicRead(central, characteristic)
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        val serviceImplementation: BaseService? = serviceImplementations[characteristic.service.uuid]

        if (serviceImplementation != null) {
            return serviceImplementation.onCharacteristicWrite(central, characteristic, value)
        } else {
            return GattStatus.REQUEST_NOT_SUPPORTED
        }
    }

    override fun onCharacteristicWriteCompleted(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        serviceImplementations[characteristic.service.uuid]?.onCharacteristicWriteCompleted(central, characteristic, value)
    }

    override fun onDescriptorRead(central: BluetoothCentral, descriptor: BluetoothGattDescriptor): ReadResponse {
        val characteristic: BluetoothGattCharacteristic = descriptor.characteristic ?: throw NullPointerException("Descriptor has no Characteristic")
        val service: BluetoothGattService = characteristic.service ?: throw NullPointerException("Characteristic has no Service")
        val serviceImplementation: BaseService? = serviceImplementations[service.uuid]

        if (serviceImplementation != null) {
            return serviceImplementation.onDescriptorRead(central, descriptor)
        } else {
            return super.onDescriptorRead(central, descriptor)
        }
    }

    override fun onDescriptorWrite(central: BluetoothCentral, descriptor: BluetoothGattDescriptor, value: ByteArray): GattStatus {
        val characteristic: BluetoothGattCharacteristic = descriptor.characteristic ?: throw NullPointerException("Descriptor has no Characteristic")
        val service: BluetoothGattService = characteristic.service ?: throw NullPointerException("Characteristic has no Service")
        val serviceImplementation: BaseService? = serviceImplementations[service.uuid]

        if (serviceImplementation != null) {
            return serviceImplementation.onDescriptorWrite(central, descriptor, value)
        } else {
            return GattStatus.REQUEST_NOT_SUPPORTED
        }
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        serviceImplementations[characteristic.service.uuid]?.onNotifyingEnabled(central, characteristic)
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        serviceImplementations[characteristic.service.uuid]?.onNotifyingDisabled(central, characteristic)
    }

    override fun onNotificationSent(central: BluetoothCentral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: GattStatus) {
        serviceImplementations[characteristic.service.uuid]?.onNotificationSent(central, value, characteristic, status)
    }

    override fun onCentralConnected(central: BluetoothCentral) {
        for (serviceImplementation in serviceImplementations.values) {
            serviceImplementation.onCentralConnected(central)
        }
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        for (serviceImplementation in serviceImplementations.values) {
            serviceImplementation.onCentralDisconnected(central)
        }
    }
}