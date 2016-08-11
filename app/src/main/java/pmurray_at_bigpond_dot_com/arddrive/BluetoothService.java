package pmurray_at_bigpond_dot_com.arddrive;

import android.app.IntentService;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String ACTION_CONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.CONNECT";
    private static final String ACTION_DISCONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.DISCONNECT";
    private static final String ACTION_SEND_BYTES = "pmurray_at_bigpond_dot_com.arddrive.action.SEND_BYTES";

    public static final String BROADCAST_CONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_CONNECTED";
    public static final String BROADCAST_EXCEPTION = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_EXCEPTION";
    public static final String BROADCAST_DISCONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_DISCONNECTED";
    public static final String BROADCAST_BYTES_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_SENT";
    public static final String BROADCAST_BYTES_NOT_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_NOT_SENT";
    public static final String BROADCAST_BYTES_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_RECEIVED";

    // a random uuid
    public static final UUID MY_UUID = UUID.fromString("b0cb6270-5f19-11e6-8b77-86f30ca893d3");

    public static final String EXTRA_MAC = "pmurray_at_bigpond_dot_com.arddrive.extra.MAC";
    public static final String EXTRA_BYTES = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES";
    public static final String EXTRA_BYTES_N = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES_N";
    public static final String EXTRA_EXCEPTION = "pmurray_at_bigpond_dot_com.arddrive.extra.EXCEPTION";

    BluetoothAdapter btAdapter;

    class Connection implements Runnable {
        private final String mac;
        private BluetoothDevice d;
        private BluetoothSocket socket = null;

        // this would be better done with nio, obviously
        private final LinkedList<byte[]> pending = new LinkedList<byte[]>();
        private byte[] buffer = new byte[1024];

        Connection(String mac) {
            this.mac = mac;
        }

        void send(byte[] bytes, int n) {
            if (n <= 0) return;
            if (n > bytes.length) n = bytes.length;
            byte[] b = new byte[n];
            System.arraycopy(b, 0, bytes, 0, n);

            synchronized (this) {
                if (socket == null) {
                    broadcastBytesNotSent(b, n);
                } else {
                    pending.add(b);
                }
            }
        }


        public void run() {
            try {
                d = btAdapter.getRemoteDevice(mac);
                if (d == null) {
                    throw new IOException(mac + " not found");
                }

                /*  sticky - needs admin permission
                if (d.getBondState() == BluetoothDevice.BOND_NONE) {
                    if (!d.createBond()) {
                        throw new IOException("cannot bond with " + mac);
                    }
                }

                while (d.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (currentConnection != this) {
                        return;
                    }
                }
                */

                socket = d.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();

                broadcastConnected(d.getAddress());

                while (currentConnection == this) {
                    if (socket.getInputStream().available() > 0) {
                        int n = socket.getInputStream().read(buffer);
                        if (n > 0) {
                            broadcastBytesReceived(d.getAddress(), buffer, n);
                        }
                    } else {
                        byte[] xmit = null;
                        synchronized (pending) {
                            if (!pending.isEmpty())
                                xmit = pending.getFirst();
                        }
                        if (xmit != null) {
                            socket.getOutputStream().write(xmit);
                            broadcastBytesSent(d.getAddress(), xmit);
                        }
                    }
                }
            } catch (Exception ex) {
                broadcastException(d.getAddress(), ex);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ex) {
                        broadcastException(d.getAddress(), ex);
                    }
                    socket = null;
                }
                broadcastDisconnected(d.getAddress());

                synchronized (pending) {
                    while (!pending.isEmpty()) {
                        broadcastBytesNotSent(pending.getFirst());
                    }
                }
            }
        }
    }

    volatile Connection currentConnection = null; // must be volatile

    public BluetoothService() {
    }

    public static IntentFilter getBroadcastFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BROADCAST_CONNECTED);
        f.addAction(BROADCAST_EXCEPTION);
        f.addAction(BROADCAST_DISCONNECTED);
        f.addAction(BROADCAST_BYTES_SENT);
        f.addAction(BROADCAST_BYTES_NOT_SENT);
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
        Log.i(getClass().getName(), "connected to " + mac);
        Intent intent = new Intent(BROADCAST_CONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastDisconnected(String mac) {
        Log.i(getClass().getName(), "disconnected from " + mac);
        Intent intent = new Intent(BROADCAST_DISCONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastException(String mac, Exception ex) {
        Log.w(getClass().getName(), "error on " + mac + ": " + ex.toString());
        Intent intent = new Intent(BROADCAST_EXCEPTION);
        intent.putExtra(EXTRA_MAC, mac);

        StackTraceElement e = null;
        for (StackTraceElement ee : ex.getStackTrace()) {
            if (ee.getClassName().startsWith("pmurray_at_bigpond_dot_com.arddrive.")) {
                e = ee;
                break;
            }
        }

        intent.putExtra(EXTRA_EXCEPTION, (e == null ? "" : e.getMethodName() + "(" + e.getLineNumber() + ")") + ex.toString());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesSent(String mac, byte[] bytes) {
        broadcastBytesSent(mac, bytes, bytes.length);
    }

    protected void broadcastBytesSent(String mac, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_SENT);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesNotSent(byte[] bytes) {
        broadcastBytesNotSent(bytes, bytes.length);
    }

    protected void broadcastBytesNotSent(byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_NOT_SENT);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesReceived(String mac, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_RECEIVED);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    @Deprecated
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (btAdapter == null) {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        handleCommand(intent);
        return START_STICKY;
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void handleCommand(Intent intent) {
        try {
            if (intent != null) {
                final String action = intent.getAction();
                if (ACTION_CONNECT.equals(action)) {
                    final String mac = intent.getStringExtra(EXTRA_MAC);
                    handleActionConnect(mac);
                } else if (ACTION_DISCONNECT.equals(action)) {
                    handleActionDisconnect();
                } else if (ACTION_SEND_BYTES.equals(action)) {
                    final byte[] bytes = intent.getByteArrayExtra(EXTRA_BYTES);
                    final int n = intent.getIntExtra(EXTRA_BYTES_N, bytes.length);
                    handleActionSendBytes(bytes, n);
                }
            }
        } catch (RuntimeException ex) {
            Log.e(getClass().getName(), intent.getAction(), ex);
            throw ex;
        }

    }

    private void handleActionConnect(String mac) {
        currentConnection = new Connection(mac);
        new Thread(currentConnection).start();
    }

    private void handleActionDisconnect() {
        currentConnection = null;
    }

    private void handleActionSendBytes(byte[] bytes, int n) {
        if (currentConnection == null) {
            broadcastBytesNotSent(bytes, n);
        } else {
            currentConnection.send(bytes, n);
        }
    }

}
