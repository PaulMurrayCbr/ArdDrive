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
    public static final String BROADCAST_CONNECTION_FAILED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_CONNECTION_FAILED";
    public static final String BROADCAST_DISCONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_DISCONNECTED";
    public static final String BROADCAST_BYTES_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_SENT";
    public static final String BROADCAST_BYTES_NOT_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_NOT_SENT";
    public static final String BROADCAST_BYTES_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_RECEIVED";

    // a random uuid
    public static final UUID MY_UUID = UUID.fromString("b0cb6270-5f19-11e6-8b77-86f30ca893d3");

    private static final String EXTRA_MAC = "pmurray_at_bigpond_dot_com.arddrive.extra.MAC";
    private static final String EXTRA_BYTES = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES";
    private static final String EXTRA_BYTES_N = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES_N";

    final Object mutex = new Object();

    final LinkedList<byte[]> pending = new LinkedList<byte[]>();

    volatile Thread worker = null;
    volatile String connectedTo = null;

    BluetoothAdapter btAdapter;

    class ConnectToDevice implements Runnable {
        BluetoothDevice d;

        ConnectToDevice(BluetoothDevice d) {
            this.d = d;
        }

        public void run() {
            BluetoothSocket socket = null;
            try {
                socket = d.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();

                synchronized (mutex) {
                    if(worker == Thread.currentThread()) {
                        connectedTo = d.getAddress();
                        worker = new Thread(new TalkToBt(socket));
                        worker.start();
                        broadcastConnected(d.getAddress());
                    }
                    else {
                        try {
                            socket.close();
                        }
                        catch(Exception ex) {}
                    }
                }

            } catch (IOException e) {
                if(socket != null) {
                    try {
                        socket.close();
                    }
                    catch(Exception ex) {}
                }
                synchronized (mutex) {
                    if(worker == Thread.currentThread()) {
                        worker = null;
                        broadcastConnectionFailed(d.getAddress());
                    }
                }
            }
        }
    }

    ;

    class TalkToBt implements Runnable {
        final BluetoothSocket socket;
        final byte[] buffer = new byte[1024];

        TalkToBt(BluetoothSocket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                for (; ; ) {
                    byte[] toSend = null;
                    String _connectedTo;

                    synchronized (mutex) {
                        if (Thread.currentThread() != worker) {
                            throw new IOException();
                        }

                        if(!pending.isEmpty()) {
                            toSend = pending.getFirst();
                        }

                        _connectedTo = connectedTo;
                    }

                    if(toSend != null) {
                        socket.getOutputStream().write(toSend);
                        broadcastBytesSent(_connectedTo, toSend, toSend.length);
                    }

                    if(socket.getInputStream().available()>0) {
                        int n = socket.getInputStream().read(buffer);
                        if (n > 0) {
                            broadcastBytesReceived(_connectedTo, buffer, n);
                        }
                    }
                }
            }
            catch(IOException ex) {
                try { socket.close();} catch(Exception ex2) {}

                synchronized (mutex) {
                    if (Thread.currentThread() == worker) {
                        broadcastDisonnected(connectedTo);
                        worker = null; // signal to all threads that they are to stop`
                        connectedTo = null;
                        pending.clear();
                    }
                }

            }
        }
    }

    public BluetoothService() {
    }

    public static IntentFilter getBroadcastFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BROADCAST_CONNECTED);
        f.addAction(BROADCAST_CONNECTION_FAILED);
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
        Intent intent = new Intent(BROADCAST_CONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastConnectionFailed(String mac) {
        Intent intent = new Intent(BROADCAST_CONNECTION_FAILED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastDisonnected(String mac) {
        Intent intent = new Intent(BROADCAST_DISCONNECTED);
        intent.putExtra(EXTRA_MAC, mac);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesSent(String mac, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_SENT);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesNotSent(byte[] bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_NOT_SENT);
        intent.putExtra(EXTRA_BYTES, bytes);
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
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        return START_STICKY;
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void handleCommand(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                final String mac = intent.getStringExtra(EXTRA_MAC);
                handleActionConnect(mac);
            } else if (ACTION_DISCONNECT.equals(action)) {
                handleActionDisconnect();
            } else if (ACTION_SEND_BYTES.equals(action)) {
                final byte[] bytes = intent.getByteArrayExtra(EXTRA_BYTES);
                handleActionSendBytes(bytes);
            }
        }
    }

    private void handleActionConnect(String mac) {
        // I assume that we have bluetooth permission by this stage

        synchronized (mutex) {
            if(connectedTo != null) {
                broadcastDisonnected(connectedTo);
            }
            worker = null; // signal to all threads that they are to stop`
            connectedTo = null;
            pending.clear();
        }

        for (BluetoothDevice d : btAdapter.getBondedDevices()) {
            if (d.getAddress().equals(mac)) {
                synchronized (mutex) {
                    worker = new Thread(new ConnectToDevice(d));
                    worker.start();
                }
            }
        }

    }

    private void handleActionDisconnect() {
        synchronized (mutex) {
            if(connectedTo != null) {
                broadcastDisonnected(connectedTo);
            }
            worker = null; // signal to all threads that they are to stop`
            connectedTo = null;
            pending.clear();
        }
    }

    private void handleActionSendBytes(byte[] bytes) {
        synchronized(mutex) {
            if (connectedTo == null) {
                broadcastBytesNotSent(bytes);
            } else {
                pending.add(bytes);
            }
        }
    }

}
