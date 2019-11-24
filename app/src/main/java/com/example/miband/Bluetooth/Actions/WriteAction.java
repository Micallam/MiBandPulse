package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.util.Arrays;

public class WriteAction extends BtLEAction {
    public static String TAG = "MiBand: WriteAction";
    private final byte[] value;

    public WriteAction(BluetoothGattCharacteristic characteristic, byte[] value) {
        super(characteristic);
        this.value = value;
    }

    @Override
    public boolean run(BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic = getCharacteristic();
        int properties = characteristic.getProperties();

        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0 || ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0)) {
            return writeValue(gatt, characteristic, value);
        }
        return false;
    }

    private boolean writeValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
        Log.d(WriteAction.TAG, "writing to characteristic: " + characteristic.getUuid() + ": " + Arrays.toString(value));

        if (characteristic.setValue(value)) {
            return gatt.writeCharacteristic(characteristic);
        }
        return false;
    }

    @Override
    public boolean expectsResult() {
        return true;
    }
}
