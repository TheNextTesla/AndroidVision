/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.team254.cheezdroid;

import android.app.Activity;
import android.os.Bundle;

/**
 * 'Selfie' Activity - Runs 'SelfieModeFragment', which allows the User to Call for A Picture Externally
 */
public class SelfieActivity extends Activity implements RobotEventListener
{
    RobotEventBroadcastReceiver rebr;

    /**
     * 'Selfie' Activity Constructor - Instantiates RobotEventBroadcastReceiver
     * @param savedInstanceState - Android Instance State (Past Runs)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        rebr = new RobotEventBroadcastReceiver(this, this);
        if (savedInstanceState == null)
        {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, SelfieModeFragment.newInstance())
                    .commit();
        }
    }


    /**
     * Implemented Method From RobotEventListener - Unused
     */
    @Override
    public void shotTaken()
    {

    }

    /**
     * Implemented Method From RobotEventListener
     * This is the Only One That is Actually Used - Cleans Up When Vision is Wanted
     */
    @Override
    public void wantsVisionMode()
    {
        finish();
    }

    /**
     * Implemented Method From RobotEventListener - Unused
     */
    @Override
    public void wantsIntakeMode()
    {

    }

    /**
     * Android Activity onDestroy Lifetime Cycle Method
     * Calls Super and 'Unregisters' The 'Receiver'
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(rebr);
    }
}
