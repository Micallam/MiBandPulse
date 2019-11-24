package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.content.Context;

import androidx.annotation.NonNull;

import com.example.miband.Device.MiBandDevice;

public class SetDeviceStateAction extends BtLEAction {
    private final MiBandDevice device;
    private final MiBandDevice.State deviceState;
    private final Context context;

    public SetDeviceStateAction(MiBandDevice device, MiBandDevice.State deviceState, Context context) {
        super (null);

        this.device = device;
        this.deviceState = deviceState;
        this.context = context;
    }

    @Override
    public boolean expectsResult() {
        return false;
    }

    @Override
    public boolean run(BluetoothGatt gatt) {
        device.setState(deviceState);
        device.sendDeviceUpdateIntent(getContext());
        return true;
    }

    public Context getContext() {
        return context;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " to " + deviceState;
    }

}
