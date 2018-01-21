package com.androidFRC.androidVision;

import com.androidFRC.androidVision.comm.CameraTargetInfo;
import com.androidFRC.androidVision.comm.RobotConnection;
import com.androidFRC.androidVision.comm.VisionUpdate;
import com.androidFRC.androidVision.comm.messages.TargetUpdateMessage;
import com.androidFRC.androidVision.math.Rotation2d;

import org.opencv.android.BetterCameraGLSurfaceView;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import static android.content.Context.SENSOR_SERVICE;

/**
 * The Surface that Shows the Camera Output (Through Computer Vision)
 */
public class VisionTrackerGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener
{
    //String Variables
    static final String LOGTAG = "VTGLSurfaceView";
    public static final String[] PROC_MODE_NAMES = new String[]{"Raw image", "Threshholded image", "Targets", "Targets plus"};

    //Constants for On-Field (Camera Only) Vision Math
    private static final double kCubeCenterHeight = 6;
    private static final double kCubeCircularRadius = 13 / 2.0;
    private static final double kCameraDeadband = 0.001;
    private static final double kCameraHeight = 31;
    private static final double kHeightDifference = kCubeCenterHeight - kCameraHeight;

    //Values Updated by Sensors
    private float[] accelerometerValues;
    private float[] magnetometerValues;

    //Sensors Required for Easy On-Field Vision
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private NativePart.TargetsInfo lastTargets;

    //Listener for the Changing Values
    private SensorEventListener sensorListener = new SensorEventCallback()
    {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent)
        {
            super.onSensorChanged(sensorEvent);
            if(sensorEvent.sensor == accelerometer)
            {
                accelerometerValues = sensorEvent.values;
            }
            else if(sensorEvent.sensor == magnetometer)
            {
                magnetometerValues = sensorEvent.values;
            }
        }
    };

    //State Variables for Time Management
    protected int frameCounter;
    protected long lastNanoTime;

    //Assorted State Variables
    protected NativePart.DISP_MODE procMode = NativePart.DISP_MODE.TARGETS_PLUS;
    TextView mFpsText = null;
    private RobotConnection mRobotConnection;
    private Preferences m_prefs;

    //Height and Width of Image Process, and Related Variable
    //TODO: Should this be the way?
    static final int kHeight = Configuration.VIDEO_HEIGHT;
    static final int kWidth = Configuration.VIDEO_WIDTH;

    //These Variables are Related the the 'Homogeneous Vectors' the CheezyPoofs Use - See Below
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    //Two Buffers of Bytes for The C++ Image to Be Transferred Back Safely
    private boolean byteArraySwitch;
    private boolean shouldArrayBeStreamed = Configuration.DEFAULT_SHOULD_VIDEO_STREAM;
    private final ByteBuffer bufferA = ByteBuffer.allocate(kWidth * kHeight * 4 + 4);
    private final ByteBuffer bufferB = ByteBuffer.allocate(kWidth * kHeight * 4 + 4);

    /**
     * Static Creates a New Pair
     * @return A New Pair of Two Integers (0, 255)
     */
    private static Pair<Integer, Integer> blankPair()
    {
        return new Pair<Integer, Integer>(0, 255);
    }

    /**
     * Constructor for the VisionTrackerGLSurface off of BetterCameraGLSurface
     * Only Runs Super and One Instantiation (New in AndroidVision)
     * @param context - Android 'Context' from an Activity
     * @param attrs - Attributes
     */
    public VisionTrackerGLSurfaceView(Context context, AttributeSet attrs)
    {
        super(context, attrs, Configuration.getCameraSettings());
        byteArraySwitch = true;
    }

    /**
     * Passes Along An openOptionsMenu to the Context
     */
    public void openOptionsMenu()
    {
        ((Activity) getContext()).openOptionsMenu();
    }

    /**
     * Lifecycle Method - Unaltered Creation
     * @param holder - Holder for the Surface in the SurfaceView
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        super.surfaceCreated(holder);
    }

    /**
     * Lifecycle Method - Unaltered Destruction
     * @param holder - Holder for the Surface in the SurfaceView
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        super.surfaceDestroyed(holder);
    }

    /**
     * Changes the 'Processing Mode' Integer
     * @param newMode - New Processing Mode Integer
     */
    public void setProcessingMode(NativePart.DISP_MODE newMode)
    {
        procMode = newMode;
    }

    /**
     * Sets Robot Connection (robotConnection)
     * @param robotConnection - New RobotConnection
     */
    public void setRobotConnection(RobotConnection robotConnection)
    {
        mRobotConnection = robotConnection;
    }

    /**
     * Sets Preferences (m_prefs)
     * @param prefs - New Preference
     */
    public void setPreferences(Preferences prefs)
    {
        m_prefs = prefs;
    }

    /**
     * Returns the Last Array Of Generated Targets
     * @return the Last Array Of Generated Targets
     */
    public NativePart.TargetsInfo getLastTargets()
    {
        return lastTargets;
    }

    /**
     * Returns the Local Vision Processing Mode (Kind of View)
     * @return procMode - The Current Processing Mode
     */
    public NativePart.DISP_MODE getProcessingMode()
    {
        return procMode;
    }

    /**
     * Sets Up the Camera View State Variables
     * @param width  -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height)
    {
        ((Activity) getContext()).runOnUiThread(new Runnable()
        {
            public void run()
            {
                Toast.makeText(getContext(), "onCameraViewStarted", Toast.LENGTH_SHORT).show();
            }
        });

        frameCounter = 0;
        lastNanoTime = System.nanoTime();
    }

    /**
     * Tells the User that the Camera View Stopped
     */
    @Override
    public void onCameraViewStopped()
    {
        ((Activity) getContext()).runOnUiThread(new Runnable()
        {
            public void run()
            {
                Toast.makeText(getContext(), "onCameraViewStopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * On Every Image Update to the Screen, This Code Runs (Vision Ops, Screen Updates)
     * @param texIn - The OpenGL texture ID that contains frame in RGBA format
     * @param texOut - The OpenGL texture ID that can be used to store modified frame image to display
     * @param width - The width of the frame
     * @param height - The height of the frame
     * @param image_timestamp - The Time of the Creation of the Image
     * @return Result of the Texture (true)
     */
    @Override
    public boolean onCameraTexture(int texIn, int texOut, int width, int height, long image_timestamp)
    {
        Log.d(LOGTAG, "onCameraTexture - Timestamp " + image_timestamp + ", current time " + System.nanoTime() / 1E9);
        //FPS Counter Incrementer
        frameCounter++;
        if (frameCounter >= 30)
        {
            final int fps = (int) (frameCounter * 1e9 / (System.nanoTime() - lastNanoTime));
            Log.i(LOGTAG, "drawFrame() FPS: " + fps);
            if (mFpsText != null)
            {
                Runnable fpsUpdater = new Runnable()
                {
                    public void run()
                    {
                        mFpsText.setText("FPS: " + fps);
                    }
                };
                new Handler(Looper.getMainLooper()).post(fpsUpdater);
            }
            else
            {
                Log.d(LOGTAG, "mFpsText == null");
                mFpsText = (TextView) ((Activity) getContext()).findViewById(R.id.fps_text_view);
            }
            frameCounter = 0;
            lastNanoTime = System.nanoTime();
        }
        NativePart.TargetsInfo targetsInfo = new NativePart.TargetsInfo();
        Pair<Integer, Integer> hRange = m_prefs != null ? m_prefs.getThresholdHRange() : blankPair();
        Pair<Integer, Integer> sRange = m_prefs != null ? m_prefs.getThresholdSRange() : blankPair();
        Pair<Integer, Integer> vRange = m_prefs != null ? m_prefs.getThresholdVRange() : blankPair();

        //Runs the Native C++ Code (See jni.c -> image_processor.cpp)
        //Switches Between Two Arrays to Be Filled by the Process Frame and Set Image C++ Method
        //TODO: Add Option to Not Send Image
        if(shouldArrayBeStreamed)
        {
            NativePart.processFrameAndSetImage(texIn, texOut, width, height, procMode.getNumber(), Math.min(hRange.first, hRange.second), Math.max(hRange.first, hRange.second),
                    Math.min(sRange.first, sRange.second), Math.max(sRange.first, sRange.second), Math.min(vRange.first, vRange.second), Math.max(vRange.first, vRange.second), byteArraySwitch ? bufferA.array() : bufferB.array(), targetsInfo);

            long timeCheck = System.currentTimeMillis();
            byte[] byteArray = byteArraySwitch ? bufferA.array() : bufferB.array();
            //https://stackoverflow.com/questions/2383265/convert-4-bytes-to-int
            int lengthOfByteArray = ((0x000000ff & byteArray[width * height * 4]) << 24) |
                    ((0x000000ff & byteArray[width * height * 4 + 1]) << 16) |
                    ((0x000000ff & byteArray[width * height * 4 + 2]) << 8) |
                    ((0x000000ff & byteArray[width * height * 4 + 3]));
            MjpgServer.getInstance().update(Arrays.copyOfRange(byteArray, 0, lengthOfByteArray));
            byteArraySwitch = !byteArraySwitch;
            Log.d(LOGTAG, "MJPG Uploading Costs " + (System.currentTimeMillis() - timeCheck) + "ms");
        }
        else
        {
            NativePart.processFrame(texIn, texOut, width, height, procMode.getNumber(), hRange.first, hRange.second,
                    sRange.first, sRange.second, vRange.first,vRange.second, targetsInfo);
        }

        VisionUpdate visionUpdate = new VisionUpdate(image_timestamp);
        Log.i(LOGTAG, "Num targets = " + targetsInfo.numTargets);

        for (int i = 0; i < targetsInfo.numTargets && i < targetsInfo.targets.length; ++i)
        {
            NativePart.TargetsInfo.Target target = targetsInfo.targets[i];

            /**
             * "Convert to a homogeneous 3d vector with x = 1
             * This is a seemingly strange operation, but it actually allows for some pretty neat vision operations
             * Basically, it is treated like a vector (only y and z, since x is distance (scale) in their model)
             * So, distance can be calculated easily taking into account Robot Pitch and Yaw
             * @see "https://stackoverflow.com/questions/29199480/what-is-the-use-of-homogeneous-vectors-in-computer-vision"
             * @see "https://prateekvjoshi.com/2014/06/13/the-concept-of-homogeneous-coordinates/"
             *
             * Uncomment the two lnes after y and z if youu want to deal with pixels, not vectors
             * double y = target.centroidX;
             * double z = target.centroidY;
             */
            double y = -(target.centroidX - kCenterCol) / getFocalLengthPixels();
            double z = (target.centroidY - kCenterRow) / getFocalLengthPixels();

            Log.i(LOGTAG, "Target at: " + y + ", " + z);
            visionUpdate.addCameraTargetInfo(new CameraTargetInfo(y, z));
        }

        if (mRobotConnection != null)
        {
            TargetUpdateMessage update = new TargetUpdateMessage(visionUpdate, System.nanoTime());
            mRobotConnection.send(update);
        }

        lastTargets = targetsInfo;

        //TODO: This seems to slow the memory bumps, any major effect on speed?
        System.gc();
        return true;
    }

    public String simpleVisionCalc(NativePart.TargetsInfo targets)
    {
        if(sensorManager == null)
        {
            sensorManager = (SensorManager) getContext().getApplicationContext().getSystemService(SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD, true);
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }

        if(accelerometerValues == null || magnetometerValues == null)
        {
            Toast.makeText(getContext(), "Getting Sensor Data", Toast.LENGTH_SHORT).show();
            return "";
        }

        float[] rValues = new float[9];
        SensorManager.getRotationMatrix(rValues, null, accelerometerValues, magnetometerValues);

        float[] gyroValues = new float[3];
        gyroValues = SensorManager.getOrientation(rValues, gyroValues);

        double currentPhoneYawDegrees =
                (-Math.toDegrees(gyroValues[1]));
        //double currentPhoneYawDegrees = -Math.toDegrees(gyroValues[1]);
        //double currentPhonePitchDegrees = (Math.toDegrees(gyroValues[2]) + 90);
        double currentPhonePitchDegrees = (Math.toDegrees(gyroValues[2]) + 90);
        Rotation2d cameraYaw = Rotation2d.fromDegrees(currentPhoneYawDegrees);
        Rotation2d cameraPitch = Rotation2d.fromDegrees(currentPhonePitchDegrees);

        if(targets == null || targets.numTargets <= 0 || targets.targets.length <= 0)
        {
            Log.d(LOGTAG, "Target Error");
            return "";
        }

        NativePart.TargetsInfo.Target target = targets.targets[0];

        double y = ((target.centroidX - kCenterCol) / getFocalLengthPixels());
        //double y = -(target.centroidX - kCenterCol) / getFocalLengthPixels();
        double z = (target.centroidY - kCenterRow) / getFocalLengthPixels();
        //double z = (target.centroidY - kCenterRow) / getFocalLengthPixels();

        Log.d(LOGTAG, "Yaw " + currentPhoneYawDegrees + " Pitch " + currentPhonePitchDegrees + " Y " + y + " Z " + z);

        double yDeadband = (y > -kCameraDeadband) && (y < kCameraDeadband) ? 0.0 : y;

        double xYaw;
        double yYaw;
        double zYaw;


            xYaw = (cameraYaw.cos() + yDeadband * cameraYaw.sin());
            yYaw = (yDeadband * cameraYaw.cos() - cameraYaw.sin());
            zYaw = z;


            /*
            xYaw = cameraYaw.cos() - yDeadband * cameraYaw.sin();
            yYaw = -yDeadband * cameraYaw.cos() - cameraYaw.sin();
            zYaw = z;
            */

        double xR = zYaw * cameraPitch.sin() + xYaw * cameraPitch.cos();
        double yR = yYaw;
        //double zR = (zYaw * cameraPitch.cos() - xYaw * cameraPitch.sin());
        double zR = (zYaw * cameraPitch.cos() - xYaw * cameraPitch.sin());

        Log.d(LOGTAG, "xYaw: "  + xYaw + " yYaw: " + yYaw + " zYaw " + zYaw + " xR: " + xR + " yR: " + yR + " zR: " + zR + " fl " + getFocalLengthPixels());

        //if((zR > 0 && kHeightDifference > 0) || (zR < 0 && kHeightDifference < 0))
        if(true)
        {
            double scaling = kHeightDifference / zR;
            //double distance = Math.hypot(xR, yR) * scaling / 2 + kCubeCircularRadius;
            double distance = (Math.hypot(xR, yR) * scaling + kCubeCircularRadius);
            Rotation2d angle = new Rotation2d(xR, yR, true);
            return (String.format(Locale.US, "D: %5.1f in A: %3.0f °", distance, -angle.getDegrees()));
        }
        else
        {
            Log.d(LOGTAG, "Vision Cal Math Error");
            return "";
        }
    }
}
