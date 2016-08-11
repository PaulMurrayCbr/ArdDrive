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
import java.io.InputStream;
import java.io.OutputStream;
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
    public static final String BROADCAST_BYTES_FLUSHED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_FLUSHED";
    public static final String BROADCAST_BYTES_NOT_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_NOT_SENT";
    public static final String BROADCAST_BYTES_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BROADCAST_BYTES_RECEIVED";

    // a random uuid
    public static final UUID MY_UUID = UUID.randomUUID();

    public static final String EXTRA_BYTES = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES";
    public static final String EXTRA_PACKET_N = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_PACKET_N";
    public static final String EXTRA_BYTES_N = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES_N";
    public static final String EXTRA_EXCEPTION = "pmurray_at_bigpond_dot_com.arddrive.extra.EXCEPTION";

    BluetoothAdapter btAdapter;

    class Connection implements Runnable {
        private final BluetoothDevice d;
        private BluetoothSocket socket = null;

        // this would be better done with nio, obviously
        private final LinkedList<byte[]> pending = new LinkedList<byte[]>();
        private byte[] buffer = new byte[1024];

        Connection(BluetoothDevice d) {
            this.d = d;
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
            InputStream in = null;
            OutputStream out = null;
            int packetNum = 0;


            d.fetchUuidsWithSdp();

            try {
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

                btAdapter.cancelDiscovery();

                socket = d.createRfcommSocketToServiceRecord(MY_UUID);

                for(int i = 0; i<3; i++) {
                    try {
                        socket.connect();
                    } catch (IOException e) {
                        broadcastException(d, e);
                        Thread.sleep(2000);
                    }
                }

                if(!socket.isConnected()) throw new IOException("Unable to connect");


                // this pairs the device, but it is a hack and the resulting socket doesn't work anyway

//                try {
//                    socket.connect();
//                } catch (IOException e) {
//                    broadcastException(d, e);
//                    // bug in bluetooth stack?
//                    socket = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(d, 1);
//                    socket.connect();
//                }

                broadcastConnected(d);

                in = socket.getInputStream();
                out = socket.getOutputStream();

                int toFlush = 0;

                while (currentConnection == this) {
                    if (in.available() > 0) {
                        int n = in.read(buffer);
                        if (n > 0) {
                            broadcastBytesReceived(d, buffer, n);
                        }
                    } else {
                        byte[] xmit = null;
                        synchronized (pending) {
                            if (!pending.isEmpty()) {
                                xmit = pending.removeFirst();
                            }
                        }

                        if(xmit == null && toFlush > 0) {
                            out.flush();
                            broadcastBytesFlushed(d, packetNum, toFlush);
                            toFlush = 0;
                        }

                        if (xmit != null && xmit.length > 0) {
                            socket.getOutputStream().write(xmit);
                            Thread.sleep(100); // TODO: THIS IS FOR TESTING PURPOSES
                            toFlush += xmit.length;
                            broadcastBytesSent(d, ++packetNum, xmit);
                        }
                    }
                }
            } catch (Exception ex) {
                broadcastException(d, ex);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ex) {
                        broadcastException(d, ex);
                    }
                    in = null;
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ex) {
                        broadcastException(d, ex);
                    }
                    out = null;
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ex) {
                        broadcastException(d, ex);
                    }
                    socket = null;
                }
                broadcastDisconnected(d);

                synchronized (pending) {
                    while (!pending.isEmpty()) {
                        broadcastBytesNotSent(pending.removeFirst());
                    }
                }
            }
        }
    }

    volatile Connection currentConnection = null; // must be volatile

    public BluetoothService() {
    }

    public static IntentFilter getBluetoothServiceFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BROADCAST_CONNECTED);
        f.addAction(BROADCAST_EXCEPTION);
        f.addAction(BROADCAST_DISCONNECTED);
        f.addAction(BROADCAST_BYTES_SENT);
        f.addAction(BROADCAST_BYTES_FLUSHED);
        f.addAction(BROADCAST_BYTES_NOT_SENT);
        f.addAction(BROADCAST_BYTES_RECEIVED);
        return f;
    }

    public static void startActionConnect(Context context, BluetoothDevice d) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
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

    protected void broadcastConnected(BluetoothDevice d) {
        Intent intent = new Intent(BROADCAST_CONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastDisconnected(BluetoothDevice d) {
        Intent intent = new Intent(BROADCAST_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastException(BluetoothDevice d, Exception ex) {
        Intent intent = new Intent(BROADCAST_EXCEPTION);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);

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

    protected void broadcastBytesSent(BluetoothDevice d, int packetNum, byte[] bytes) {
        broadcastBytesSent(d, packetNum, bytes, bytes.length);
    }

    protected void broadcastBytesSent(BluetoothDevice d, int packetNum, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_SENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_PACKET_N, packetNum);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesFlushed(BluetoothDevice d, int packetNum, int bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_FLUSHED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_PACKET_N, packetNum);
        intent.putExtra(EXTRA_BYTES_N, bytes);
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

    protected void broadcastBytesReceived(BluetoothDevice d, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_RECEIVED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
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
                    BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    handleActionConnect(d);
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

    private void handleActionConnect(BluetoothDevice d) {
        currentConnection = new Connection(d);
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
