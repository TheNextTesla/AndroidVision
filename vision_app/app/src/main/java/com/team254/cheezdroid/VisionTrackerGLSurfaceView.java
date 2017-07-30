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
import android.graphics.Bitmap;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;

public class VisionTrackerGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener {

    static final String LOGTAG = "VTGLSurfaceView";
    protected int procMode = NativePart.DISP_MODE_TARGETS_PLUS;
    public static final String[] PROC_MODE_NAMES = new String[]{"Raw image", "Threshholded image", "Targets", "Targets plus"};
    protected int frameCounter;
    protected long lastNanoTime;
    TextView mFpsText = null;
    private RobotConnection mRobotConnection;
    private Preferences m_prefs;

    static final int kHeight = 480;
    static final int kWidth = 640;
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    private boolean byteArraySwitch;
    private final IntBuffer bufferA = IntBuffer.allocate(kWidth * kHeight);
    private final IntBuffer bufferB = IntBuffer.allocate(kWidth * kHeight);

    static BetterCamera2Renderer.Settings getCameraSettings() {
        BetterCamera2Renderer.Settings settings = new BetterCamera2Renderer.Settings();
        settings.height = kHeight;
        settings.width = kWidth;
        settings.camera_settings = new HashMap<>();
        settings.camera_settings.put(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.SENSOR_EXPOSURE_TIME, 1000000L);
        settings.camera_settings.put(CaptureRequest.LENS_FOCUS_DISTANCE, .2f);
        return settings;
    }

    public VisionTrackerGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs, getCameraSettings());
        byteArraySwitch = true;
    }

    public void openOptionsMenu() {
        ((Activity) getContext()).openOptionsMenu();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
    }

    public void setProcessingMode(int newMode) {
        if (newMode >= 0 && newMode < PROC_MODE_NAMES.length)
            procMode = newMode;
        else
            Log.e(LOGTAG, "Ignoring invalid processing mode: " + newMode);
    }

    public int getProcessingMode() {
        return procMode;
    }

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

    @Override
    public void onCameraViewStopped() {
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), "onCameraViewStopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCameraTexture(int texIn, int texOut, int width, int height, long image_timestamp) {
        Log.d(LOGTAG, "onCameraTexture - Timestamp " + image_timestamp + ", current time " + System.nanoTime() / 1E9);
        // FPS
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

        /*
        NativePart.processFrame(texIn, texOut, width, height, procMode, hRange.first, hRange.second,
                sRange.first, sRange.second, vRange.first, vRange.second, targetsInfo);
        */

        VisionUpdate visionUpdate = new VisionUpdate(image_timestamp);
        Log.i(LOGTAG, "Num targets = " + targetsInfo.numTargets);


        long timeCheck = System.currentTimeMillis();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(byteArraySwitch ? bufferA.array() : bufferB.array(), 0, width, 0, 0, width, height);
        Log.d(LOGTAG, Integer.toHexString(byteArraySwitch ? bufferA.array()[0] : bufferB.array()[0]));
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
        Log.d(LOGTAG, "Bitmap Conversion Costs " + (System.currentTimeMillis() - timeCheck) + "ms");

        //Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //bmp.setPixels(byteArraySwitch ? bufferA.array() : bufferB.array(), 0, width, 0, 0, width, height);
        //int bytes = bmp.getByteCount()
        //ByteBuffer buffer = ByteBuffer.allocate(bytes);
        //bmp.copyPixelsToBuffer(buffer);
        //byte[] byteArray = buffer.array();
        //Log.d(LOGTAG, "Bitmap Conversion Costs " + (System.currentTimeMillis() - timeCheck) + "ms");

        timeCheck = System.currentTimeMillis();
        byte[] byteArray = stream.toByteArray();

        MjpgServer.getInstance().update(byteArray);
        Log.d(LOGTAG, "MJPG Uploading Costs " + (System.currentTimeMillis() - timeCheck) + "ms");

        for (int i = 0; i < targetsInfo.numTargets; ++i) {
            NativePart.TargetsInfo.Target target = targetsInfo.targets[i];

            // Convert to a homogeneous 3d vector with x = 1
            double y = -(target.centroidX - kCenterCol) / getFocalLengthPixels();
            double z = (target.centroidY - kCenterRow) / getFocalLengthPixels();
            Log.i(LOGTAG, "Target at: " + y + ", " + z);
            visionUpdate.addCameraTargetInfo(
                    new CameraTargetInfo(y, z));
        }

        if (mRobotConnection != null) {
            TargetUpdateMessage update = new TargetUpdateMessage(visionUpdate, System.nanoTime());
            mRobotConnection.send(update);
        }
        return true;
    }

    public void setRobotConnection(RobotConnection robotConnection) {
        mRobotConnection = robotConnection;
    }

    public void setPreferences(Preferences prefs) {
        m_prefs = prefs;
    }

    private static Pair<Integer, Integer> blankPair() {
        return new Pair<Integer, Integer>(0, 255);
    }

    public static Bitmap RGBA2ARGB(Bitmap img)
    {

        int width  = img.getWidth();
        int height = img.getHeight();

        int[] pixelsIn  = new int[height*width];
        int[] pixelsOut = new int[height*width];

        img.getPixels(pixelsIn,0,width,0,0,width,height);

        int pixel=0;
        int count=width*height;

        while(count-->0){
            int inVal = pixelsIn[pixel];

            //Get and set the pixel channel values from/to int  //TODO OPTIMIZE!
            int r = (int)( (inVal & 0xff000000)>>24 );
            int g = (int)( (inVal & 0x00ff0000)>>16 );
            int b = (int)( (inVal & 0x0000ff00)>>8  );
            int a = (int)(  inVal & 0x000000ff)      ;

            pixelsOut[pixel] = (int)( a <<24 | r << 16 | g << 8 | b );
            pixel++;
        }

        Bitmap out =  Bitmap.createBitmap(pixelsOut,0,width,width,height, Bitmap.Config.ARGB_8888);
        return out;
    }
}
