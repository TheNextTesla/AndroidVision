package com.androidFRC.androidVision.comm;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.androidFRC.androidVision.Configuration;
import com.androidFRC.androidVision.RobotEventBroadcastReceiver;
import com.androidFRC.androidVision.comm.messages.HeartbeatMessage;
import com.androidFRC.androidVision.comm.messages.OffWireMessage;
import com.androidFRC.androidVision.comm.messages.VisionMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RobotConnection
{
    //Connection State Variables
    private int m_port;
    private String m_host;
    private Context m_context;
    private boolean m_running = true;
    private boolean m_connected = false;
    volatile private Socket m_socket;
    private Thread m_connect_thread, m_read_thread, m_write_thread;

    //Timing State Variables
    private long m_last_heartbeat_sent_at = System.currentTimeMillis();
    private long m_last_heartbeat_rcvd_at = 0;

    //'Queue' of the VisionMessages to be Sent Out
    private ArrayBlockingQueue<VisionMessage> mToSend = new ArrayBlockingQueue<>(30);

    /**
     * Runnable That Constantly Loops, Sending Messages In the Queue When Available
     */
    protected class WriteThread implements Runnable
    {
        @Override
        public void run()
        {
            while (m_running)
            {
                VisionMessage nextToSend = null;
                try
                {
                    nextToSend = mToSend.poll(250, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e)
                {
                    Log.e("WriteThead", "Couldn't poll queue");
                }
                if (nextToSend == null)
                {
                    continue;
                }
                sendToWire(nextToSend);
            }
        }
    }

    /**
     * Runnable that Constantly Loops, Reading and Interpreting Messages As Available
     */
    protected class ReadThread implements Runnable
    {
        /**
         * Reacts For Each Message Type
         * @param message - New Vision Message to React
         */
        public void handleMessage(VisionMessage message)
        {
            //TODO: Should these be else ifs / switches?  A Message Cannot Have More Than One Type...
            if ("heartbeat".equals(message.getType()))
            {
                m_last_heartbeat_rcvd_at = System.currentTimeMillis();
            }
            if ("shot".equals(message.getType()))
            {
                broadcastShotTaken();
            }
            if ("camera_mode".equals(message.getType()))
            {
                if ("vision".equals(message.getMessage()))
                {
                    broadcastWantVisionMode();
                }
                else if ("intake".equals(message.getMessage()))
                {
                    broadcastWantIntakeMode();
                }
            }

            Log.w("Connection" , message.getType() + " " + message.getMessage());
        }

        /**
         * Runs Looping of the Reader
         */
        @Override
        public void run()
        {
            while (m_running)
            {
                if (m_socket != null || m_connected)
                {
                    BufferedReader reader;
                    try
                    {
                        InputStream is = m_socket.getInputStream();
                        reader = new BufferedReader(new InputStreamReader(is));
                    }
                    catch (IOException e)
                    {
                        Log.e("ReadThread", "Could not get input stream");
                        continue;
                    }
                    catch (NullPointerException npe)
                    {
                        Log.e("ReadThread", "socket was null");
                        continue;
                    }

                    String jsonMessage = null;
                    try
                    {
                        jsonMessage = reader.readLine();
                    }
                    catch (IOException e)
                    {
                        //TODO: Catch Block Event?
                    }
                    if (jsonMessage != null)
                    {
                        OffWireMessage parsedMessage = new OffWireMessage(jsonMessage);
                        if (parsedMessage.isValid())
                        {
                            handleMessage(parsedMessage);
                        }
                    }
                }
                else
                {
                    try
                    {
                        Thread.sleep(100, 0);
                    }
                    catch (InterruptedException e)
                    {
                        //TODO: Catch Block Event?
                    }
                }
            }
        }
    }

    /**
     * Runnable that Loops, Constantly Checking the 'Heartbeat' Messages
     * Starts Connections When Heartbeats Can Be Found, And Stops Them When They Haven't
     */
    protected class ConnectionMonitor implements Runnable
    {
        @Override
        public void run()
        {
            while (m_running)
            {
                try
                {
                    if (m_socket == null || !m_socket.isConnected() && !m_connected)
                    {
                        tryConnect();
                        Thread.sleep(250, 0);
                    }

                    long now = System.currentTimeMillis();

                    if (now - m_last_heartbeat_sent_at > Configuration.SEND_HEARTBEAT_PERIOD)
                    {
                        send(HeartbeatMessage.getInstance());
                        m_last_heartbeat_sent_at = now;
                    }

                    if (Math.abs(m_last_heartbeat_rcvd_at - m_last_heartbeat_sent_at) > Configuration.THRESHOLD_HEARTBEAT && m_connected)
                    {
                        m_connected = false;
                        broadcastRobotDisconnected();
                        broadcastWantVisionMode();
                    }
                    if (Math.abs(m_last_heartbeat_rcvd_at - m_last_heartbeat_sent_at) < Configuration.THRESHOLD_HEARTBEAT && !m_connected)
                    {
                        m_connected = true;
                        broadcastRobotConnected();
                    }

                    Thread.sleep(Configuration.CONNECTOR_SLEEP_MS, 0);
                }
                catch (InterruptedException e)
                {
                    //TODO: Catch Block Event?
                }
            }
        }
    }

    /**
     * Simple RobotConnection Constructor
     * @param context - Android Context
     * @param host - IP To Look At (LocalHost if using adb forward)
     * @param port - 'Port' To Set Up Connection
     */
    public RobotConnection(Context context, String host, int port)
    {
        m_context = context;
        m_host = host;
        m_port = port;
    }

    /**
     * Context Only Constructor - Assumes Default Networking Parameters
     * @param context - Android Context
     */
    public RobotConnection(Context context)
    {
        this(context, Configuration.ROBOT_PROXY_HOST, Configuration.ROBOT_PORT);
    }

    /**
     * Attempts to Start the Connection Socket
     */
    synchronized private void tryConnect()
    {
        if (m_socket == null)
        {
            try
            {
                m_socket = new Socket(m_host, m_port);
                m_socket.setSoTimeout(100);
            }
            catch (IOException e)
            {
                Log.w("RobotConnector", "Could not connect");
                m_socket = null;
            }
        }
    }

    /**
     * Attempts to Stop Connection and Runnable Threads
     */
    synchronized public void stop()
    {
        m_running = false;
        if (m_connect_thread != null && m_connect_thread.isAlive())
        {
            try
            {
                m_connect_thread.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        if (m_write_thread != null && m_write_thread.isAlive())
        {
            try
            {
                m_write_thread.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        if (m_read_thread != null && m_read_thread.isAlive())
        {
            try
            {
                m_read_thread.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the Runnable Threads
     */
    synchronized public void start()
    {
        m_running = true;

        if (m_write_thread == null || !m_write_thread.isAlive())
        {
            m_write_thread = new Thread(new WriteThread());
            m_write_thread.start();
        }

        if (m_read_thread == null || !m_read_thread.isAlive())
        {
            m_read_thread = new Thread(new ReadThread());
            m_read_thread.start();
        }

        if (m_connect_thread == null || !m_connect_thread.isAlive())
        {
            m_connect_thread = new Thread(new ConnectionMonitor());
            m_connect_thread.start();
        }
    }

    /**
     * Restart = Stop, Start
     */
    synchronized public void restart()
    {
        stop();
        start();
    }

    /**
     * Is the RobotConnection Properly Communicating
     * @return If the RobotConnection Properly Communicating
     */
    public synchronized boolean isConnected()
    {
        return m_socket != null && m_socket.isConnected() && m_connected;
    }

    /**
     * Directly Sends the VisionMessage over the Output Stream
     * @param message - Vision Message to be Sent
     * @return Whether or Not the Send Was Successful
     */
    private synchronized boolean sendToWire(VisionMessage message)
    {
        String toSend = message.toJson() + "\n";
        if (m_socket != null && m_socket.isConnected())
        {
            try
            {
                OutputStream os = m_socket.getOutputStream();
                os.write(toSend.getBytes());
                return true;
            }
            catch (IOException e)
            {
                Log.w("RobotConnection", "Could not send data to socket, try to reconnect");
                m_socket = null;
            }
        }
        return false;
    }

    /**
     * Adds the Message to the Queue of Messages to be Sent
     * @param message - VisionMessage to be Sent
     * @return If the Addition Was Successful
     */
    public synchronized boolean send(VisionMessage message)
    {
        return mToSend.offer(message);
    }

    /**
     * Sends Out A 'Broadcast' to All Other Classes 'Listening' that the Robot is Connected
     */
    private void broadcastRobotConnected()
    {
        Intent i = new Intent(RobotConnectionStatusBroadcastReceiver.ACTION_ROBOT_CONNECTED);
        m_context.sendBroadcast(i);
    }

    /**
     * Sends Out A 'Broadcast' to All Other Classes 'Listening' that the a Shot Was Taken
     */
    private void broadcastShotTaken()
    {
        Intent i = new Intent(RobotEventBroadcastReceiver.ACTION_SHOT_TAKEN);
        m_context.sendBroadcast(i);
    }

    /**
     * Sends Out A 'Broadcast' to All Other Classes 'Listening' that the 'VisionMode' has Been Requested
     */
    public void broadcastWantVisionMode()
    {
        Intent i = new Intent(RobotEventBroadcastReceiver.ACTION_WANT_VISION);
        m_context.sendBroadcast(i);
    }

    /**
     * Sends Out A 'Broadcast' to All Other Classes 'Listening' that the 'IntakeMode' has Been Requested
     */
    private void broadcastWantIntakeMode()
    {
        Intent i = new Intent(RobotEventBroadcastReceiver.ACTION_WANT_INTAKE);
        m_context.sendBroadcast(i);
    }

    /**
     * Sends Out A 'Broadcast' to All Other Classes 'Listening' that the Robot Has Been Disconnected
     */
    private void broadcastRobotDisconnected()
    {
        Intent i = new Intent(RobotConnectionStatusBroadcastReceiver.ACTION_ROBOT_DISCONNECTED);
        m_context.sendBroadcast(i);
    }
}
