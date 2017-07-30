package com.team254.cheezdroid;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MjpgServer {

    public static final String K_BOUNDARY = "boundary";
    private static MjpgServer sInst = null;

    public static final String TAG = "MJPG";

    public static MjpgServer getInstance() {
        if (sInst == null) {
            sInst = new MjpgServer();
        }
        return sInst;
    }

    private ArrayList<Connection> mConnections = new ArrayList<>();
    private Object mLock = new Object();

    private class Connection {

        private Socket mSocket;

        public Connection(Socket s) {
            mSocket = s;
        }

        public boolean isAlive() {
            return !mSocket.isClosed() && mSocket.isConnected();
        }

        public void start() {
            try {
                Log.i(TAG, "Starting a connection!");
                OutputStream stream = mSocket.getOutputStream();
                stream.write(("HTTP/1.0 200 OK\r\n" +
                        "Server: androidvision\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Connection: close\r\n" +
                        "Content-Type: multipart/x-mixed-replace;boundary=--" + K_BOUNDARY + "\r\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeImageUpdate(byte[] buffer) {
            if (!isAlive()) {
                return;
            }
            OutputStream stream = null;
            try {
                stream = mSocket.getOutputStream();
                stream.write(("\r\n--" + K_BOUNDARY + "\r\n").getBytes());
                //Content-type: image/jpeg
                stream.write(("Content-type: image/bmp\r\n" +
                        "Content-Length: " + buffer.length + "\r\n" +
                        "\r\n").getBytes());
                stream.write(buffer);
                stream.flush();
            } catch (IOException e) {
                //e.printStackTrace();
                // There is a broken pipe exception being thrown here I cannot figure out.
            }
        }

    }

    private ServerSocket mServerSocket;
    private boolean mRunning;
    private Thread mRunThread;
    private Long mLastUpdate = 0L;

    private MjpgServer() {
        try {
            mServerSocket = new ServerSocket(5800);
            mRunning = true;
            mRunThread = new Thread(runner);
            mRunThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update(byte[] bytes) {
        new SendUpdateTask().execute(bytes);
    }

    private class SendUpdateTask extends AsyncTask<byte[], Void, Void> {

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Long result) {
        }

        @Override
        protected Void doInBackground(byte[]... params) {
            update(params[0], true);
            return null;
        }
    }

    private void update(byte[] bytes, boolean updateTimer) {
        if (updateTimer) {
            mLastUpdate = System.currentTimeMillis();
        }
        synchronized (mLock) {
            ArrayList<Integer> badIndices = new ArrayList<>(mConnections.size());
            for (int i = 0; i < mConnections.size(); i++) {
                Connection c = mConnections.get(i);
                if (c == null || !c.isAlive()) {
                    badIndices.add(i);
                } else {
                    c.writeImageUpdate(bytes);
                }
            }
            for (int i : badIndices) {
                mConnections.remove(i);
            }
        }
    }

    Runnable runner = new Runnable() {

        @Override
        public void run() {
            while (mRunning) {
                try {
                    Log.i(TAG, "Waiting for connections");
                    Socket s = mServerSocket.accept();
                    Log.i("MjpgServer", "Got a socket: " + s);
                    Connection c = new Connection(s);
                    synchronized (mLock) {
                        mConnections.add(c);
                    }
                    c.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
}