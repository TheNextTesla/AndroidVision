package com.team254.cheezdroid;

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
    public static final int VIDEO_PORT = 8200;

    private Configuration() {}
}
