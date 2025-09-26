package com.example.switchwifibt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DeviceListActivity extends AppCompatActivity {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private DeviceListAdapter deviceListAdapter;
    private String activeDeviceAddress;
    private ArrayList<BluetoothDevice> deviceList;
    private ListView deviceListView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        deviceListView = findViewById(R.id.paired_devices_list);
        deviceList = new ArrayList<>();

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }

        populateDeviceList();
        getActiveDevice();

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (deviceList.isEmpty()) return;

                BluetoothDevice device = deviceList.get(position);

                // Если нажато уже активное устройство
                if (device.getAddress().equals(activeDeviceAddress)) {
                    disconnectFromDevice();
                    return;
                }
                
                // Если есть другое активное соединение, сначала отключаемся
                if (socket != null && socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                deviceListAdapter.setConnectingPosition(position);
                deviceListView.setEnabled(false);

                new Thread(() -> {
                    BluetoothSocket[] tempSocketHolder = new BluetoothSocket[1];
                    try {
                        tempSocketHolder[0] = device.createRfcommSocketToServiceRecord(MY_UUID);
                        // Set a 10-second timeout for connection
                        Handler timeoutHandler = new Handler(Looper.getMainLooper());
                        Runnable timeoutRunnable = () -> {
                            try {
                                if (tempSocketHolder[0] != null && !tempSocketHolder[0].isConnected()) {
                                    tempSocketHolder[0].close();
                                }
                            } catch (IOException ignored) {}
                            runOnUiThread(() -> {
                                Toast.makeText(DeviceListActivity.this, "Превышено время ожидания подключения", Toast.LENGTH_SHORT).show();
                                deviceListAdapter.setConnectingPosition(-1);
                                deviceListView.setEnabled(true);
                            });
                        };
                        timeoutHandler.postDelayed(timeoutRunnable, TimeUnit.SECONDS.toMillis(10));

                        tempSocketHolder[0].connect();
                        timeoutHandler.removeCallbacks(timeoutRunnable); // Cancel timeout if connected

                        runOnUiThread(() -> {
                            socket = tempSocketHolder[0];
                            activeDeviceAddress = device.getAddress();
                            Toast.makeText(DeviceListActivity.this, "Подключено к " + device.getName(), Toast.LENGTH_SHORT).show();
                            deviceListAdapter.setActiveDeviceAddress(activeDeviceAddress);
                            deviceListAdapter.setConnectingPosition(-1);
                            deviceListView.setEnabled(true);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            Toast.makeText(DeviceListActivity.this, "Не удалось подключиться: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            deviceListAdapter.setConnectingPosition(-1);
                            deviceListView.setEnabled(true);
                        });
                        if (tempSocketHolder[0] != null) {
                            try {
                                tempSocketHolder[0].close();
                            } catch (IOException ignored) {}
                        }
                    }
                }).start();
            }
        });

        deviceListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void disconnectFromDevice() {
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
                Toast.makeText(this, "Отключено", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка при отключении", Toast.LENGTH_SHORT).show();
            }
        }
        activeDeviceAddress = null;
        deviceListAdapter.setActiveDeviceAddress(null);
    }

    @SuppressLint("MissingPermission")
    private void populateDeviceList() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        deviceList.addAll(pairedDevices);

        if (deviceList.isEmpty()) {
            // Handle no devices case if necessary, e.g., show a message
            finish(); // Or just close if there's nothing to show
        } else {
            deviceListAdapter = new DeviceListAdapter(this, deviceList);
            deviceListView.setAdapter(deviceListAdapter);
        }
    }

    @SuppressLint("MissingPermission")
    private void getActiveDevice() {
        bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                if (!connectedDevices.isEmpty()) {
                    // Assuming one active device for simplicity
                    activeDeviceAddress = connectedDevices.get(0).getAddress();
                    deviceListAdapter.setActiveDeviceAddress(activeDeviceAddress);
                }
                bluetoothAdapter.closeProfileProxy(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.HEADSET); // You can check other profiles like A2DP
    }
}
