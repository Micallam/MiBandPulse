package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGattCallback;

import androidx.annotation.Nullable;

import com.example.miband.Bluetooth.Actions.BtLEAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Transaction {
    private final List<BtLEAction> mActions = new ArrayList<>(4);
    private
    @Nullable
    BluetoothGattCallback gattCallback;

    void add(BtLEAction action) {
        mActions.add(action);
    }

    List<BtLEAction> getActions() {
        return Collections.unmodifiableList(mActions);
    }

    boolean hasElements() {
        return !mActions.isEmpty();
    }

    void setGattCallback(@Nullable BluetoothGattCallback callback) {
        gattCallback = callback;
    }

    /**
     * Returns the GattCallback for this transaction, or null if none.
     */
    @Nullable
    BluetoothGattCallback getGattCallback() {
        return gattCallback;
    }

}
