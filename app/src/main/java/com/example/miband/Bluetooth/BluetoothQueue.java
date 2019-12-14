package com.example.miband.Bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.miband.Bluetooth.Actions.AbstractGattListenerWriteAction;
import com.example.miband.Bluetooth.Actions.BtLEAction;
import com.example.miband.Device.MiBandDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothQueue {

    public static final String TAG = "MiBand: BluetoothQueue";

    private final Object mGattMonitor = new Object();
    private final MiBandDevice mDevice;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private final BlockingQueue<Transaction> mTransactions = new LinkedBlockingQueue<>();
    private volatile boolean mCrashed;
    private volatile boolean mAbortTransaction;

    private final Context mContext;
    private CountDownLatch mWaitForActionResultLatch;
    private CountDownLatch mConnectionLatch;
    private BluetoothGattCharacteristic mWaitCharacteristic;
    private final InternalGattCallback internalGattCallback;

    public BluetoothQueue(BluetoothAdapter bluetoothAdapter, MiBandDevice device, BluetoothGattCallback externalGattCallback, Context context) {
        mBluetoothAdapter = bluetoothAdapter;
        mDevice = device;
        internalGattCallback = new InternalGattCallback(externalGattCallback);
        mContext = context;

        // Run all actions of the transaction until one doesn't succeed
        // got disconnected
        // this special action overwrites the transaction gatt listener (if any), it must
        // always be the last action in the transaction
        // check again, maybe due to some condition, action did not need to write, so we can't wait
        // abort the transaction
        Thread dispatchThread = new Thread("GATT Dispatcher") {

            @Override
            public void run() {
                Log.d(BluetoothQueue.TAG, "Queue Dispatch Thread started.");

                while (!mCrashed) {
                    try {
                        Transaction qTransaction = mTransactions.take();

                        if (!mDevice.isConnected()) {
                            Log.d(BluetoothQueue.TAG, "not connected, waiting for connection...");
                            internalGattCallback.reset();

                            mConnectionLatch = new CountDownLatch(1);
                            mConnectionLatch.await();
                            mConnectionLatch = null;
                        }

                        if (qTransaction != null) {
                            internalGattCallback.setTransactionGattCallback(qTransaction.getGattCallback());
                            mAbortTransaction = false;
                            // Run all actions of the transaction until one doesn't succeed
                            for (BtLEAction action : qTransaction.getActions()) {
                                if (mAbortTransaction) { // got disconnected
                                    Log.d(BluetoothQueue.TAG, "Aborting running transaction");
                                    break;
                                }
                                mWaitCharacteristic = action.getCharacteristic();
                                mWaitForActionResultLatch = new CountDownLatch(1);

                                Log.d(BluetoothQueue.TAG, "About to run action: " + action);

                                if (action instanceof AbstractGattListenerWriteAction) {
                                    // this special action overwrites the transaction gatt listener (if any), it must
                                    // always be the last action in the transaction
                                    internalGattCallback.setTransactionGattCallback(((AbstractGattListenerWriteAction) action).getGattCallback());
                                }

                                if (action.run(mBluetoothGatt)) {
                                    // check again, maybe due to some condition, action did not need to write
                                    boolean waitForResult = action.expectsResult();
                                    if (waitForResult) {
                                        mWaitForActionResultLatch.await();
                                        mWaitForActionResultLatch = null;
                                        if (mAbortTransaction) {
                                            break;
                                        }
                                    }
                                } else {
                                    Log.d(BluetoothQueue.TAG, "Action returned false: " + action);
                                    break; // abort the transaction
                                }
                            }
                        }
                    } catch (InterruptedException ignored) {
                        mConnectionLatch = null;
                        Log.d(BluetoothQueue.TAG, "Thread interrupted");
                    } catch (Throwable ex) {
                        Log.d(BluetoothQueue.TAG, "Queue Dispatch Thread died: " + ex.getMessage(), ex);
                        mCrashed = true;
                        mConnectionLatch = null;
                    } finally {
                        mWaitForActionResultLatch = null;
                        mWaitCharacteristic = null;
                    }
                }
                Log.d(BluetoothQueue.TAG, "Queue Dispatch Thread terminated.");
            }
        };
        dispatchThread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean connect() {
        if (mDevice.isConnected()) {
            Log.d(BluetoothQueue.TAG, "Ingoring connect() because already connected.");
            return false;
        }
        synchronized (mGattMonitor) {
            if (mBluetoothGatt != null) {
                // Tribal knowledge says you're better off not reusing existing BluetoothGatt connections,
                // so create a new one.
                Log.d(BluetoothQueue.TAG, "connect() requested -- disconnecting previous connection: " + mDevice.getName());
                disconnect();
            }
        }
        Log.d(BluetoothQueue.TAG, "Attempting to connect to " + mDevice.getName());
        mBluetoothAdapter.cancelDiscovery();
        BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mDevice.getAddress());

        synchronized (mGattMonitor) {
            // connectGatt with true doesn't really work ;( too often connection problems

            mBluetoothGatt = remoteDevice.connectGatt(mContext, false, internalGattCallback, BluetoothDevice.TRANSPORT_LE);
        }
        boolean result = mBluetoothGatt != null;
        if (result) {
            setDeviceConnectionState(MiBandDevice.State.CONNECTING);
        }
        return result;
    }

    void add(Transaction transaction) {
        Log.d(BluetoothQueue.TAG, "about to add: " + transaction);
        if (transaction.hasElements()) {
            mTransactions.add(transaction);
        }
    }

    public void insert(Transaction transaction) {
        Log.d(BluetoothQueue.TAG, "about to insert: " + transaction);
        if (transaction.hasElements()) {
            List<Transaction> tail = new ArrayList<>(mTransactions.size() + 2);

            tail.addAll(mTransactions);
            mTransactions.clear();
            mTransactions.add(transaction);
            mTransactions.addAll(tail);
        }
    }

    private boolean checkCorrectGattInstance(BluetoothGatt gatt, String where) {
        if (gatt != mBluetoothGatt && mBluetoothGatt != null) {
            Log.d(BluetoothQueue.TAG, "Ignoring event from wrong BluetoothGatt instance: " + where + "; " + gatt);
            return true;
        }
        return false;
    }

    private void setDeviceConnectionState(MiBandDevice.State newState) {
        Log.d(BluetoothQueue.TAG, "new device connection state: " + newState);

        mDevice.setState(newState);
        mDevice.sendDeviceUpdateIntent(mContext);
        if (mConnectionLatch != null && newState == MiBandDevice.State.CONNECTED) {
            mConnectionLatch.countDown();
        }
    }

    private boolean reconnect() {
        if (mBluetoothGatt != null) {
            Log.d(BluetoothQueue.TAG, "Enabling automatic ble reconnect...");
            boolean result = mBluetoothGatt.connect();
            if (result) {
                setDeviceConnectionState(MiBandDevice.State.WAITING_FOR_RECONNECT);
            }
            return result;
        }
        return false;
    }

    private void disconnect() {
        synchronized (mGattMonitor) {
            Log.d(BluetoothQueue.TAG, "disconnect()");
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                mBluetoothGatt = null;
                Log.d(BluetoothQueue.TAG, "Disconnecting BluetoothQueue from GATT device");
                gatt.disconnect();
                gatt.close();
                setDeviceConnectionState(MiBandDevice.State.NOT_CONNECTED);
            }
        }
    }

    private void handleDisconnected(int status) {
        Log.d(BluetoothQueue.TAG, "handleDisconnected: " + status);
        internalGattCallback.reset();
        mTransactions.clear();
        mAbortTransaction = true;
        if (mWaitForActionResultLatch != null) {
            mWaitForActionResultLatch.countDown();
        }

        boolean wasInitialized = mDevice.isInitialized();

        setDeviceConnectionState(MiBandDevice.State.NOT_CONNECTED);

        // either we've been disconnected because the device is out of range
        // or because of an explicit @{link #disconnect())
        // To support automatic reconnection, we keep the mBluetoothGatt instance
        // alive (we do not close() it). Unfortunately we sometimes have problems
        // reconnecting automatically, so we try to fix this by re-creating mBluetoothGatt.
        // Not sure if this actually works without re-initializing the device...
        if (mBluetoothGatt != null) {
            if (!wasInitialized || !reconnect()) {
                disconnect(); // ensure that we start over cleanly next time
            }
        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final class InternalGattCallback extends BluetoothGattCallback {
        private
        @Nullable
        BluetoothGattCallback mTransactionGattCallback;
        private final BluetoothGattCallback mExternalGattCallback;

        InternalGattCallback(BluetoothGattCallback externalGattCallback) {
            mExternalGattCallback = externalGattCallback;
        }

        void setTransactionGattCallback(@Nullable BluetoothGattCallback callback) {
            mTransactionGattCallback = callback;
        }

        private BluetoothGattCallback getCallbackToUse() {
            if (mTransactionGattCallback != null) {
                return mTransactionGattCallback;
            }
            return mExternalGattCallback;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(BluetoothQueue.TAG, "connection state change, newState: " + newState + getStatusString(status));

            synchronized (mGattMonitor) {
                if (mBluetoothGatt == null) {
                    mBluetoothGatt = gatt;
                }
            }

            if (checkCorrectGattInstance(gatt, "connection state event")) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(BluetoothQueue.TAG, "connection state event with error status " + status);
            }

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(BluetoothQueue.TAG, "Connected to GATT server.");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTED);
                    // Attempts to discover services after successful connection.
                    List<BluetoothGattService> cachedServices = gatt.getServices();
                    if (cachedServices != null && cachedServices.size() > 0) {
                        Log.d(BluetoothQueue.TAG, "Using cached services, skipping discovery");
                        onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS);
                    } else {
                        Log.d(BluetoothQueue.TAG, "Attempting to start service discovery");
                        // discover services in the main thread (appears to fix Samsung connection problems)
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (mBluetoothGatt != null) {
                                    mBluetoothGatt.discoverServices();
                                }
                            }
                        });
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(BluetoothQueue.TAG, "Disconnected from GATT server.");
                    handleDisconnected(status);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    Log.d(BluetoothQueue.TAG, "Connecting to GATT server...");
                    setDeviceConnectionState(MiBandDevice.State.CONNECTING);
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(BluetoothQueue.TAG, "On services discovered...");
            if (checkCorrectGattInstance(gatt, "services discovered: " + getStatusString(status))) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (getCallbackToUse() != null) {
                    // only propagate the successful event
                    getCallbackToUse().onServicesDiscovered(gatt, status);
                }
            } else {
                Log.d(BluetoothQueue.TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(BluetoothQueue.TAG, "characteristic write: " + characteristic.getUuid() + getStatusString(status));
            if (checkCorrectGattInstance(gatt, "characteristic write")) {
                return;
            }
            if (getCallbackToUse() != null) {
                getCallbackToUse().onCharacteristicWrite(gatt, characteristic, status);
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(BluetoothQueue.TAG, "characteristic read: " + characteristic.getUuid() + getStatusString(status));
            if (checkCorrectGattInstance(gatt, "characteristic read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicRead(gatt, characteristic, status);
                } catch (Throwable ex) {
                    Log.d(BluetoothQueue.TAG, "onCharacteristicRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(BluetoothQueue.TAG, "descriptor read: " + descriptor.getUuid() + getStatusString(status));
            if (checkCorrectGattInstance(gatt, "descriptor read")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorRead(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(BluetoothQueue.TAG, "onDescriptorRead: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(BluetoothQueue.TAG, "descriptor write: " + descriptor.getUuid() + getStatusString(status));
            if (checkCorrectGattInstance(gatt, "descriptor write")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onDescriptorWrite(gatt, descriptor, status);
                } catch (Throwable ex) {
                    Log.d(BluetoothQueue.TAG, "onDescriptorWrite: " + ex.getMessage(), ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            Log.d(BluetoothQueue.TAG, "characteristic changed: " + characteristic.getUuid() + " value: " + Arrays.toString(characteristic.getValue()));

            if (checkCorrectGattInstance(gatt, "characteristic changed")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onCharacteristicChanged(gatt, characteristic);
                } catch (Throwable ex) {
                    Log.d(BluetoothQueue.TAG, "onCharaceristicChanged: " + ex.getMessage(), ex);
                }
            } else {
                Log.d(BluetoothQueue.TAG, "No gattcallback registered, ignoring characteristic change");
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(BluetoothQueue.TAG, "remote rssi: " + rssi + getStatusString(status));
            if (checkCorrectGattInstance(gatt, "remote rssi")) {
                return;
            }
            if (getCallbackToUse() != null) {
                try {
                    getCallbackToUse().onReadRemoteRssi(gatt, rssi, status);
                } catch (Throwable ex) {
                    Log.d(BluetoothQueue.TAG, "onReadRemoteRssi: " + ex.getMessage(), ex);
                }
            }
        }

        private void checkWaitingCharacteristic(BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (characteristic != null) {
                    Log.d(BluetoothQueue.TAG, "failed btle action, aborting transaction: " + characteristic.getUuid() + getStatusString(status));
                }
                mAbortTransaction = true;
            }
            if (characteristic != null && BluetoothQueue.this.mWaitCharacteristic != null && characteristic.getUuid().equals(BluetoothQueue.this.mWaitCharacteristic.getUuid())) {
                if (mWaitForActionResultLatch != null) {
                    mWaitForActionResultLatch.countDown();
                }
            } else {
                if (BluetoothQueue.this.mWaitCharacteristic != null) {
                    Log.d(BluetoothQueue.TAG, "checkWaitingCharacteristic: mismatched characteristic received: " + ((characteristic != null && characteristic.getUuid() != null) ? characteristic.getUuid().toString() : "(null)"));
                }
            }
        }

        private String getStatusString(int status) {
            return status == BluetoothGatt.GATT_SUCCESS ? " (success)" : " (failed: " + status + ")";
        }

        void reset() {
            Log.d(BluetoothQueue.TAG, "internal gatt callback set to null");

            mTransactionGattCallback = null;
        }
    }
}
