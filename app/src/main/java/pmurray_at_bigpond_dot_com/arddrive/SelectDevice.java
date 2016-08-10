package pmurray_at_bigpond_dot_com.arddrive;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SelectDevice extends AppCompatActivity {
    BluetoothAdapter btAdapter;
    final ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_device);
        ((ListView) findViewById(R.id.devicesList)).setAdapter(new BtDeviceAdapter(this, devices));
    }
}
