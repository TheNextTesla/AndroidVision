package com.androidFRC.androidVision;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import com.androidFRC.androidVision.comm.RobotConnectionStateListener;
import com.androidFRC.androidVision.comm.RobotConnectionStatusBroadcastReceiver;
import com.androidFRC.androidVision.math.Rotation2d;

import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity (Basically the Main Activity) that Has
 */
public class VisionTrackerActivity extends Activity implements RobotConnectionStateListener, RobotEventListener
{
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static boolean sLocked = true;

    private VisionTrackerGLSurfaceView mView;
    private TextView mProcMode;
    private ImageButton mLockButton, mPrefsButton, mViewTypeButton;
    private TextView mBatteryText;
    private TextView mTestInterfaceText;
    private ImageView mChargingIcon;
    private Preferences m_prefs;

    private View connectionStateView;
    private RobotConnectionStatusBroadcastReceiver rbr;
    private RobotEventBroadcastReceiver rer;
    private Timer mUpdateViewTimer;
    private Long mLastSelfieLaunch = 0L;

    private Toast mCalibrationToast;
    private Handler mCalibrationTestHandler;
    private Runnable mCalibrationTestRunnable;

    private boolean mIsToggledFieldTest;
    private boolean mIsRunning;

    /**
     * If the Activity is "Locked" (Pinned and Settings are Not Visible)
     * @return If the Activity is "Locked" (Pinned and Settings are Not Visible)
     */
    public static boolean isLocked()
    {
        return sLocked;
    }

    /**
     * Method that Runs (Implementing RobotEventListener) Whenever 'Shot Taken'
     * Runs Based on a Android 'Broadcast' Setup
     * @see RobotEventListener
     */
    @Override
    public void shotTaken()
    {
        Log.i("VisionActivity", "Shot taken");
        playAirhorn();
    }

    /**
     * Method that Runs (Implementing RobotEventListener) Whenever The Computer 'Wants Vision Mode'
     * Runs Based on a Android 'Broadcast' Setup
     * @see RobotEventListener
     */
    @Override
    public void wantsVisionMode()
    {

    }

    /**
     * Method that Runs (Implementing RobotEventListener) Whenever The Computer 'Wants Intake Mode'
     * Runs Based on a Android 'Broadcast' Setup
     * @see RobotEventListener
     */
    @Override
    public void wantsIntakeMode()
    {

    }

    /**
     * Enclosed Class that Runs Methods When the Power Source Changes
     */
    private class PowerStateBroadcastReceiver extends BroadcastReceiver
    {
        /**
         * Setup the Android Intent Filer Callbacks
         * @param activity - The Vision Activity (enclosing class)
         */
        public PowerStateBroadcastReceiver(VisionTrackerActivity activity)
        {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            activity.registerReceiver(this, intentFilter);
        }

        /**
         * Updates the Battery Text When The Power Source Changes
         * @param context - Android Context
         * @param intent - Purpose / Message of the Call
         */
        @Override
        public void onReceive(Context context, Intent intent)
        {
            VisionTrackerActivity.this.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    updateBatteryText();
                }
            });
        }
    }

    private PowerStateBroadcastReceiver mPbr;

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment
    {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            ActivityCompat.requestPermissions(parent.getActivity(),
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    Activity activity = parent.getActivity();
                                    if (activity != null)
                                    {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    /**
     * Enclosed Dialog Class that Shows a Simple Error Popup
     */
    public static class ErrorDialog extends DialogFragment
    {
        private static final String ARG_MESSAGE = "message";

        /**
         *
         * @param message - String Message to Be Stored and Displayed in the Error Dialog
         * @return An Error Dialog Instance
         */
        public static ErrorDialog newInstance(String message)
        {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        /**
         * Runs and Sets Up a Dialog
         * @param savedInstanceState - Android Bundle / Past Info
         * @return A Dialog
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * Makes Sure that the Application has the Necessary Camera Permission (Newer Android Version Standard)
     * TODO: Bug - On the first run, after giving it the necessary permission, the camera is still blank until restart
     */
    private void requestCameraPermission()
    {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
        {
            new ConfirmationDialog().show(this.getFragmentManager(), FRAGMENT_DIALOG);
        }
        else
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * Reacts the User Input of When the Camera Permissions are Called
     * @param requestCode - Code to Determine Which Permissions Callback Is It
     * @param permissions - String Array of Permission
     * @param grantResults - Results Integer Array
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                ErrorDialog.newInstance(getString(R.string.request_permission)).show(getFragmentManager(), FRAGMENT_DIALOG);
            }
            else
            {
                tryStartCamera();
            }
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Android Lifecycle for When the Activity Starts
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        mIsRunning = true;
        Log.i("VisionActivity", "onStart");
    }

    /**
     * Android Lifecycle for When the Activity Restarts
     */
    @Override
    protected void onRestart()
    {
        super.onRestart();
        Log.i("VisionActivity", "onRestart");
    }

    /**
     * Android Lifecycle Creation
     * @param savedInstanceState - Android Saved Activity State
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        // Init app prefs
        m_prefs = new Preferences(this);

        setContentView(R.layout.activity);
        tryStartCamera();

        connectionStateView = findViewById(R.id.connectionState);
        mLockButton = findViewById(R.id.lockButton);
        mViewTypeButton = findViewById(R.id.viewSelectButton);
        mPrefsButton = findViewById(R.id.hsvEditButton);
        mBatteryText = findViewById(R.id.battery_text);
        mTestInterfaceText = findViewById(R.id.test_text_view);
        mChargingIcon = findViewById(R.id.chargingIcon);

        updateBatteryText();

        rbr = new RobotConnectionStatusBroadcastReceiver(this, this);
        rer = new RobotEventBroadcastReceiver(this, this);

        // Listen for power events
        mPbr = new PowerStateBroadcastReceiver(this);

        if (sLocked)
        {
            setLockOn();
        }
        else
        {
            setLockOff();
        }

        mLockButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (!sLocked)
                {
                    setLockOn();
                }
            }
        });

        mLockButton.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                if (sLocked)
                {
                    setLockOff();
                    //TODO: Solve java.lang.NullPointerException: Attempt to read from field 'android.content.Intent com.android.server.am.TaskRecord.intent' on a null object reference
                    return true;
                }
                return false;
            }
        });

        mCalibrationTestHandler = new Handler();
        mCalibrationTestRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if(mCalibrationToast != null)
                    mCalibrationToast.cancel();

                String response = mView.simpleVisionCalc(mView.getLastTargets());
                mCalibrationToast = Toast.makeText(getApplicationContext(), response, Toast.LENGTH_SHORT);
                if(!response.equals(""))
                    mCalibrationToast.show();

                mCalibrationTestHandler.postDelayed(this, 1000);
            }
        };

        MjpgServer.getInstance().pause();

        whitelistLockTasks();

        Log.i("VisionActivity", "onCreate");
    }

    /**
     * Attempts to Start the Camera, Nothing Otherwise
     */
    private void tryStartCamera()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {
            requestCameraPermission();
            return;
        }
        mView = findViewById(R.id.my_gl_surface_view);
        mView.setCameraTextureListener(mView);
        mView.setPreferences(m_prefs);
        TextView tv = findViewById(R.id.fps_text_view);
        mProcMode = findViewById(R.id.proc_mode_text_view);
        mView.setProcessingMode(NativePart.DISP_MODE.TARGETS_PLUS);
        //TODO: MjpegServer Pause?
        MjpgServer.getInstance().pause();
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                updateProcModeText();
            }
        });
    }

    /**
     * Android Lifecycle - What to do When the Application is Paused
     */
    @Override
    protected void onPause()
    {
        Log.i("VisionTrackerActivity", "onPause");
        if (mView != null)
        {
            mView.onPause();
        }
        if (mUpdateViewTimer != null)
        {
            mUpdateViewTimer.cancel();
            mUpdateViewTimer = null;
        }
        mIsRunning = false;
        super.onPause();
    }

    /**
     * Android Lifecycle - What to do When the Application is Resumed
     */
    @Override
    protected void onResume()
    {
        Log.i("VisionActivity", "onResume " + mView);
        super.onResume();
        if (mView != null)
        {
            mView.onResume();
        }
        mUpdateViewTimer = new Timer();
        mUpdateViewTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        updateBatteryText();
                    }
                });
            }
        }, 0, 60000);
        mIsRunning = true;
    }

    /**
     * Allows the Options Menu to Open Up
     * @param menu - The Menu to Be Inflated
     * @return The Boolean From Super
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Opens the Option Menu, Without Regard to View
     * @param v - View (Ignored)
     */
    public void showViewOptions(View v)
    {
        mView.openOptionsMenu();
    }


    /**
     * Displays Calibration Data on the Field
     * @param v - View (Ignored)
     */
    public void toggleFieldTest(View v)
    {
        if(mIsToggledFieldTest)
        {
            mTestInterfaceText.setText("\n\n\n\nField Calibration Data Test");
            mCalibrationTestHandler.post(mCalibrationTestRunnable);
        }
        else
        {
            mCalibrationTestHandler.removeCallbacks(mCalibrationTestRunnable);
            mTestInterfaceText.setText("");
        }
        mIsToggledFieldTest = !mIsToggledFieldTest;
    }

    /**
     * Runs Whenever the Vision Mode Menu Changes
     * @param item - Menu Item Selected
     * @return Simple Boolean
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.raw:
                mView.setProcessingMode(NativePart.DISP_MODE.RAW);
                break;
            case R.id.thresh:
                mView.setProcessingMode(NativePart.DISP_MODE.THRESH);
                break;
            case R.id.targets:
                mView.setProcessingMode(NativePart.DISP_MODE.TARGETS);
                break;
            case R.id.targets_plus:
                mView.setProcessingMode(NativePart.DISP_MODE.TARGETS_PLUS);
                break;
            default:
                return false;
        }

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                updateProcModeText();
            }
        });
        return true;
    }

    /**
     * Method That Opens the HSV Settings Change Panel Over As An Overlay of the Display
     * @param v - View (Not Really Used, But Needed for the Method to be Valid for this Kind of Stuff)
     */
    public void openBottomSheet(View v)
    {
        View view = getLayoutInflater().inflate(R.layout.hsv_bottom_sheet, null);
        LinearLayout container = (LinearLayout) view.findViewById(R.id.popup_window);
        container.getBackground().setAlpha(20);

        final Dialog mBottomSheetDialog = new Dialog(VisionTrackerActivity.this, R.style.MaterialDialogSheet);
        mBottomSheetDialog.setContentView(view);
        mBottomSheetDialog.setCancelable(true);
        mBottomSheetDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mBottomSheetDialog.getWindow().setGravity(Gravity.BOTTOM);
        mBottomSheetDialog.show();

        //Changes the OpenCV Color Range H Value Based on User Changes
        final RangeSeekBar hSeekBar = (RangeSeekBar) view.findViewById(R.id.hSeekBar);
        setSeekBar(hSeekBar, getHRange());
        hSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>()
        {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max)
            {
                Log.i("H", min + " " + max);
                Log.d("HValue", min + " " + max);
                //m_prefs.setThresholdHRange(min, max);
                m_prefs.setThresholdHRange(max, min);
            }
        });

        //Changes the OpenCV Color Range S Value Based on User Changes
        final RangeSeekBar sSeekBar = (RangeSeekBar) view.findViewById(R.id.sSeekBar);
        setSeekBar(sSeekBar, getSRange());
        sSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>()
        {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max)
            {
                Log.i("S", min + " " + max);
                Log.d("SValue", min + " " + max);
                //m_prefs.setThresholdSRange(min, max);
                m_prefs.setThresholdSRange(max, min);
            }
        });

        //Changes the OpenCV Color Range V Value Based on User Changes
        final RangeSeekBar vSeekBar = (RangeSeekBar) view.findViewById(R.id.vSeekBar);
        setSeekBar(vSeekBar, getVRange());
        vSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>()
        {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max)
            {
                Log.i("V", min + " " + max);
                Log.d("VValue", min + " " + max);
                //m_prefs.setThresholdVRange(min, max);
                m_prefs.setThresholdVRange(max, min);
            }
        });

        //Button that Restores the XML Defined Default Settings
        Button restoreButton = (Button) view.findViewById(R.id.restoreDefaultsButton);
        restoreButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                m_prefs.restoreDefaults();
                setSeekBar(hSeekBar, getHRange());
                setSeekBar(sSeekBar, getSRange());
                setSeekBar(vSeekBar, getVRange());
            }
        });
    }

    /**
     * Places a Specified SeekBar to a Min and Max Value Pair
     * @param bar - SeekerBar to be Set
     * @param values - Pair of Values to Set the Bar to
     */
    private static void setSeekBar(RangeSeekBar<Integer> bar, Pair<Integer, Integer> values)
    {
        bar.setSelectedMinValue(values.first);
        bar.setSelectedMaxValue(values.second);
    }

    /**
     * Gets the Current H Value Setting Range - Really Just A Simple Wrapper for One Function
     * @return A Pair of Integers Representing the Max and Min of the Range
     */
    public Pair<Integer, Integer> getHRange()
    {
        return m_prefs.getThresholdHRange();
    }

    /**
     * Gets the Current S Value Setting Range - Really Just A Simple Wrapper for One Function
     * @return A Pair of Integers Representing the Max and Min of the Range
     */
    public Pair<Integer, Integer> getSRange()
    {
        return m_prefs.getThresholdSRange();
    }

    /**
     * Gets the Current V Value Setting Range - Really Just A Simple Wrapper for One Function
     * @return A Pair of Integers Representing the Max and Min of the Range
     */
    public Pair<Integer, Integer> getVRange()
    {
        return m_prefs.getThresholdVRange();
    }

    /**
     * Reaction to the Robot Become Connected (Responsible for the Color Color Change)
     * @see RobotConnectionStateListener
     */
    @Override
    public void robotConnected()
    {
        Log.i("MainActivity", "Robot Connected");
        mView.setRobotConnection(AppContext.getRobotConnection());
        connectionStateView.setBackgroundColor(ContextCompat.getColor(this, R.color.cheesy_poof_blue));
        stopBadConnectionAnimation();
    }

    /**
     * Android Lifecycle Method - Stops All the Method Broadcast Receiver Callbacks
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(rbr);
        unregisterReceiver(mPbr);
        unregisterReceiver(rer);
    }

    /**
     * Starts the (Very Irritating) Animation that Occurs Whenever the Connection is Considered Bad
     */
    public void startBadConnectionAnimation()
    {
        Animation animation = new ScaleAnimation(1, 1, 1, 20, Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0);
        animation.setDuration(200);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        connectionStateView.startAnimation(animation);
    }

    /**
     * Stops the Above Animation Effect
     */
    public void stopBadConnectionAnimation()
    {
        connectionStateView.clearAnimation();
    }

    /**
     * Called When the Robot is Disconnected, and Decides What Screen Animation to Show
     * @see RobotConnectionStateListener
     */
    @Override
    public void robotDisconnected()
    {
        Log.i("MainActivity", "Robot Disconnected");
        mView.setRobotConnection(null);
        connectionStateView.setBackgroundColor(ContextCompat.getColor(this, R.color.holo_red_light));
        if (isLocked())
        {
            startBadConnectionAnimation();
        }
        else
        {
            stopBadConnectionAnimation();
        }
    }

    /**
     * Responsible for the Screen Setup When the 'Lock' is On
     * Sets sLocked true
     */
    public void setLockOn()
    {
        sLocked = true;
        mLockButton.setImageResource(R.drawable.locked);
        mLockButton.clearAnimation();
        mLockButton.setBackgroundColor(Color.TRANSPARENT);
        mLockButton.setAlpha(0.75f);
        mPrefsButton.setVisibility(View.GONE);
        mViewTypeButton.setVisibility(View.GONE);
        startLockTask();
    }

    /**
     * Responsible for the Screen Setup When the 'Lock' is Off
     * Sets sLocked false
     */
    public void setLockOff()
    {
        sLocked = false;
        mPrefsButton.setVisibility(View.VISIBLE);
        mViewTypeButton.setVisibility(View.VISIBLE);
        mLockButton.setImageResource(R.drawable.unlocked);
        mLockButton.setBackgroundColor(Color.RED);
        mLockButton.setAlpha(1.0f);
        Animation animation = new AlphaAnimation(1, 0);
        animation.setDuration(350);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        mLockButton.startAnimation(animation);
        stopLockTask();
        stopBadConnectionAnimation();
    }

    /**
     * Allows the Locking Tasks to Run (If Able to do With a 'Device Manager'
     */
    private void whitelistLockTasks()
    {
        DevicePolicyManager manager =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName componentName = AppDeviceAdminReceiver.getComponentName(this);

        try
        {
            if (manager.isDeviceOwnerApp(getPackageName()))
            {
                manager.setLockTaskPackages(componentName, new String[]{getPackageName()});
            }
        }
        catch (NullPointerException npe)
        {
            npe.printStackTrace();
        }
    }

    /**
     * Makes the Application Able to Use Device Administrator Abilities if Enabled
     * TODO: Currently Not In Use, Make Subject to Code Cleanups?
     */
    private void enableDeviceAdmin()
    {
        DevicePolicyManager manager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName componentName = AppDeviceAdminReceiver.getComponentName(this);

        try
        {
            if(!manager.isAdminActive(componentName))
            {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                startActivityForResult(intent, 0);
                return;
            }
        }
        catch (NullPointerException npe)
        {
            npe.printStackTrace();
        }
    }

    /**
     * Closes All of the Dialog Windows Whenever the Window 'Focus' Changes (Not Camera Focus)
     * @param hasFocus - Whether this comes as an increase in focus or not
     * @see "https://stackoverflow.com/questions/7924296/how-to-use-onwindowfocuschanged-method"
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus && sLocked)
        {
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }

    /**
     * Updates the Overlay Battery Text
     * TODO: Review Android Text Warning Solutions
     */
    private void updateBatteryText()
    {
        Intent batteryStatus =
                registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPercentage = 100f * level / scale;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        mBatteryText.setText(Integer.toString((int)batteryPercentage) + "%");
        mChargingIcon.setVisibility(isCharging ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the Text For the Overlay Telling Which Mode the Camera is In, Whenever the Mode Changes
     */
    private void updateProcModeText()
    {
        mProcMode.setText("Proc Mode: "
                + VisionTrackerGLSurfaceView.PROC_MODE_NAMES[mView.getProcessingMode().getNumber()]);
    }

    /**
     * Plays an Airhorn Sound (duh)
     */
    public void playAirhorn()
    {
        MediaPlayer mp = MediaPlayer.create(this, R.raw.airhorn);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mp)
            {
                mp.reset();
                mp.release();
            }
        });
        mp.start();
    }
}
