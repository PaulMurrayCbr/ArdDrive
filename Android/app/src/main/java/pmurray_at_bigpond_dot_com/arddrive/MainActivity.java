package pmurray_at_bigpond_dot_com.arddrive;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static pmurray_at_bigpond_dot_com.arddrive.BluetoothService.*;

public class MainActivity extends AppCompatActivity {
    private static final int RETRY_CHOOSE_BLUETOOTH = 0xBEEF + 1;

    static class MyThing {

        final String mac;
        final String act;
        final int tx, rx, nb;
        final String uuid;
        final String txt;

        MyThing(Intent msg) {
            if (msg.hasExtra(BluetoothDevice.EXTRA_DEVICE)) {
                BluetoothDevice device =
                        msg.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mac = device.getName();
            } else {
                mac = "NO DEVICE";
            }

            String s = msg.getAction();
            if (s != null) s = s.substring(s.lastIndexOf('.') + 1);
            act = (s);

            if (msg.hasExtra(EXTRA_SERIAL_TX)) {
                tx = msg.getIntExtra(EXTRA_SERIAL_TX, -1);
            } else {
                tx = -1;
            }

            if (msg.hasExtra(EXTRA_SERIAL_RX)) {
                rx = msg.getIntExtra(EXTRA_SERIAL_RX, -1);
            } else rx = -1;


            if (msg.hasExtra(BluetoothDevice.EXTRA_UUID)) {
                uuid = (msg.getParcelableExtra(BluetoothDevice.EXTRA_UUID).toString());
            } else uuid = null;

            if (msg.hasExtra(EXTRA_BYTES_N)) {
                nb = msg.getIntExtra(EXTRA_BYTES_N, -1);
            } else nb = -1;

            StringBuilder sb = new StringBuilder();
            sb.append(' ');

            if (msg.hasExtra(EXTRA_BYTES)) {
                byte[] b = msg.getByteArrayExtra(EXTRA_BYTES);
                int bytes = nb == -1 || nb > b.length ? b.length : nb;
                sb.append(" [");
                for (int i = 0; i < bytes; i++) {
                    byte bb = b[i];
                    if (bb >= ' ' && bb < 127)
                        sb.append((char) bb);
                    else {
                        byte bbb = (byte) ((bb >> 4) & 0xf);
                        sb.append((char) (bbb + (bbb < 10 ? '0' : 'a' - 10)));
                        bbb = (byte) ((bb) & 0xf);
                        sb.append((char) (bbb + (bbb < 10 ? '0' : 'a' - 10)));
                    }
                }
                sb.append("]");
            }

            if (msg.hasExtra(EXTRA_EXCEPTION)) {
                sb.append(msg.getStringExtra(EXTRA_EXCEPTION));
            }

            txt = sb.toString();
        }
    }


    static class BroadcastsAdapter extends ArrayAdapter<MyThing> {
        public BroadcastsAdapter(Context context, List<MyThing> messages) {
            super(context, R.layout.device_list_item, messages);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            MyThing msg = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.broadcast_list_item, parent, false);
            }
            // Lookup view for data population
            TextView mac = (TextView) convertView.findViewById(R.id.broadcast_mac);
            TextView act = (TextView) convertView.findViewById(R.id.broadcast_action);
            TextView txt = (TextView) convertView.findViewById(R.id.broadcast_text);
            TextView nb = (TextView) convertView.findViewById(R.id.broadcast_nBytes);
            TextView rx = (TextView) convertView.findViewById(R.id.broadcast_rxSerial);
            TextView tx = (TextView) convertView.findViewById(R.id.broadcast_txSerial);
            TextView uuid = (TextView) convertView.findViewById(R.id.broadcast_uuid);

            mac.setText(msg.mac);
            act.setText(msg.act);
            txt.setText(msg.txt);
            nb.setText(msg.nb == -1 ? null : Integer.toString(msg.nb));
            tx.setText(msg.tx == -1 ? null : Integer.toString(msg.tx));
            rx.setText(msg.rx == -1 ? null : Integer.toString(msg.rx));
            uuid.setText(msg.uuid);

            return convertView;
        }
    }

    BroadcastsAdapter broadcastsAdapter;


    final BroadcastReceiver broadcastReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            while (broadcastsAdapter.getCount() >= 20) {
                broadcastsAdapter.remove(broadcastsAdapter.getItem(0));
            }
            broadcastsAdapter.add(new MyThing(intent));
        }
    };


    final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            while (broadcastsAdapter.getCount() >= 20) {
                broadcastsAdapter.remove(broadcastsAdapter.getItem(0));
            }
            broadcastsAdapter.add(new MyThing(intent));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ((Button) findViewById(R.id.send_foo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startActionSendMessage(getApplicationContext(), "foo");
            }
        });

        ((SeekBar) findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar var1, int var2, boolean var3) {
                startActionSendMessage(getApplicationContext(), "SLIDER:" + Integer.toString(var2) + " ;");
            }

            public void onStartTrackingTouch(SeekBar var1) {
            }

            public void onStopTrackingTouch(SeekBar var1) {
            }

        });

        broadcastsAdapter = new BroadcastsAdapter(this, new ArrayList<MyThing>());
        ListView broadcastList = (ListView) findViewById(R.id.broadcastList);
        broadcastList.setAdapter(broadcastsAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReciever, addAllBroadcasts(new IntentFilter()));

        registerReceiver(btReceiver, lowLevelBtFiter());
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastReciever);
        unregisterReceiver(btReceiver);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_select_device) {
            chooseBluetooth();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void chooseBluetooth() {
        if (obtainBlueToothPermission(RETRY_CHOOSE_BLUETOOTH)) {
            startActivity(new Intent(this, SelectDevice.class));
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(android.R.id.content), R.string.permissionRefused, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        retry(requestCode);
    }

    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            retry(requestCode);
        } else {
            Snackbar.make(findViewById(android.R.id.content), R.string.activityUnsuccessful, Snackbar.LENGTH_LONG).show();
        }
    }

    private void retry(int requestCode) {
        switch (requestCode) {
            case RETRY_CHOOSE_BLUETOOTH:
                chooseBluetooth();
                break;
        }
    }

    protected IntentFilter lowLevelBtFiter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        f.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        f.addAction(BluetoothDevice.ACTION_UUID);
        return f;
    }


}
