package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.example.miband.Bluetooth.Actions.BtLEAction;

public class ReadAction extends BtLEAction {

    public ReadAction(BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public boolean run(BluetoothGatt gatt) {
        int properties = getCharacteristic().getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            return gatt.readCharacteristic(getCharacteristic());
        }
        return false;
    }

    @Override
    public boolean expectsResult() {
        return true;
    }
}
