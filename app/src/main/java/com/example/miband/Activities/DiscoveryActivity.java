package com.example.miband.Activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.miband.Device.DeviceCandidateAdapter;
import com.example.miband.Device.MiBandDevice;
import com.example.miband.R;
import com.example.miband.Utils.AndroidUtils;

import java.util.ArrayList;
import java.util.Objects;

public class DiscoveryActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    public static String TAG = "MiBand: DiscoveryActivity";

    private static final long SCAN_DURATION = 60000; // 60s

    private final Handler handler = new Handler();

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    if (isScanning != Scanning.SCANNING_BTLE) {
                        discoveryStarted(Scanning.SCANNING_BT);
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // continue with LE scan, if available
                            if (isScanning == Scanning.SCANNING_BT) {
                                checkAndRequestLocationPermission();
                                startDiscovery(Scanning.SCANNING_BTLE);
                            } else {
                                discoveryFinished();
                            }
                        }
                    });
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                    bluetoothStateChanged(newState);
                    break;
                case BluetoothDevice.ACTION_FOUND:
                case BluetoothDevice.ACTION_UUID: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device.getName() != null) {
                        handleDeviceFound(device);
                    }

                    break;
                }
            }
        }
    };

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            logMessageContent(scanRecord);
            if (device.getName() != null){
                handleDeviceFound(device);
            }
        }
    };

    private void handleDeviceFound(BluetoothDevice device) {
        MiBandDevice candidate = new MiBandDevice(device, device.getAddress());

        int index = deviceCandidates.indexOf(candidate);
        if (index >= 0) {
            // replace existing device candidate
            deviceCandidates.set(index, candidate);
        } else {
            deviceCandidates.add(candidate);
        }
        candidateListAdapter.notifyDataSetChanged();
    }

    public void logMessageContent(byte[] value) {
        if (value != null) {
            Log.d(DiscoveryActivity.TAG, "DATA: " + AndroidUtils.hexdump(value, 0, value.length));
        }
    }

    private final Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopDiscovery();
        }
    };

    private ProgressBar progressView;
    private BluetoothAdapter adapter;
    private final ArrayList<MiBandDevice> deviceCandidates = new ArrayList<>();
    private DeviceCandidateAdapter candidateListAdapter;
    private Button startButton;
    private Scanning isScanning = Scanning.SCANNING_OFF;

    private enum Scanning {
        SCANNING_BT,
        SCANNING_BTLE,
        SCANNING_OFF
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_discovery);
        startButton = findViewById(R.id.discovery_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartButtonClick(startButton);
            }
        });

        progressView = findViewById(R.id.discovery_progressbar);
        progressView.setProgress(0);
        progressView.setIndeterminate(true);
        progressView.setVisibility(View.GONE);
        ListView deviceCandidatesView = findViewById(R.id.discovery_deviceCandidatesView);

        candidateListAdapter = new DeviceCandidateAdapter(this, deviceCandidates);
        deviceCandidatesView.setAdapter(candidateListAdapter);
        deviceCandidatesView.setOnItemClickListener(this);
        deviceCandidatesView.setOnItemLongClickListener(this);

        IntentFilter bluetoothIntents = new IntentFilter();
        bluetoothIntents.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothIntents.addAction(BluetoothDevice.ACTION_UUID);
        bluetoothIntents.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothIntents.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(bluetoothReceiver, bluetoothIntents);

        startDiscovery();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("deviceCandidates", deviceCandidates);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Parcelable> restoredCandidates = savedInstanceState.getParcelableArrayList("deviceCandidates");
        if (restoredCandidates != null) {
            deviceCandidates.clear();
            for (Parcelable p : restoredCandidates) {
                deviceCandidates.add((MiBandDevice) p);
            }
        }
    }

    public void onStartButtonClick(View button) {
        Log.d(DiscoveryActivity.TAG, "Start Button clicked");
        if (isScanning()) {
            stopDiscovery();
        } else {
            deviceCandidates.clear();
            candidateListAdapter.notifyDataSetChanged();

            startDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            Log.d(DiscoveryActivity.TAG, "Tried to unregister Bluetooth Receiver that wasn't registered.");
        }
        super.onDestroy();
    }


    /**
     * Pre: bluetooth is available, enabled and scanning is off.
     * Post: BT is discovering
     */
    private void startDiscovery() {
        if (isScanning()) {
            Log.d(DiscoveryActivity.TAG, "Not starting discovery, because already scanning.");
            return;
        }
        startDiscovery(Scanning.SCANNING_BT);
    }

    private void startDiscovery(Scanning what) {
        Log.d(DiscoveryActivity.TAG, "Starting discovery: " + what);
        discoveryStarted(what); // just to make sure
        if (ensureBluetoothReady()) {
            if (what == Scanning.SCANNING_BTLE) {
                if (AndroidUtils.supportsBluetoothLE()) {
                    startBTLEDiscovery();
                } else {
                    discoveryFinished();
                }
            }
        } else {
            discoveryFinished();
            AndroidUtils.toast(DiscoveryActivity.this, "Enable bluetooth to discovery devices", Toast.LENGTH_SHORT);
        }
    }

    private boolean isScanning() {
        return isScanning != Scanning.SCANNING_OFF;
    }

    private void stopDiscovery() {
        Log.d(DiscoveryActivity.TAG, "Stopping discovery");
        if (isScanning()) {
            Scanning wasScanning = isScanning;
            // unfortunately, we don't always get a call back when stopping the scan, so
            // we do it manually; BEFORE stopping the scan!
            discoveryFinished();

            if (wasScanning == Scanning.SCANNING_BT) {
                stopBTDiscovery();
            } else if (wasScanning == Scanning.SCANNING_BTLE) {
                stopBTLEDiscovery();
            }
            handler.removeMessages(0, stopRunnable);
        }
    }

    private void stopBTLEDiscovery() {
        if (adapter != null)
            adapter.stopLeScan(leScanCallback);
    }

    private void stopBTDiscovery() {
        if (adapter != null)
            adapter.cancelDiscovery();
    }

    private void bluetoothStateChanged(int newState) {
        discoveryFinished();
        if (newState == BluetoothAdapter.STATE_ON) {
            this.adapter = BluetoothAdapter.getDefaultAdapter();
            startButton.setEnabled(true);
        } else {
            this.adapter = null;
            startButton.setEnabled(false);
        }
    }

    private void discoveryFinished() {
        isScanning = Scanning.SCANNING_OFF;
        progressView.setVisibility(View.GONE);
        startButton.setText(getString(R.string.start_scanning));
    }

    private void discoveryStarted(Scanning what) {
        isScanning = what;
        progressView.setVisibility(View.VISIBLE);
        startButton.setText(getString(R.string.stop_scanning));
    }

    private boolean ensureBluetoothReady() {
        boolean available = checkBluetoothAvailable();
        startButton.setEnabled(available);
        if (available) {
            adapter.cancelDiscovery();
            return true;
        }
        return false;
    }

    private boolean checkBluetoothAvailable() {
        BluetoothManager bluetoothService = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothService == null) {
            Log.d(DiscoveryActivity.TAG, "No bluetooth available");
            this.adapter = null;
            return false;
        }
        BluetoothAdapter adapter = bluetoothService.getAdapter();
        if (adapter == null) {
            Log.d(DiscoveryActivity.TAG, "No bluetooth available");
            this.adapter = null;
            return false;
        }
        if (!adapter.isEnabled()) {
            Log.d(DiscoveryActivity.TAG, "Bluetooth not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            this.adapter = null;
            return false;
        }
        this.adapter = adapter;
        return true;
    }

    private void startBTLEDiscovery() {
        Log.d(DiscoveryActivity.TAG, "Starting BTLE Discovery");
        handler.removeMessages(0, stopRunnable);
        handler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_DURATION);
        adapter.startLeScan(leScanCallback);
    }

    private void checkAndRequestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
    }

    private Message getPostMessage(Runnable runnable) {
        Message m = Message.obtain(handler, runnable);
        m.obj = runnable;
        return m;
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        MiBandDevice deviceCandidate = deviceCandidates.get(position);
        if (deviceCandidate == null) {
            Log.d(DiscoveryActivity.TAG, "Device candidate clicked, but item not found");
            return true;
        }

        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MiBandDevice deviceCandidate = deviceCandidates.get(position);
        if (deviceCandidate == null) {
            Log.d(DiscoveryActivity.TAG, "Device candidate clicked, but item not found");
            return;
        }

        stopDiscovery();

        Log.d(DiscoveryActivity.TAG, "Using device candidate " + deviceCandidate.getName() + " " + deviceCandidate.getAddress());

        Bundle bundle = new Bundle();
        bundle.putParcelable(MiBandDevice.EXTRA_DEVICE_CANDIDATE, deviceCandidate);

        Intent intent = new Intent(this, PairingActivity.class);
        intent.putExtra("bundle", bundle);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBTDiscovery();
        stopBTLEDiscovery();
    }
}
