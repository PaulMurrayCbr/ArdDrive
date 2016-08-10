package pmurray_at_bigpond_dot_com.arddrive;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;

public class MainActivity extends AppCompatActivity {
    private static final int RETRY_CHOOSE_BLUETOOTH = 0xBEEF + 1;

    final BroadcastReceiver broadcastReciever = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            Snackbar.make(findViewById(android.R.id.content), action, Snackbar.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ((Button)findViewById(R.id.send_foo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                BluetoothService.startActionSendBytes(getApplicationContext(), "foo".getBytes());
            }
        });

        ((SeekBar)findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar var1, int var2, boolean var3)  {
                BluetoothService.startActionSendBytes(getApplicationContext(), ("{"+var2+"}").getBytes());

            }

            public void onStartTrackingTouch(SeekBar var1) {}

            public void onStopTrackingTouch(SeekBar var1) {}

        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(broadcastReciever, BluetoothService.getBroadcastFilter());
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastReciever);
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

    private void retry(int requestCode) {
        switch (requestCode) {
            case RETRY_CHOOSE_BLUETOOTH:
                chooseBluetooth();
            break;
        }
    }

}
