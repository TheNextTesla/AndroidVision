package com.team254.cheezdroid;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Singleton Class That Sends Frames over and IP Server
 */
public class MjpgServer
{
    private static final String K_BOUNDARY = "boundary";
    private static MjpgServer sInst = null;
    //TODO: Find Better Default Image Byte Array
    private static byte[] mDefault;

    private static final String TAG = "MJPG";

    /**
     * Singleton getInstance of the Class
     * @return The Only MjpgServer
     */
    public static MjpgServer getInstance()
    {
        if (sInst == null)
        {
            sInst = new MjpgServer();
        }
        return sInst;
    }

    //List of "Connections" to Manage How to Send Images
    private ArrayList<Connection> mConnections = new ArrayList<>();
    private Object mLock = new Object();

    /**
     * Enclosed Class Connection - Deals With the Actual Sending of Image Data
     */
    private class Connection
    {
        private Socket mSocket;

        /**
         * Constructor for Connection - Simple Set
         * @param s - Socket to Use
         */
        public Connection(Socket s)
        {
            mSocket = s;
        }

        /**
         * Boolean of Whether or Not the Connection is Not Closed and Connected
         * @return Whether or Not the Connection is Not Closed and Connected
         */
        public boolean isAlive()
        {
            return !mSocket.isClosed() && mSocket.isConnected();
        }

        /**
         * Begins the HTTP Stream Connection
         */
        public void start()
        {
            try
            {
                Log.i(TAG, "Starting a connection!");
                OutputStream stream = mSocket.getOutputStream();
                stream.write(("HTTP/1.0 200 OK\r\n" +
                        "Server: androidvision\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Connection: close\r\n" +
                        "Content-Type: multipart/x-mixed-replace;boundary=--" + K_BOUNDARY + "\r\n").getBytes());
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        /**
         * Sends and Flushes A Stream Of Bytes That Represent the Next Image Update
         * @param buffer - Byte Array to be Streamed
         */
        public void writeImageUpdate(byte[] buffer)
        {
            if (!isAlive())
            {
                return;
            }
            Log.d(TAG, "Uploading " + buffer.length + " bytes");

            OutputStream stream = null;
            try
            {
                stream = mSocket.getOutputStream();
                stream.write(("\r\n--" + K_BOUNDARY + "\r\n").getBytes());
                //Content-type: image/jpeg
                stream.write(("Content-type: image/jpeg\r\n" +
                        "Content-Length: " + buffer.length + "\r\n" +
                        "\r\n").getBytes());
                stream.write(buffer);
                stream.flush();
            }
            catch (IOException e)
            {
                //e.printStackTrace();
                //TODO: There is a broken pipe exception being thrown here I cannot figure out.
            }
        }
    }

    //State Variables for MjpegServer
    private ServerSocket mServerSocket;
    private boolean mRunning;
    private Thread mRunThread;
    private Long mLastUpdate = 0L;

    /**
     * Private Constructor for the Singleton MjpegServer
     * TODO: What to do if it initially fails and cannot be rerun?
     */
    private MjpgServer()
    {
        try
        {
            mServerSocket = new ServerSocket(Configuration.VIDEO_PORT);
            mRunning = true;
            mRunThread = new Thread(runner);
            mRunThread.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Creates a New Asynchronous Task that Streams an Image Update
     * @param bytes - Byte Array of the Image Update
     */
    public void update(byte[] bytes)
    {
        new SendUpdateTask().execute(bytes);
    }

    /**
     * Sends Out A Clearing (Default) Image Update to Make Sure the User(s)
     * Know That the Stream Has Paused
     */
    public void pause()
    {
        for(Connection c : mConnections)
        {
            c.writeImageUpdate(mDefault);
        }
    }

    /**
     * Asynchronous Task that Handles Image Update Streaming
     */
    private class SendUpdateTask extends AsyncTask<byte[], Void, Void>
    {
        /**
         * Does Nothing While It is Being Sent
         * @param progress - Current Progress on Task
         */
        protected void onProgressUpdate(Integer... progress)
        {

        }

        /**
         * Does Nothing After it is Sent
         * @param result - Result Variable
         */
        protected void onPostExecute(Long result)
        {

        }

        /**
         * The Actual Method that Sends Asynchronously
         * Goes Off the Update Method, and Only Allows For One Parameter
         * @param params - Byte Array (Only One)
         * @return Nothing
         */
        @Override
        protected Void doInBackground(byte[]... params)
        {
            update(params[0], true);
            return null;
        }
    }

    /**
     * Sends the Image Update, Maintains Timing off the Action
     * @param bytes - Image to Be Sent
     * @param updateTimer - Boolean For Whether or Not to Keep Update Time
     */
    private void update(byte[] bytes, boolean updateTimer)
    {
        if (updateTimer)
        {
            mLastUpdate = System.currentTimeMillis();
        }
        synchronized (mLock)
        {
            ArrayList<Integer> badIndices = new ArrayList<>(mConnections.size());
            for (int i = 0; i < mConnections.size(); i++)
            {
                Connection c = mConnections.get(i);
                if (c == null || !c.isAlive())
                {
                    badIndices.add(i);
                }
                else
                {
                    c.writeImageUpdate(bytes);
                }
            }
            for (int i : badIndices)
            {
                mConnections.remove(i);
            }
        }
    }

    //Runnable that Manages Connections
    Runnable runner = new Runnable()
    {
        @Override
        public void run()
        {
            while (mRunning)
            {
                try
                {
                    Log.i(TAG, "Waiting for connections");
                    Socket s = mServerSocket.accept();
                    Log.i("MjpgServer", "Got a socket: " + s);
                    Connection c = new Connection(s);
                    synchronized (mLock)
                    {
                        mConnections.add(c);
                    }
                    c.start();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    };
}