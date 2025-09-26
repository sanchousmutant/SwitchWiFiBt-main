package com.example.switchwifibt;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.content.pm.PackageManager;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import java.util.List;

public class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

    private int connectingPosition = -1;
    private String activeDeviceAddress = null;

    public DeviceListAdapter(Context context, List<BluetoothDevice> devices) {
        super(context, 0, devices);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_list_item, parent, false);
        }

        TextView deviceName = convertView.findViewById(R.id.device_name);
        ProgressBar progressBar = convertView.findViewById(R.id.connecting_progress);
        ImageView checkMark = convertView.findViewById(R.id.check_mark);

        BluetoothDevice device = getItem(position);
        if (device != null) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName.setText(device.getName());
            } else {
                deviceName.setText(device.getAddress());
            }

            if (position == connectingPosition) {
                progressBar.setVisibility(View.VISIBLE);
                checkMark.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                if (device.getAddress().equals(activeDeviceAddress)) {
                    checkMark.setVisibility(View.VISIBLE);
                } else {
                    checkMark.setVisibility(View.GONE);
                }
            }
        }

        return convertView;
    }

    public void setConnectingPosition(int position) {
        connectingPosition = position;
        notifyDataSetChanged();
    }

    public void setActiveDeviceAddress(String address) {
        activeDeviceAddress = address;
        notifyDataSetChanged();
    }
}
