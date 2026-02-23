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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.DataOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeviceListActivity extends AppCompatActivity {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private DeviceListAdapter deviceListAdapter;
    private String activeDeviceAddress;
    private ArrayList<BluetoothDevice> deviceList;
    private ListView deviceListView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private SwitchMaterial btSwitch;
    private View listContainer;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        deviceListView = findViewById(R.id.paired_devices_list);
        deviceList = new ArrayList<>();

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }

        // Кнопка "Готово"
        Button btnDone = findViewById(R.id.btn_done);
        btnDone.setOnClickListener(v -> finish());

        // Кнопка настроек BT
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
        });

        // Закрытие при нажатии на затемнённый фон
        findViewById(R.id.panel_overlay).setOnClickListener(v -> finish());

        // Переключатель Bluetooth
        btSwitch = findViewById(R.id.bt_switch);
        btSwitch.setChecked(bluetoothAdapter.isEnabled());
        updateListVisibility(bluetoothAdapter.isEnabled());

        btSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == bluetoothAdapter.isEnabled()) return;

            btSwitch.setEnabled(false);
            new Thread(() -> {
                String cmd = isChecked ? "svc bluetooth enable" : "svc bluetooth disable";
                boolean rootSuccess = executeRootCommand(cmd);
                runOnUiThread(() -> {
                    if (rootSuccess) {
                        // Ждём пока адаптер переключится
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            btSwitch.setEnabled(true);
                            updateListVisibility(isChecked);
                            if (isChecked) {
                                populateDeviceList();
                                getActiveDevice();
                            } else {
                                deviceList.clear();
                                if (deviceListAdapter != null) {
                                    deviceListAdapter.notifyDataSetChanged();
                                }
                            }
                        }, 1000);
                    } else {
                        // Нет root — откатываем Switch
                        btSwitch.setChecked(!isChecked);
                        btSwitch.setEnabled(true);
                        Toast.makeText(DeviceListActivity.this, "Нет root-доступа", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        populateDeviceList();
        getActiveDevice();

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (deviceList.isEmpty()) return;

                BluetoothDevice device = deviceList.get(position);

                if (device.getAddress().equals(activeDeviceAddress)) {
                    disconnectFromDevice();
                    return;
                }

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
                    AtomicBoolean timedOut = new AtomicBoolean(false);
                    try {
                        tempSocketHolder[0] = device.createRfcommSocketToServiceRecord(MY_UUID);
                        Handler timeoutHandler = new Handler(Looper.getMainLooper());
                        Runnable timeoutRunnable = () -> {
                            if (timedOut.compareAndSet(false, true)) {
                                try {
                                    if (tempSocketHolder[0] != null) {
                                        tempSocketHolder[0].close();
                                    }
                                } catch (IOException ignored) {}
                                Toast.makeText(DeviceListActivity.this, "Превышено время ожидания подключения", Toast.LENGTH_SHORT).show();
                                deviceListAdapter.setConnectingPosition(-1);
                                deviceListView.setEnabled(true);
                            }
                        };
                        timeoutHandler.postDelayed(timeoutRunnable, TimeUnit.SECONDS.toMillis(10));

                        tempSocketHolder[0].connect();
                        timeoutHandler.removeCallbacks(timeoutRunnable);

                        if (!timedOut.get()) {
                            runOnUiThread(() -> {
                                socket = tempSocketHolder[0];
                                activeDeviceAddress = device.getAddress();
                                Toast.makeText(DeviceListActivity.this, "Подключено к " + device.getName(), Toast.LENGTH_SHORT).show();
                                deviceListAdapter.setActiveDeviceAddress(activeDeviceAddress);
                                deviceListAdapter.setConnectingPosition(-1);
                                deviceListView.setEnabled(true);
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (!timedOut.get()) {
                            runOnUiThread(() -> {
                                Toast.makeText(DeviceListActivity.this, "Не удалось подключиться: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                deviceListAdapter.setConnectingPosition(-1);
                                deviceListView.setEnabled(true);
                            });
                        }
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
        socket = null;
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
            finish();
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
                    activeDeviceAddress = connectedDevices.get(0).getAddress();
                    deviceListAdapter.setActiveDeviceAddress(activeDeviceAddress);
                }
                bluetoothAdapter.closeProfileProxy(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.HEADSET);
    }

    private void updateListVisibility(boolean btEnabled) {
        deviceListView.setVisibility(btEnabled ? View.VISIBLE : View.GONE);
    }

    private boolean executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
