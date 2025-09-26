package com.example.switchwifibt;

import android.Manifest;
import android.annotation.SuppressLint;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;

public class PermissionActivity extends AppCompatActivity {
    private static final int BLUETOOTH_CONNECT_PERMISSION_CODE = 101;

    private ActivityResultLauncher<String> requestBluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                proceedWithBluetoothLogic();
            });

    private ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    openDeviceListAndFinish();
                } else {
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // First, check for the BLUETOOTH_CONNECT permission.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            // If permission is already granted, proceed with Bluetooth logic.
            proceedWithBluetoothLogic();
        }
    }

    private void proceedWithBluetoothLogic() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            // Bluetooth is already on, go to the device list.
            openDeviceListAndFinish();
        } else if (bluetoothAdapter != null) {
            // Bluetooth is off, request to turn it on.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            // Bluetooth adapter is not available, just finish.
            finish();
        }
    }

    private void openDeviceListAndFinish() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
        finish();
    }
}
