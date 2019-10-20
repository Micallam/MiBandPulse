package com.example.miband;

import android.bluetooth.BluetoothAdapter;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class PairingActivity extends AppCompatActivity {
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
                Log.d(MainActivity.TAG, "pairing activity: device changed: " + device);
                if (PairingActivity.this.device.getAddress().equals(device.getAddress())) {
                    if (device.isInitialized()) {
                        pairingFinished(true, PairingActivity.this.device);
                    } else if (device.isConnecting() || device.isInitializing()) {
                        Log.d(MainActivity.TAG, "still connecting/initializing device...");
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
                Log.d(MainActivity.TAG, "Bond state changed: " + device + ", state: " + device.getBondState() + ", expected address: " + bondingAddress);
                if (bondingAddress != null && bondingAddress.equals(device.getAddress())) {
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        Log.d(MainActivity.TAG, "Bonded with " + device.getAddress());
                        bondingAddress = null;
                        attemptToConnect();
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        Log.d(MainActivity.TAG, "Bonding in progress with " + device.getAddress());
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        Log.d(MainActivity.TAG, "Not bonded with " + device.getAddress() + ", attempting to connect anyway.");
                        bondingAddress = null;
                        attemptToConnect();
                    } else {
                        Log.d(MainActivity.TAG, "Unknown bond state for device " + device.getAddress() + ": " + bondState);
                        pairingFinished(false, PairingActivity.this.device);
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
        device = intent.getParcelableExtra(MiBandDevice.EXTRA_DEVICE_CANDIDATE);
        if (device == null && savedInstanceState != null) {
            device = savedInstanceState.getParcelable(STATE_DEVICE_CANDIDATE);
        }
        if (device == null) {
            Toast.makeText(this, "No MAC address passed, cannot pair.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, DiscoveryActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
/*
        if (coordinator.getSupportedDeviceSpecificSettings(device) != null) { // FIXME: this will no longer be sane in the future
            SharedPreferences sharedPrefs = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
            String authKey = sharedPrefs.getString("authkey", null);
            if (authKey == null || authKey.isEmpty()) {
                SharedPreferences.Editor editor = sharedPrefs.edit();

                String randomAuthkey = RandomStringUtils.random(16, true, true);
                editor.putString("authkey", randomAuthkey);
                editor.apply();
            }
        }

        if (!MiBandCoordinator.hasValidUserInfo()) {
            Intent userSettingsIntent = new Intent(this, MiBandPreferencesActivity.class);
            startActivityForResult(userSettingsIntent, REQ_CODE_USER_SETTINGS, null);
            return;
        }*/

        // already valid user info available, use that and pair
        startPairing();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
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
        message.setText("Pairing with " + device + "…");

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

    private void pairingFinished(boolean pairedSuccessfully, MiBandDevice candidate) {
        Log.d(MainActivity.TAG, "pairingFinished: " + pairedSuccessfully);
        if (!isPairing) {
            return;
        }

        isPairing = false;
        AndroidUtils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(this), mPairingReceiver);
        AndroidUtils.safeUnregisterBroadcastReceiver(this, mBondingReceiver);

        if (pairedSuccessfully) {
            // remember the device since we do not necessarily pair... temporary -- we probably need
            // to query the db for available devices in ControlCenter. But only remember un-bonded
            // devices, as bonded devices are displayed anyway.
            String deviceAddress = device.getAddress();
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
           /*TODO next activity and find sth more about Prefs class
            if (device != null && device.getBondState() == BluetoothDevice.BOND_NONE) {
                Prefs prefs = GBApplication.getPrefs();
                prefs.getPreferences().edit().putString(MiBandConst.PREF_MIBAND_ADDRESS, deviceAddress).apply();
            }
            Intent intent = new Intent(this, ControlCenterv2.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);*/
        }
        finish();
    }

    private void stopPairing() {
        // TODO
        isPairing = false;
    }

    protected void performBluetoothPair(MiBandDevice deviceCandidate) {
        BluetoothDevice device = deviceCandidate.getDevice();

        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            AndroidUtils.toast(this, "Already bonded with " + device.getName() + " (" + device.getAddress() + "), connecting…", Toast.LENGTH_SHORT);
            performApplicationLevelPair();
            return;
        }

        bondingAddress = device.getAddress();
        if (bondState == BluetoothDevice.BOND_BONDING) {
            AndroidUtils.toast(this, "Bonding in progress: " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
            return;
        }

        AndroidUtils.toast(this, "Creating bond with: " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
        if (!device.createBond()) {
            AndroidUtils.toast(this, "Unable to bond with: " + device.getName() + " (" + bondingAddress + ")", Toast.LENGTH_LONG);
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
