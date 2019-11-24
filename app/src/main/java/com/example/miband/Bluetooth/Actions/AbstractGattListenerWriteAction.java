package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;

public abstract class AbstractGattListenerWriteAction extends WriteAction {

    public AbstractGattListenerWriteAction(BluetoothGattCharacteristic characteristic, byte[] value) {
        super(characteristic, value);
    }

    public BluetoothGattCallback getGattCallback() {
        return new BluetoothGattCallback() {
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                AbstractGattListenerWriteAction.this.onCharacteristicChanged(gatt, characteristic);
            }
        };
    }

    abstract void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
}
