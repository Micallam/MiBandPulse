package com.example.miband;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Collection;
import java.util.Collections;

public class MiBandDevice implements Parcelable {
    public static final String ACTION_DEVICE_CHANGED = "com.example.mibanddevice.action.device_changed";
    public static final String EXTRA_DEVICE_CANDIDATE = "com.example.mibanddevice.EXTRA_DEVICE_CANDIDATE";

    public static final short RSSI_UNKNOWN = 0;
    public static final String EXTRA_DEVICE = "device";
    private String mName;
    private final String mAddress;
    private State mState = State.NOT_CONNECTED;

    private BluetoothDevice mBluetoothDevice;

    public MiBandDevice(BluetoothDevice device, String address){
        mBluetoothDevice = device;
        mAddress = address;
        mName = device.getName();
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getAddress(){
        return mAddress;
    }

    public BluetoothDevice getDevice(){
        return mBluetoothDevice;
    }
    @NonNull
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Collection<? extends ScanFilter> createBLEScanFilters() {
        ParcelUuid mi2Service = new ParcelUuid(MiBandService.UUID_SERVICE_MIBAND2_SERVICE);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(mi2Service).build();
        return Collections.singletonList(filter);
    }

    public boolean isInitialized() {
        return mState.ordinal() >= State.INITIALIZED.ordinal();
    }

    public boolean isConnecting() {
        return mState == State.CONNECTING;
    }

    public boolean isConnected() {
        return mState.ordinal() >= State.CONNECTED.ordinal();
    }

    public boolean isInitializing() {
        return mState == State.INITIALIZING;
    }

    public enum State {
        NOT_CONNECTED,
        WAITING_FOR_RECONNECT,
        CONNECTING,
        CONNECTED,
        INITIALIZING,
        AUTHENTICATION_REQUIRED,
        AUTHENTICATING,
        INITIALIZED,
    }

    protected MiBandDevice(Parcel in) {
        mName = in.readString();
        mAddress = in.readString();
        mState = (State) in.readValue(State.class.getClassLoader());
        mBluetoothDevice = (BluetoothDevice) in.readValue(BluetoothDevice.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mAddress);
        dest.writeValue(mState);
        dest.writeValue(mBluetoothDevice);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<MiBandDevice> CREATOR = new Parcelable.Creator<MiBandDevice>() {
        @Override
        public MiBandDevice createFromParcel(Parcel in) {
            return new MiBandDevice(in);
        }

        @Override
        public MiBandDevice[] newArray(int size) {
            return new MiBandDevice[size];
        }
    };


    public void sendDeviceUpdateIntent(Context context) {
        Intent deviceUpdateIntent = new Intent(ACTION_DEVICE_CHANGED);
        deviceUpdateIntent.putExtra(EXTRA_DEVICE, this);
        LocalBroadcastManager.getInstance(context).sendBroadcast(deviceUpdateIntent);
    }


    public void setState(State state) {
        mState = state;
    }

    public State getState(){
        return mState;
    }
}