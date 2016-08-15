package pmurray_at_bigpond_dot_com.arddrive;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.UUID;

/**
 * TODO: break this class into a bytes-only class and a "understands messages" subclass
 */

public class BluetoothService extends Service {
    private static final String ACTION_CONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.CONNECT";
    private static final String ACTION_DISCONNECT = "pmurray_at_bigpond_dot_com.arddrive.action.DISCONNECT";
    private static final String ACTION_SEND_BYTES = "pmurray_at_bigpond_dot_com.arddrive.action.SEND_BYTES";
    private static final String ACTION_SEND_MESSAGE = "pmurray_at_bigpond_dot_com.arddrive.action.SEND_MESSAGE";

    public static final String BROADCAST_CONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_CONNECTED";
    public static final String BROADCAST_EXCEPTION = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_EXCEPTION";
    public static final String BROADCAST_DISCONNECTED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_DISCONNECTED";
    public static final String BROADCAST_BYTES_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_BYTES_SENT";
    public static final String BROADCAST_BYTES_FLUSHED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_BYTES_FLUSHED";
    public static final String BROADCAST_BYTES_NOT_SENT = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_BYTES_NOT_SENT";
    public static final String BROADCAST_BYTES_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_BYTES_RECEIVED";

    public static final String BROADCAST_MESSAGE_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_MESSAGE_RECEIVED";
    public static final String BROADCAST_BAD_MESSAGE = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_BAD_MESSAGE";
    public static final String BROADCAST_HEARTBEAT_ACQUIRED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_HEARTBEAT_ACQUIRED";
    public static final String BROADCAST_HEARTBEAT_RECEIVED = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_HEARTBEAT_RECEIVED";
    public static final String BROADCAST_HEARTBEAT_LOST = "pmurray_at_bigpond_dot_com.arddrive.broadcast.BTS_HEARTBEAT_LOST";

    /**
     * A UUID to use for the connection if you are connecting to a serial board. From the docs:
     * <blockquote>
     * Hint: If you are connecting to a Bluetooth serial board then try using the well-known SPP
     * UUID 00001101-0000-1000-8000-00805F9B34FB. However if you are connecting to an Android peer
     * then please generate your own unique UUID.
     * </blockquote>
     */

    public static final UUID SERIAL_BOARD_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final String EXTRA_UUID = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_UUID";

    public static final String EXTRA_BYTES = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES";
    public static final String EXTRA_SERIAL_RX = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_SERIAL_RX";
    public static final String EXTRA_SERIAL_TX = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_SERIAL_TX";
    public static final String EXTRA_BYTES_N = "pmurray_at_bigpond_dot_com.arddrive.extra.EXTRA_BYTES_N";
    public static final String EXTRA_EXCEPTION = "pmurray_at_bigpond_dot_com.arddrive.extra.EXCEPTION";

    public static final String EXTRA_CHECKSUM = "pmurray_at_bigpond_dot_com.arddrive.extra.CHECKSUM";
    public static final String EXTRA_FAULT = "pmurray_at_bigpond_dot_com.arddrive.extra.MESSAGE_FAULT";

    public enum MessageFault {
        incorrect_checksum,
        unexpected_character
    }

    ;

    BluetoothAdapter btAdapter;


    static class ChecksumCalculator {
        int checksum;

        ChecksumCalculator() {
            clear();
        }

        void clear() {
            checksum = 0xDEBB1E;
        }

        void append(byte[] bytes) {
            for (byte b : bytes) {
                checksum = ((checksum << 19) ^ (checksum >> 5) ^ b) & 0xFFFFFF;
            }
        }
    }

    class MessageBuilder {
        final ChecksumCalculator checksum = new ChecksumCalculator();
        final byte[] checkbytes = new byte[3];

        final BluetoothDevice d;
        MessageBuilder(BluetoothDevice d) { this.d = d;}

        byte[] makeMsg(byte[] bytes) {
            checksum.clear();
            checksum.append(bytes);

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            try {
                bs.write('<');
                bs.write(Base64.encode(bytes, Base64.DEFAULT));
                bs.write('#');
                checkbytes[0] = (byte)(checksum.checksum >>16);
                checkbytes[1] = (byte)(checksum.checksum >>8);
                checkbytes[2] = (byte)(checksum.checksum >>0);

                bs.write(Base64.encode(checkbytes, Base64.DEFAULT));
                bs.write('>');
                bs.write('\n');
            } catch (IOException ex) {
            } // this never happens

            return bs.toByteArray();
        }
    }


    enum MessageParserState {
        IDLE, MESSAGE, CHECKSUM
    }

    class MessageParser {
        private final BluetoothDevice d;
        ChecksumCalculator checksum = new ChecksumCalculator();
        boolean heartbeat = false;
        long most_recent_heartbeat_ms = System.currentTimeMillis();
        static final long heartbeat_fail_ms = 1000L * 30L;

        ByteArrayOutputStream message64 = new ByteArrayOutputStream();

        MessageParserState state;
        ByteArrayOutputStream messageChecksum64 = new ByteArrayOutputStream();


        public MessageParser(BluetoothDevice d) {
            this.d = d;
            clear();
        }

        void clear() {
            checksum.clear();
            state = MessageParserState.IDLE;
            message64.reset();
        }

        void append(byte[] bytes, int n) {
            for (int i = 0; i < n; i++) append(bytes[i]);
        }

        void append(final byte b) {
            if (b <= ' ') return; // ignore white space

            // asterisks are a heartbeat signal
            if (b == '*') {
                if (!heartbeat) {
                    broadcastHeartbeatAcquired(d);
                }

                most_recent_heartbeat_ms = System.currentTimeMillis();
                heartbeat = true;
                broadcastHeartbeatReceived(d);
                return;
            }

            // on any '<', force a restart.
            if (b == '<' && state != MessageParserState.IDLE) {
                broadcastBadMessage(d, message64.toByteArray(), -1, MessageFault.unexpected_character);
                state = MessageParserState.IDLE;
            }

            switch (state) {
                case IDLE:
                    if (b == '<') {
                        message64.reset();
                        state = MessageParserState.MESSAGE;
                    }
                    break;

                case MESSAGE:
                    if (b == '#') {
                        messageChecksum64.reset();
                        state = MessageParserState.CHECKSUM;
                    } else if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || (b == '+') || (b == '/') || (b == '=')) {
                        message64.write(b);
                    } else {
                        message64.write(b);
                        broadcastBadMessage(d, message64.toByteArray(), -1, MessageFault.unexpected_character);
                        state = MessageParserState.IDLE;
                    }
                    break;

                case CHECKSUM:
                    if (b == '>') {
                        byte[] message = Base64.decode(message64.toByteArray(), Base64.DEFAULT);
                        checksum.clear();
                        checksum.append(message);
                        byte[] decodedChecksum = Base64.decode(messageChecksum64.toByteArray(), Base64.DEFAULT);

                        if (decodedChecksum.length != 3) {
                            broadcastBadMessage(d, message, checksum.checksum, MessageFault.incorrect_checksum);
                        } else {
                            int check = (((int)decodedChecksum[0] << 16)&0xFF0000) | (((int)decodedChecksum[1] << 8)&0xFF00) | (((int)decodedChecksum[2])&0xFF);
                            if (checksum.checksum != check) {
                                broadcastBadMessage(d, message, checksum.checksum, MessageFault.incorrect_checksum);
                            } else {
                                broadcastMessageReceived(d, message, check);
                            }
                        }

                        state = MessageParserState.IDLE;
                    } else if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || (b == '+') || (b == '/') || (b == '=')) {
                        messageChecksum64.write(b);
                    } else {
                        broadcastBadMessage(d, message64.toByteArray(), -1, MessageFault.unexpected_character);
                        state = MessageParserState.IDLE;
                    }
                    break;
            }
        }

        void checkHeartbeat() {
            if (heartbeat && System.currentTimeMillis() - most_recent_heartbeat_ms > heartbeat_fail_ms) {
                heartbeat = false;
                broadcastHeartbeatLost(d);
            }
        }
    }


    protected class Connection implements Runnable {
        private final BluetoothDevice d;
        private final UUID uuid;
        private BluetoothSocket socket = null;

        private final LinkedList<byte[]> pending = new LinkedList<byte[]>();
        private byte[] buffer = new byte[1024 + 5]; // max bluetooth message is 1024 bytes. 5 is slop.

        private final MessageParser parser;
        private final MessageBuilder messageBuilder;

        Connection(BluetoothDevice d, UUID uuid) {
            this.d = d;
            this.uuid = uuid;
            this.parser = new MessageParser(d);
            this.messageBuilder = new MessageBuilder(d);
        }

        void sendMessage(byte[] bytes) {
            send(messageBuilder.makeMsg(bytes));
        }

        void send(byte[] bytes) {
            synchronized (this) {
                if (socket == null) {
                    broadcastBytesNotSent(bytes);
                } else {
                    pending.add(bytes);
                }
            }
        }

        public void run() {

            InputStream in = null;
            OutputStream out = null;
            int txSerial = 0;
            int rxSerial = 0;

            try {
                btAdapter.cancelDiscovery();
                socket = d.createRfcommSocketToServiceRecord(SERIAL_BOARD_UUID);
                socket.connect();

                broadcastConnected(d);

                in = socket.getInputStream();
                out = socket.getOutputStream();

                int toFlush = 0;

                while (currentConnection == this && socket.isConnected()) {
                    parser.checkHeartbeat();

                    if (in.available() > 0) {
                        ++rxSerial;
                        int n = in.read(buffer);
                        if (n > 0) {
                            broadcastBytesReceived(d, rxSerial, buffer, n);
                            parser.append(buffer, n);
                        }
                    } else {
                        byte[] xmit = null;
                        synchronized (pending) {
                            if (!pending.isEmpty()) {
                                xmit = pending.removeFirst();
                            }
                        }

                        if (xmit == null && toFlush > 0) {
                            out.flush();
                            broadcastBytesFlushed(d, txSerial, toFlush);
                            toFlush = 0;
                        }

                        if (xmit != null && xmit.length > 0) {
                            socket.getOutputStream().write(xmit);
                            toFlush += xmit.length;
                            broadcastBytesSent(d, ++txSerial, xmit);
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

    public static IntentFilter addAllBroadcasts(IntentFilter f) {
        f.addAction(BROADCAST_CONNECTED);
        f.addAction(BROADCAST_EXCEPTION);
        f.addAction(BROADCAST_DISCONNECTED);
        f.addAction(BROADCAST_BYTES_SENT);
        f.addAction(BROADCAST_BYTES_FLUSHED);
        f.addAction(BROADCAST_BYTES_NOT_SENT);
        f.addAction(BROADCAST_BYTES_RECEIVED);

        f.addAction(BROADCAST_MESSAGE_RECEIVED);
        f.addAction(BROADCAST_BAD_MESSAGE);

        f.addAction(BROADCAST_HEARTBEAT_ACQUIRED);
        f.addAction(BROADCAST_HEARTBEAT_RECEIVED);
        f.addAction(BROADCAST_HEARTBEAT_LOST);

        return f;
    }

    public static IntentFilter addMessageBroadcasts(IntentFilter f) {
        f.addAction(BROADCAST_MESSAGE_RECEIVED);
        f.addAction(BROADCAST_BAD_MESSAGE);
        f.addAction(BROADCAST_HEARTBEAT_ACQUIRED);
        f.addAction(BROADCAST_HEARTBEAT_LOST);

        return f;
    }

    public static void startActionConnect(Context context, BluetoothDevice d, UUID uuid) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, uuid);
        context.startService(intent);
    }

    public static void startActionDisconnect(Context context) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_DISCONNECT);
        context.startService(intent);
    }

    public static void startActionSendBytes(Context context, byte[] bytes) {
        startActionSendBytes(context, bytes, 0, bytes.length);
    }

    public static void startActionSendBytes(Context context, byte[] bytes, int offs, int len) {
        byte[] cpy = new byte[len];
        System.arraycopy(bytes, offs, cpy, 0, len);

        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_SEND_BYTES);
        intent.putExtra(EXTRA_BYTES, cpy);
        context.startService(intent);
    }

    public static void startActionSendMessage(Context context, String message) {
        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_SEND_MESSAGE);
        intent.putExtra(EXTRA_BYTES, message.getBytes());
        context.startService(intent);
    }


    public static void startActionSendMessage(Context context, byte[] bytes) {
        startActionSendMessage(context, bytes, 0, bytes.length);
    }

    public static void startActionSendMessage(Context context, byte[] bytes, int offs, int len) {
        byte[] cpy = new byte[len];
        System.arraycopy(bytes, offs, cpy, 0, len);

        Intent intent = new Intent(context, BluetoothService.class);
        intent.setAction(ACTION_SEND_MESSAGE);
        intent.putExtra(EXTRA_BYTES, cpy);
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

    protected void broadcastBytesSent(BluetoothDevice d, int txSerial, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_SENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_SERIAL_TX, txSerial);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesFlushed(BluetoothDevice d, int txSerial, int bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_FLUSHED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_SERIAL_TX, txSerial);
        intent.putExtra(EXTRA_BYTES_N, bytes);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesNotSent(byte[] bytes) {
        Intent intent = new Intent(BROADCAST_BYTES_NOT_SENT);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, bytes.length);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBytesReceived(BluetoothDevice d, int rxSerial, byte[] bytes, int n) {
        Intent intent = new Intent(BROADCAST_BYTES_RECEIVED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_SERIAL_RX, rxSerial);
        intent.putExtra(EXTRA_BYTES, bytes);
        intent.putExtra(EXTRA_BYTES_N, n);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }


    protected void broadcastMessageReceived(BluetoothDevice d, byte[] message, int checksum) {
        Intent intent = new Intent(BROADCAST_MESSAGE_RECEIVED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_BYTES, message);
        intent.putExtra(EXTRA_CHECKSUM, checksum);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastBadMessage(BluetoothDevice d, byte[] message, int checksum, MessageFault fault) {
        Intent intent = new Intent(BROADCAST_BAD_MESSAGE);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        intent.putExtra(EXTRA_BYTES, message);
        if (checksum != -1) intent.putExtra(EXTRA_CHECKSUM, checksum);
        intent.putExtra(EXTRA_FAULT, fault);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastHeartbeatAcquired(BluetoothDevice d) {
        Intent intent = new Intent(BROADCAST_HEARTBEAT_ACQUIRED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastHeartbeatReceived(BluetoothDevice d) {
        Intent intent = new Intent(BROADCAST_HEARTBEAT_RECEIVED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void broadcastHeartbeatLost(BluetoothDevice d) {
        Intent intent = new Intent(BROADCAST_HEARTBEAT_LOST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, d);
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
                    UUID uuid = intent.getParcelableExtra(EXTRA_UUID);
                    handleActionConnect(d, uuid);
                } else if (ACTION_DISCONNECT.equals(action)) {
                    handleActionDisconnect();
                } else if (ACTION_SEND_BYTES.equals(action)) {
                    final byte[] bytes = intent.getByteArrayExtra(EXTRA_BYTES);
                    handleActionSendBytes(bytes);
                } else if (ACTION_SEND_MESSAGE.equals(action)) {
                    final byte[] bytes = intent.getByteArrayExtra(EXTRA_BYTES);
                    handleActionSendMessage(bytes);
                }
            }
        } catch (RuntimeException ex) {
            Log.e(getClass().getName(), intent.getAction(), ex);
            throw ex;
        }

    }

    private void handleActionConnect(BluetoothDevice d, UUID uuid) {
        currentConnection = new Connection(d, uuid);
        new Thread(currentConnection).start();
    }

    private void handleActionDisconnect() {
        currentConnection = null;
    }

    private void handleActionSendBytes(byte[] bytes) {
        if (currentConnection == null) {
            broadcastBytesNotSent(bytes);
        } else {
            currentConnection.send(bytes);
        }
    }

    private void handleActionSendMessage(byte[] bytes) {
        if (currentConnection != null) {
            currentConnection.sendMessage(bytes);
        }
    }

    @Override
    public void onDestroy() {
        currentConnection = null; // this will stop the connection thread
        super.onDestroy();
    }


}
