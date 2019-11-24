package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;

import com.example.miband.Device.MiBandService;

import java.util.Date;
import java.util.UUID;

public abstract class BtLEAction {
    static final UUID UUID_DESCRIPTOR_GATT_CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString((String.format(MiBandService.BASE_UUID, "2902")));

    private final BluetoothGattCharacteristic characteristic;
    private final long creationTimestamp;

    BtLEAction(BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
        creationTimestamp = System.currentTimeMillis();
    }

    /**
     * Returns true if this action expects an (async) result which must
     * be waited for, before continuing with other actions.
     * <p/>
     * This is needed because the current Bluedroid stack can only deal
     * with one single bluetooth operation at a time.
     */
    public abstract boolean expectsResult();

    /**
     * Executes this action, e.g. reads or write a GATT characteristic.
     *
     * @param gatt the characteristic to manipulate, or null if none.
     * @return true if the action was successful, false otherwise
     */
    public abstract boolean run(BluetoothGatt gatt);

    /**
     * Returns the GATT characteristic being read/written/...
     *
     * @return the GATT characteristic, or null
     */
    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    private String getCreationTime() {
        return new Date(creationTimestamp).toString();
    }

    @NonNull
    public String toString() {
        BluetoothGattCharacteristic characteristic = getCharacteristic();
        String uuid = characteristic == null ? "(null)" : characteristic.getUuid().toString();
        return getCreationTime() + ": " + getClass().getSimpleName() + " on characteristic: " + uuid;
    }
}


