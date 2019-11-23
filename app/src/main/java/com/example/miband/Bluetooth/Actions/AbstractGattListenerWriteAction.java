package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;

import com.example.miband.Bluetooth.BluetoothQueue;

import java.util.Objects;

public abstract class AbstractGattListenerWriteAction extends WriteAction {
    BluetoothQueue queue;

    public AbstractGattListenerWriteAction(BluetoothQueue queue, BluetoothGattCharacteristic characteristic, byte[] value) {
        super(characteristic, value);
        this.queue = queue;
        Objects.requireNonNull(queue, "queue must not be null");
    }

    public BluetoothGattCallback getGattCallback() {
        return new BluetoothGattCallback() {
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                AbstractGattListenerWriteAction.this.onCharacteristicChanged(gatt, characteristic);
            }
        };
    }

    protected abstract boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
}
