package com.androidFRC.androidVision;

import android.hardware.camera2.CaptureRequest;

import org.opencv.android.BetterCamera2Renderer;

import java.util.HashMap;

/**
 * Final Class that Handles Java Code Permanent Settings
 * Other Application Settings Can Be Found in XML, i.e. menu.xml, strings.xml, etc...
 */
public final class Configuration
{
    //Specifications for the Video Stream Quality
    public static final int VIDEO_HEIGHT = 480;
    public static final int VIDEO_WIDTH = 640;

    //Constants For The Robot Connection Information
    public static final int ROBOT_PORT = 8254;
    public static final String ROBOT_PROXY_HOST = "localhost";
    public static final int CONNECTOR_SLEEP_MS = 100;
    public static final int THRESHOLD_HEARTBEAT = 800;
    public static final int SEND_HEARTBEAT_PERIOD = 100;

    //Constant for the Video Connection Information
    public static final int VIDEO_PORT = 5800;
    public static final boolean DEFAULT_SHOULD_VIDEO_STREAM = true;

    //WakeLock Acquiring Timeout Constant (if 0, it will Assume No Timeout [1Sec * 1000ms/sec])
    public static final int WAKE_LOCK_ACQUIRE_TIMEOUT =  1000;

    /**
     * Instantiates a List of Camera Settings and Fills it into a 'BetterCamera2Renderer.Settings'
     * If Camera is too bright or dark, adjust the Exposure Time Below
     * @see "https://stackoverflow.com/questions/28429071/camera-preview-is-too-dark-in-low-light-android"
     * @see "https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#SENSOR_EXPOSURE_TIME"
     * @return settings - A List of Camera Settings
     */
    static BetterCamera2Renderer.Settings getCameraSettings()
    {
        BetterCamera2Renderer.Settings settings = new BetterCamera2Renderer.Settings();
        settings.height = VIDEO_HEIGHT;
        settings.width = VIDEO_WIDTH;
        settings.camera_settings = new HashMap<>();
        settings.camera_settings.put(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_FOCUS_DISTANCE, .2f);
        settings.camera_settings.put(CaptureRequest.SENSOR_EXPOSURE_TIME, 10000000L);
        return settings;
    }

    private Configuration() {}
}
