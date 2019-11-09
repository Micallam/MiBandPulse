package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGattCallback;

import androidx.annotation.Nullable;

import com.example.miband.Bluetooth.Actions.BtLEAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Transaction {
    private final List<BtLEAction> mActions = new ArrayList<>(4);
    private
    @Nullable
    BluetoothGattCallback gattCallback;

    private final String mName;
    private final long creationTimestamp = System.currentTimeMillis();

    public String getTaskName() {
        return mName;
    }

    public Transaction(String taskName) {
        mName = taskName;
    }

    public void add(BtLEAction action) {
        mActions.add(action);
    }

    public List<BtLEAction> getActions() {
        return Collections.unmodifiableList(mActions);
    }

    public boolean isEmpty() {
        return mActions.isEmpty();
    }

    public void setGattCallback(@Nullable BluetoothGattCallback callback) {
        gattCallback = callback;
    }

    /**
     * Returns the GattCallback for this transaction, or null if none.
     */
    public
    @Nullable
    BluetoothGattCallback getGattCallback() {
        return gattCallback;
    }

    public int getActionCount() {
        return mActions.size();
    }

}
