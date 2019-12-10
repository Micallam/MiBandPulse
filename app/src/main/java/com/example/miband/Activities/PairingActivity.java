package com.example.miband.Activities;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.miband.Device.MiBandDevice;
import com.example.miband.Device.MiBandService;
import com.example.miband.R;
import com.example.miband.Utils.AndroidUtils;

public class PairingActivity extends AppCompatActivity {
    public static String TAG = "MiBand: PairingActivity";

    private static final int REQ_CODE_USER_SETTINGS = 52;
    private static final String STATE_DEVICE_CANDIDATE = "stateDeviceCandidate";
    private static final long DELAY_AFTER_BONDING = 1000; // 1s
    private TextView message;
    private boolean isPairing;
    private MiBandDevice device;
    private String bondingAddress;

    private final BroadcastReceiver mPairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MiBandDevice.ACTION_DEVICE_CHANGED.equals(intent.getAction())) {
                MiBandDevice device = intent.getParcelableExtra(MiBandDevice.EXTRA_DEVICE);
                Log.d(PairingActivity.TAG, "pairing activity: device changed: " + device.getName() + " " + device.getAddress() + " state: " + device.getState());
                if (PairingActivity.this.device.getAddress().equals(device.getAddress())) {
                    if (device.isInitialized()) {
                        pairingFinished(true);
                    } else if (device.isConnecting() || device.isInitializing()) {
                        Log.d(PairingActivity.TAG, "still connecting/initializing device...");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mBondingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(PairingActivity.TAG, "Bond state changed: " + device + ", state: " + device.getBondState() + ", expected address: " + bondingAddress);
                if (bondingAddress != null && bondingAddress.equals(device.getAddress())) {
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        Log.d(PairingActivity.TAG, "Bonded with " + device.getAddress());
                        bondingAddress = null;
                        attemptToConnect();
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        Log.d(PairingActivity.TAG, "Bonding in progress with " + device.getAddress());
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        Log.d(PairingActivity.TAG, "Not bonded with " + device.getAddress() + ", attempting to connect anyway.");
                        bondingAddress = null;
                        attemptToConnect();
                    } else {
                        Log.d(PairingActivity.TAG, "Unknown bond state for device " + device.getAddress() + ": " + bondState);
                        pairingFinished(false);
                    }
                }
            }
        }
    };

    private void attemptToConnect() {
        Looper mainLooper = Looper.getMainLooper();
        new Handler(mainLooper).postDelayed(new Runnable() {
            @Override
            public void run() {
                performApplicationLevelPair();
            }
        }, DELAY_AFTER_BONDING);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mi_band_pairing);

        message = findViewById(R.id.miband_pair_message);
        Intent intent = getIntent();
        Bundle bundle = intent.getBundleExtra("bundle");
        device = bundle.getParcelable(MiBandDevice.EXTRA_DEVICE_CANDIDATE);
        if (device == null && savedInstanceState != null) {
            device = savedInstanceState.getParcelable(STATE_DEVICE_CANDIDATE);
        }
        if (device == null) {
            Toast.makeText(this, "No MAC address passed, cannot pair.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, DiscoveryActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }

        startPairing();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_DEVICE_CANDIDATE, device);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        device = savedInstanceState.getParcelable(STATE_DEVICE_CANDIDATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // start pairing immediately when we return from the user settings
        if (requestCode == REQ_CODE_USER_SETTINGS) {
            startPairing();
        }
    }

    @Override
    protected void onDestroy() {
        // just to be sure, remove the receivers -- might actually be already unregistered
        AndroidUtils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(this), mPairingReceiver);
        AndroidUtils.safeUnregisterBroadcastReceiver(this, mBondingReceiver);
        if (isPairing) {
            stopPairing();
        }
        super.onDestroy();
    }

    private void startPairing() {
        isPairing = true;
        message.setText(getString(R.string.pairing_with, device.getName(), device.getAddress()));

        IntentFilter filter = new IntentFilter(MiBandDevice.ACTION_DEVICE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mPairingReceiver, filter);

        if (!shouldSetupBTLevelPairing()) {
            // there are connection problems on certain Galaxy S devices at least;
            // try to connect without BT pairing (bonding)
            attemptToConnect();
            return;
        }

        filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBondingReceiver, filter);

        performBluetoothPair(device);
    }

    private boolean shouldSetupBTLevelPairing() {
        return true;
    }

    private void pairingFinished(boolean pairedSuccessfully) {
        Log.d(PairingActivity.TAG, "pairingFinished: " + pairedSuccessfully);
        if (!isPairing) {
            return;
        }

        isPairing = false;
        AndroidUtils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(this), mPairingReceiver);
        AndroidUtils.safeUnregisterBroadcastReceiver(this, mBondingReceiver);

        if (pairedSuccessfully) {
            Intent intent = new Intent(PairingActivity.this, DeviceControlActivity.class);

            Bundle bundle = new Bundle();
            bundle.putParcelable(MiBandDevice.EXTRA_DEVICE, device);

            intent.putExtra("bundle", bundle);
            startActivity(intent);
        }
        finish();
    }

    private void stopPairing() {
        isPairing = false;
    }

    protected void performBluetoothPair(MiBandDevice deviceCandidate) {
        BluetoothDevice device = deviceCandidate.getDevice();

        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            AndroidUtils.toast(this, "Już powiązano z " + device.getName() + " (" + device.getAddress() + "), łączenie…", Toast.LENGTH_SHORT);
            performApplicationLevelPair();
            return;
        }

        bondingAddress = device.getAddress();
        if (bondState == BluetoothDevice.BOND_BONDING) {
            AndroidUtils.toast(this, "W trakcie wiązania z " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
            return;
        }

        AndroidUtils.toast(this, "Tworzą wiązanie z: " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
        if (!device.createBond()) {
            AndroidUtils.toast(this, "Nie można powiązać z: " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
        }
    }

    private void performApplicationLevelPair() {
        MiBandService miBandService = new MiBandService(PairingActivity.this);
        miBandService.disconnect(); // just to make sure...

        if (device != null) {
            miBandService.connect(device, true);
        } else {
            AndroidUtils.toast(this, "Unable to connect, can't recognize the device type", Toast.LENGTH_LONG);
        }
    }
}
