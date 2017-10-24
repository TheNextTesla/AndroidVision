package com.team254.cheezdroid;

import com.team254.cheezdroid.comm.CameraTargetInfo;
import com.team254.cheezdroid.comm.RobotConnection;
import com.team254.cheezdroid.comm.VisionUpdate;
import com.team254.cheezdroid.comm.messages.TargetUpdateMessage;
import com.team254.cheezdroid.comm.messages.VisionMessage;

import org.opencv.android.BetterCamera2Renderer;
import org.opencv.android.BetterCameraGLSurfaceView;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CaptureRequest;
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
import java.util.HashMap;

public class VisionTrackerGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener {

    //String Variables
    //TODO: Reorganize into a Configuration File
    static final String LOGTAG = "VTGLSurfaceView";
    public static final String[] PROC_MODE_NAMES = new String[]{"Raw image", "Threshholded image", "Targets", "Targets plus"};

    //State Variables for Time Management
    protected int frameCounter;
    protected long lastNanoTime;

    //TODO: Make procMode into an enum
    protected int procMode = NativePart.DISP_MODE_TARGETS_PLUS;
    TextView mFpsText = null;
    private RobotConnection mRobotConnection;
    private Preferences m_prefs;

    //Height and Width of Image Process, and Related Variable
    static final int kHeight = 480;
    static final int kWidth = 640;

    //These Variables are Related the the 'Homogeneous Vectors the CheezyPoofs Use' - See Below
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    //Two Buffers of Bytes for The C++ Image to Be Transferred Back Safely
    private boolean byteArraySwitch;
    private final ByteBuffer bufferA = ByteBuffer.allocate(kWidth * kHeight * 4 + 4);
    private final ByteBuffer bufferB = ByteBuffer.allocate(kWidth * kHeight * 4 + 4);

    /**
     * Instantiates a List of Camera Settings and Fills it into a 'BetterCamera2Renderer.Settings'
     * @return settings - A List of Camera Settings
     */
    static BetterCamera2Renderer.Settings getCameraSettings() {
        BetterCamera2Renderer.Settings settings = new BetterCamera2Renderer.Settings();
        settings.height = kHeight;
        settings.width = kWidth;
        settings.camera_settings = new HashMap<>();
        settings.camera_settings.put(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_FOCUS_DISTANCE, .2f);
        settings.camera_settings.put(CaptureRequest.SENSOR_EXPOSURE_TIME, 10000000L);
        //If Camera is too bright or dark, adjust the Exposure Time Above
        //@see "https://stackoverflow.com/questions/28429071/camera-preview-is-too-dark-in-low-light-android"
        //@see "https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#SENSOR_EXPOSURE_TIME"
        return settings;
    }

    /**
     * Static Creates a New Pair
     * @return A New Pair of Two Integers (0, 255)
     */
    private static Pair<Integer, Integer> blankPair() {
        return new Pair<Integer, Integer>(0, 255);
    }

    /**
     * Constructor for the VisionTrackerGLSurface off of BetterCameraGLSurface
     * Only Runs Super and One Instantiation (New in AndroidVision)
     * @param context - Android 'Context' from an Activity
     * @param attrs - Attributes
     */
    public VisionTrackerGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs, getCameraSettings());
        byteArraySwitch = true;
    }

    /**
     * Passes Along An openOptionsMenu to the Context
     */
    public void openOptionsMenu() {
        ((Activity) getContext()).openOptionsMenu();
    }

    /**
     * Lifecycle Method - Unaltered Creation
     * @param holder - Holder for the Surface in the SurfaceView
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    /**
     * Lifecycle Method - Unaltered Destruction
     * @param holder - Holder for the Surface in the SurfaceView
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
    }

    /**
     * Changes the 'Processing Mode' Integer
     * TODO: Change to Enum For Easier Use (WHY IN THE WORLD IS IT AN IT)
     * @param newMode - New Processing Mode Integer
     */
    public void setProcessingMode(int newMode) {
        if (newMode >= 0 && newMode < PROC_MODE_NAMES.length)
            procMode = newMode;
        else
            Log.e(LOGTAG, "Ignoring invalid processing mode: " + newMode);
    }

    /**
     * Sets Robot Connection (robotConnection)
     * @param robotConnection - New RobotConnection
     */
    public void setRobotConnection(RobotConnection robotConnection) {
        mRobotConnection = robotConnection;
    }

    /**
     * Sets Preferences (m_prefs)
     * @param prefs - New Preference
     */
    public void setPreferences(Preferences prefs) {
        m_prefs = prefs;
    }

    /**
     * Returns the Local Vision Processing Mode (Kind of View)
     * @return procMode - The Current Processing Mode
     */
    public int getProcessingMode() {
        return procMode;
    }

    /**
     * Sets Up the Camera View State Variables
     * @param width  -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), "onCameraViewStarted", Toast.LENGTH_SHORT).show();
            }
        });
        // NativePart.initCL();
        frameCounter = 0;
        lastNanoTime = System.nanoTime();
    }

    /**
     * Tells the User that the Camera View Stopped
     */
    @Override
    public void onCameraViewStopped() {
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), "onCameraViewStopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * On Every Image Update to the Screen, This Code Runs (Vision Ops, Screen Updates)
     * @param texIn - The OpenGL texture ID that contains frame in RGBA format
     * @param texOut - The OpenGL texture ID that can be used to store modified frame image t display
     * @param width - The width of the frame
     * @param height - The height of the frame
     * @param image_timestamp - The Time of the Creation of the Image
     * @return Result of the Texture (true)
     */
    @Override
    public boolean onCameraTexture(int texIn, int texOut, int width, int height, long image_timestamp) {
        Log.d(LOGTAG, "onCameraTexture - Timestamp " + image_timestamp + ", current time " + System.nanoTime() / 1E9);
        //FPS Counter Incrementer
        frameCounter++;
        if (frameCounter >= 30) {
            final int fps = (int) (frameCounter * 1e9 / (System.nanoTime() - lastNanoTime));
            Log.i(LOGTAG, "drawFrame() FPS: " + fps);
            if (mFpsText != null) {
                Runnable fpsUpdater = new Runnable() {
                    public void run() {
                        mFpsText.setText("FPS: " + fps);
                    }
                };
                new Handler(Looper.getMainLooper()).post(fpsUpdater);
            } else {
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

        //Runs the Native C++ Code (See jni.c -> image_processor.cpp
        //Switches Between Two Arrays to Be Filled by the Process Frame and Set Image C++ Method
        //TODO: Add Option to Not Send Image
        if(byteArraySwitch)
        {
            NativePart.processFrameAndSetImage(texIn, texOut, width, height, procMode, hRange.first, hRange.second,
                    sRange.first, sRange.second, vRange.first, vRange.second, bufferA.array(), targetsInfo);
        }
        else
        {
            NativePart.processFrameAndSetImage(texIn, texOut, width, height, procMode, hRange.first, hRange.second,
                    sRange.first, sRange.second, vRange.first, vRange.second, bufferB.array(), targetsInfo);
        }
        byteArraySwitch = !byteArraySwitch;

        VisionUpdate visionUpdate = new VisionUpdate(image_timestamp);
        Log.i(LOGTAG, "Num targets = " + targetsInfo.numTargets);

        long timeCheck = System.currentTimeMillis();
        byte[] byteArray = byteArraySwitch ? bufferA.array() : bufferB.array();
        //https://stackoverflow.com/questions/2383265/convert-4-bytes-to-int
        int lengthOfByteArray = ((0x000000ff & byteArray[width * height * 4]) << 24) | ((0x000000ff & byteArray[width * height * 4 + 1]) << 16) | ((0x000000ff & byteArray[width * height * 4 + 2]) << 8) | ((0x000000ff & byteArray[width * height * 4 + 3]));
        MjpgServer.getInstance().update(Arrays.copyOfRange(byteArray, 0, lengthOfByteArray));
        Log.d(LOGTAG, "MJPG Uploading Costs " + (System.currentTimeMillis() - timeCheck) + "ms");

        for (int i = 0; i < targetsInfo.numTargets; ++i) {
            NativePart.TargetsInfo.Target target = targetsInfo.targets[i];

            //TODO: Find the Application of this Conversion.  What does it do to Vision Processing?
            //https://stackoverflow.com/questions/29199480/what-is-the-use-of-homogeneous-vectors-in-computer-vision
            // Convert to a homogeneous 3d vector with x = 1
            //double y = -(target.centroidX - kCenterCol) / getFocalLengthPixels();
            //double z = (target.centroidY - kCenterRow) / getFocalLengthPixels();
            double y = target.centroidX;
            double z = target.centroidY;

            Log.i(LOGTAG, "Target at: " + y + ", " + z);
            visionUpdate.addCameraTargetInfo(new CameraTargetInfo(y, z));
        }

        if (mRobotConnection != null) {
            TargetUpdateMessage update = new TargetUpdateMessage(visionUpdate, System.nanoTime());
            mRobotConnection.send(update);
        }

        //TODO: This seems to slow the memory bumps, any major effect on speed?
        System.gc();
        return true;
    }
}
