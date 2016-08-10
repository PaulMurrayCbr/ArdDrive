package pmurray_at_bigpond_dot_com.arddrive;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

public class BluetoothService  extends Service {
    private static final String ACTION_CONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.CONNECT";
    private static final String ACTION_DISCONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.DISCONNECT";
    private static final String ACTION_SEND_BYTES = "pmurray_at_bigpond_dot_com.arddrive.action.SEND_BYTES";

    public static final String BROADCAST_CONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_CONNECTED";
    public static final String BROADCAST_DISCONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_DISCONNECTED";
    public static final String BROADCAST_BYTES_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_SENT";
    public static final String BROADCAST_BYTES_NOT_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_NOT_SENT";
    public static final String BROADCAST_BYTES_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_RECEIVED";

    private static final String EXTRA_MAC = "pmurray_at_bigpond_dot_com.arddrive.extra.MAC";
    private static final String EXTRA_BYTES = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES";


    public BluetoothService() {
    }

    public static IntentFilter getBroadcastFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BROADCAST_CONNECTED);
        f.addAction(BROADCAST_DISCONNECTED);
        f.addAction(BROADCAST_BYTES_SENT);
        f.addAction(BROADCAST_BYTES_RECEIVED);
        return f;
    }

    public static void startActionConnect(Context context, String mac) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(EXTRA_MAC, mac);
        context.startService(intent);
    }

    public static void startActionDisconnect(Context context) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_DISCONNECT);
        context.startService(intent);
    }

    public static void startActionSendBytes(Context context, byte[] bytes) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_SEND_BYTES);
        intent.putExtra(EXTRA_BYTES, bytes);
        context.startService(intent);
    }

    protected void broadcastConnected(String mac) {
        Intent intent = new Intent(BROADCAST_CONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastDisonnected(String mac) {
        Intent intent = new Intent(BROADCAST_DISCONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesSent(String mac, byte[] bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_SENT);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra(EXTRA_BYTES, bytes);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesNotSent(byte[] bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_NOT_SENT);
        intent.putExtra(EXTRA_BYTES, bytes);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesReceived(String mac, byte[] bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_RECEIVED);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra(EXTRA_BYTES, bytes);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        return START_STICKY;
    }

    public IBinder onBind (Intent intent) {
        return null;
    }

    protected void handleCommand(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                final String mac = intent.getStringExtra(EXTRA_MAC);
                handleActionConnect(mac);
            }
            else if (ACTION_DISCONNECT.equals(action)) {
                handleActionDisconnect();
            }
            else if (ACTION_SEND_BYTES.equals(action)) {
                final byte[] bytes = intent.getByteArrayExtra(EXTRA_BYTES);
                handleActionSendBytes(bytes);
            }

        }
    }

    private void handleActionConnect(String mac) {
        broadcastConnected(mac);
    }

    private void handleActionDisconnect() {
        broadcastDisonnected("meh");
    }

    private void handleActionSendBytes(byte[] bytes) {
        broadcastBytesSent("meh", bytes);
    }

}
