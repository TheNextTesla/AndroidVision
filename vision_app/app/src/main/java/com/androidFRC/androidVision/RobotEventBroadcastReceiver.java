package com.androidFRC.androidVision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Broadcast Receiver Class that Calls Methods of the RobotEventListener Implementing Classes
 */
public class RobotEventBroadcastReceiver extends BroadcastReceiver
{
    public static final String ACTION_SHOT_TAKEN = "ACTION_SHOT_TAKEN";
    public static final String ACTION_WANT_VISION = "ACTION_WANT_VISION";
    public static final String ACTION_WANT_INTAKE = "ACTION_WANT_INTAKE";

    private RobotEventListener m_listener;

    /**
     * Constructor for the Receiver that Add its to Android's List of Intent Call Backs
     * @param context - Android Context
     * @param listener - The Listener in Question (RobotEventListener)
     */
    public RobotEventBroadcastReceiver(Context context, RobotEventListener listener)
    {
        this.m_listener = listener;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOT_TAKEN);
        intentFilter.addAction(ACTION_WANT_VISION);
        intentFilter.addAction(ACTION_WANT_INTAKE);
        context.registerReceiver(this, intentFilter);
    }

    /**
     * Calls Listener Methods When the Broadcast is Received
     * @param context - Android Context
     * @param intent - 'Intent' of the Action (How to Figure out Which Kind of Broadcast Was Received)
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (ACTION_SHOT_TAKEN.equals(intent.getAction()))
        {
            m_listener.shotTaken();
        }
        if (ACTION_WANT_VISION.equals(intent.getAction()))
        {
            m_listener.wantsVisionMode();
        }
        if (ACTION_WANT_INTAKE.equals(intent.getAction()))
        {
            m_listener.wantsIntakeMode();
        }
    }
}
