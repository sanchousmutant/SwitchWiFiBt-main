package com.example.switchwifibt;

import android.Manifest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

import java.io.DataOutputStream;

public class PermissionActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> requestBluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    proceedWithBluetoothLogic();
                } else {
                    Toast.makeText(this, "Разрешение Bluetooth не предоставлено", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            proceedWithBluetoothLogic();
        }
    }

    private void proceedWithBluetoothLogic() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            openDeviceListAndFinish();
        } else {
            enableBluetoothViaRoot();
        }
    }

    private void enableBluetoothViaRoot() {
        new Thread(() -> {
            boolean success = executeRootCommand("svc bluetooth enable");
            runOnUiThread(() -> {
                if (success) {
                    // Даём время на инициализацию BT-адаптера
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        openDeviceListAndFinish();
                    }, 1000);
                } else {
                    Toast.makeText(this, "Нет root-доступа, запрос через систему", Toast.LENGTH_SHORT).show();
                    fallbackEnableBluetooth();
                }
            });
        }).start();
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

    private void fallbackEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    openDeviceListAndFinish();
                } else {
                    finish();
                }
            });

    private void openDeviceListAndFinish() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
        finish();
    }
}
