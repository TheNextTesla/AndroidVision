package com.team254.cheezdroid;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import com.team254.cheezdroid.comm.RobotConnection;

import java.util.Arrays;

/**
 * Manages the Fundamental Application Operation
 */
public class AppContext extends Application
{
    private AppContext instance;
    private PowerManager.WakeLock wakeLock;
    private OnScreenOffReceiver onScreenOffReceiver;

    /*
        This class is mainly here so we can get references to the application context in places where
        it is otherwise extremely hairy to do so. USE SPARINGLY.
     */
    private static AppContext app;

    private RobotConnection rc;

    /**
     * Constructor that Sets the Current Instance to the New Object
     */
    public AppContext()
    {
        super();
        app = this;
    }

    /**
     * Returns the Application Context
     * @return the Application Context
     */
    public static Context getDefaultContext()
    {
        return app.getApplicationContext();
    }

    /**
     * Android Lifecycle Method - Creates Robot Connection and Sets Up WakeLock
     */
    @Override
    public void onCreate()
    {
        super.onCreate();
        instance = this;
        rc = new RobotConnection(getDefaultContext());
        rc.start();
        registerKioskModeScreenOffReceiver();
    }

    /**
     * Returns the Current Instance of the AppContext's RobotConnection
     * @return the Current Instance of the AppContext's RobotConnection
     */
    public static RobotConnection getRobotConnection()
    {
        return app.rc;
    }

    /**
     * Registers Screen Off Receiver
     */
    private void registerKioskModeScreenOffReceiver()
    {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        onScreenOffReceiver = new OnScreenOffReceiver();
        registerReceiver(onScreenOffReceiver, filter);
    }

    /**
     *
     * @return The Current WakeLock, And Creates it if not Available
     */
    public PowerManager.WakeLock getWakeLock()
    {
        if(wakeLock == null)
        {
            //TODO: Handle WakeLock Failure Outcome
            //lazy loading: first call, create wakeLock via PowerManager.
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "wakeup");
        }
        return wakeLock;
    }

    /**
     * When the AppContext is Killed, It Stops the RobotConnection
     */
    @Override
    public void onTerminate()
    {
        super.onTerminate();
        rc.stop();
    }
}