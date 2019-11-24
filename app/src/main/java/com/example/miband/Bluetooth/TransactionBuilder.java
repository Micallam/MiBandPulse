package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.miband.Bluetooth.Actions.BtLEAction;
import com.example.miband.Bluetooth.Actions.NotifyAction;
import com.example.miband.Bluetooth.Actions.WriteAction;

public class TransactionBuilder {

    public static String TAG = "MiBand: TransactionBuilder";
    private final Transaction mTransaction;
    private boolean mQueued;

    public TransactionBuilder() {
        mTransaction = new Transaction();
    }

    public void write(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic == null) {
            Log.d(TransactionBuilder.TAG, "Unable to write characteristic: null");
            return;
        }
        WriteAction action = new WriteAction(characteristic, data);
        add(action);
    }

    public void notify(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (characteristic == null) {
            Log.d(TransactionBuilder.TAG, "Unable to notify characteristic: null");
            return;
        }
        NotifyAction action = createNotifyAction(characteristic, enable);
        add(action);
    }

    private NotifyAction createNotifyAction(BluetoothGattCharacteristic characteristic, boolean enable) {
        return new NotifyAction(characteristic, enable);
    }

    public void add(BtLEAction action) {
        mTransaction.add(action);
    }

    void setGattCallback(@Nullable BluetoothGattCallback callback) {
        mTransaction.setGattCallback(callback);
    }

    public void queue(BluetoothQueue queue) {
        if (mQueued) {
            throw new IllegalStateException("This builder had already been queued. You must not reuse it.");
        }
        mQueued = true;
        queue.add(mTransaction);
    }

    public Transaction getTransaction() {
        return mTransaction;
    }
}
