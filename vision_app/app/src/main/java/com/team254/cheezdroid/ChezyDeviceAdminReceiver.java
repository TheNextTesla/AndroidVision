package com.team254.cheezdroid;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;

/**
 * Class that Manages the Application's 'Admin Receiver' Status
 */
public class ChezyDeviceAdminReceiver extends DeviceAdminReceiver
{
    /**
     * Gets the Application Component that Is Available
     * @param context - Android Context
     * @return Specifies the Application Component that Is Available
     */
    public static ComponentName getComponentName(Context context)
    {
        return new ComponentName(context.getApplicationContext(), ChezyDeviceAdminReceiver.class);
    }
}