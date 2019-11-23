package com.example.miband.Bluetooth.Actions;

import android.bluetooth.BluetoothGatt;
import android.content.Context;

import com.example.miband.Device.MiBandDevice;

public class SetDeviceBusyAction extends BtLEAction {
    private final MiBandDevice device;
    private final Context context;
    private final String busyTask;

    /**
     * When run, will mark the device as busy (or not busy).
     *
     * @param device   the device to mark
     * @param busyTask the task name to set as busy task, or null to mark as not busy
     * @param context
     */
    public SetDeviceBusyAction(MiBandDevice device, String busyTask, Context context) {
        super(null);

        this.device = device;
        this.busyTask = busyTask;
        this.context = context;
    }

    @Override
    public boolean expectsResult() {
        return false;
    }

    @Override
    public boolean run(BluetoothGatt gatt) {
        device.setBusyTask(busyTask);
        device.sendDeviceUpdateIntent(context);
        return true;
    }

    @Override
    public String toString() {
        return getCreationTime() + ": " + getClass().getName() + ": " + busyTask;
    }
}
