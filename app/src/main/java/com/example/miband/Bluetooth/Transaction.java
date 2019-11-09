package com.example.miband.Bluetooth;

import androidx.annotation.Nullable;

import com.example.miband.Bluetooth.Actions.BtLEAction;
import com.example.miband.Bluetooth.Gatt.GattCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Transaction {
    private final List<BtLEAction> mActions = new ArrayList<>(4);
    private
    @Nullable
    GattCallback gattCallback;

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

    public void setGattCallback(@Nullable GattCallback callback) {
        gattCallback = callback;
    }

    /**
     * Returns the GattCallback for this transaction, or null if none.
     */
    public
    @Nullable
    GattCallback getGattCallback() {
        return gattCallback;
    }

    public int getActionCount() {
        return mActions.size();
    }

}
