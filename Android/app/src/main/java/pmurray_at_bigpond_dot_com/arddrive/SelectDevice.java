package pmurray_at_bigpond_dot_com.arddrive;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SelectDevice extends AppCompatActivity {
    static final int POPULATE_BONDED_DEVICES = 0xBEEF + 1;
    static final int SCAN_BLUETOOTH = 0xBEEF + 2;

    BluetoothAdapter btAdapter;
    final ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    boolean needToCancelDiscovery = false;

    static class BtDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
        public BtDeviceAdapter(Context context, List<BluetoothDevice> devices) {
            super(context, R.layout.device_list_item, devices);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            BluetoothDevice device = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_list_item, parent, false);
            }
            // Lookup view for data population
            TextView name = (TextView) convertView.findViewById(R.id.bluetoothName);
            TextView addr = (TextView) convertView.findViewById(R.id.bluetoothAddress);
            // Populate the data into the template view using the data object
            name.setText(device.getName());
            addr.setText(device.getAddress());
            return convertView;
        }
    }

    BtDeviceAdapter devicesListAdapter;

    private final BroadcastReceiver blueToothDiscoveryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                populateBondedDevices();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_device);

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        devicesListAdapter = new BtDeviceAdapter(this, devices);
        ((ListView) findViewById(R.id.devicesList)).setAdapter(devicesListAdapter);

        findViewById(R.id.scanButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanBluetooth();
            }
        });

        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(needToCancelDiscovery) {
                    btAdapter.cancelDiscovery();
                    needToCancelDiscovery = false;
                }
                finish();
            }
        });


        ((ListView) findViewById(R.id.devicesList)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                AdapterView<BtDeviceAdapter> v = (AdapterView<BtDeviceAdapter>) adapterView;
                BluetoothDevice d = v.getAdapter().getItem(i);
                if(d != null) {
                    if(needToCancelDiscovery) {
                        btAdapter.cancelDiscovery();
                        needToCancelDiscovery = false;
                    }

                    BluetoothService.startActionConnect(getApplicationContext(), d, BluetoothService.SERIAL_BOARD_UUID);
                    finish();
                }
            }

        });
    }

    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(blueToothDiscoveryReceiver, filter);
        populateBondedDevices();
    }

    protected void onStop() {
        if(needToCancelDiscovery) {
            btAdapter.cancelDiscovery();
            needToCancelDiscovery = false;
        }
        unregisterReceiver(blueToothDiscoveryReceiver);
        super.onStop();
    }

    protected void populateBondedDevices() {
        if (obtainBlueToothPermission(POPULATE_BONDED_DEVICES) && enableBlueTooth(POPULATE_BONDED_DEVICES)) {
            devicesListAdapter.clear();
            devicesListAdapter.addAll(btAdapter.getBondedDevices());
            Snackbar.make(findViewById(android.R.id.content), "found " + devices.size() + " paired devices", Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    protected void scanBluetooth() {
        if (obtainBlueToothPermission(SCAN_BLUETOOTH) && obtainBlueToothAdminPermission(SCAN_BLUETOOTH) && enableBlueTooth(SCAN_BLUETOOTH)) {
            devicesListAdapter.clear();
            btAdapter.startDiscovery();
            needToCancelDiscovery = true;
        }
    }

    protected boolean obtainBlueToothPermission(final int callbackCode) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Snackbar.make(findViewById(android.R.id.content), R.string.bluetoothRationale, Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                retry(callbackCode);
                            }
                        })
                        .setAction("Cancel", null)
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, callbackCode);
            }
            return false;
        }
    }

    protected boolean obtainBlueToothAdminPermission(final int callbackCode) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADMIN)) {
                Snackbar.make(findViewById(android.R.id.content), R.string.bluetoothAdminRationale, Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                retry(callbackCode);
                            }
                        })
                        .setAction("Cancel", null)
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, callbackCode);
            }
            return false;
        }
    }

    protected boolean enableBlueTooth(int callbackCode) {
        if (btAdapter.isEnabled()) {
            return true;
        }
        else {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), callbackCode);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        for(int r: grantResults) {
            if(r!=PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(android.R.id.content), R.string.permissionRefused, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        retry(requestCode);
    }

    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        if(resultCode == RESULT_OK) {
            retry(requestCode);
        }
        else {
            Snackbar.make(findViewById(android.R.id.content), R.string.activityUnsuccessful, Snackbar.LENGTH_LONG).show();
        }
    }

    protected void retry(int requestCode) {
        switch (requestCode) {
            case POPULATE_BONDED_DEVICES:
                populateBondedDevices();
                break;
            case SCAN_BLUETOOTH:
                scanBluetooth();
                break;
        }
    }

}
