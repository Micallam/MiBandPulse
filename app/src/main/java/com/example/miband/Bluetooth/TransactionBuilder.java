package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.miband.Bluetooth.Actions.BtLEAction;
import com.example.miband.Bluetooth.Actions.NotifyAction;
import com.example.miband.Bluetooth.Actions.ReadAction;
import com.example.miband.Bluetooth.Actions.WriteAction;
import com.example.miband.Device.MiBandSupport;

public class TransactionBuilder {

    public static String TAG = "MiBand: TransactionBuilder";
    private final Transaction mTransaction;
    private boolean mQueued;

    public TransactionBuilder(String taskName) {
        mTransaction = new Transaction(taskName);
    }

    public TransactionBuilder read(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            Log.d(TransactionBuilder.TAG, "Unable to read characteristic: null");
            return this;
        }
        ReadAction action = new ReadAction(characteristic);
        return add(action);
    }

    public TransactionBuilder write(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic == null) {
            Log.d(TransactionBuilder.TAG, "Unable to write characteristic: null");
            return this;
        }
        WriteAction action = new WriteAction(characteristic, data);
        return add(action);
    }

    public TransactionBuilder notify(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (characteristic == null) {
            Log.d(TransactionBuilder.TAG, "Unable to notify characteristic: null");
            return this;
        }
        NotifyAction action = createNotifyAction(characteristic, enable);
        return add(action);
    }

    protected NotifyAction createNotifyAction(BluetoothGattCharacteristic characteristic, boolean enable) {
        return new NotifyAction(characteristic, enable);
    }

    public TransactionBuilder add(BtLEAction action) {
        mTransaction.add(action);
        return this;
    }

    /**
     * Sets a GattCallback instance that will be called when the transaction is executed,
     * resulting in GattCallback events.
     *
     * @param callback the callback to set, may be null
     */
    public void setGattCallback(@Nullable BluetoothGattCallback callback) {
        mTransaction.setGattCallback(callback);
    }

    public
    @Nullable
    BluetoothGattCallback getGattCallback() {
        return mTransaction.getGattCallback();
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

    public String getTaskName() {
        return mTransaction.getTaskName();
    }
}
