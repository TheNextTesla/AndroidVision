package com.team254.cheezdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * Manages The Broadcast Based on Phone State
 */
public class OnScreenOffReceiver extends BroadcastReceiver
{
    /**
     * When a Broadcast is Recieved
     * @param context - Android Context
     * @param intent - Purpose / Message of the Method Call
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))
        {
            AppContext ctx = (AppContext) context.getApplicationContext();
            // is Kiosk Mode active?
            if(shouldStayAwake())
            {
                wakeUpDevice(ctx);
            }
        }
    }

    /**
     * Attempts to Wake the Device
     * @param context - Android Context
     */
    private void wakeUpDevice(AppContext context)
    {
        //Get WakeLock Reference via AppContext
        PowerManager.WakeLock wakeLock = context.getWakeLock();
        if (wakeLock.isHeld())
        {
            //Release Old WakeLock
            wakeLock.release();
        }

        //Create a new wake lock...
        //TODO: Should Give Max Time For WakeLock
        wakeLock.acquire();

        //... and Release Again
        wakeLock.release();
    }

    /**
     * If the VisionTrackerActivity is Currently Locked
     * @return If the VisionTrackerActivity is Currently Locked
     */
    private boolean shouldStayAwake()
    {
        return VisionTrackerActivity.isLocked();
    }
}