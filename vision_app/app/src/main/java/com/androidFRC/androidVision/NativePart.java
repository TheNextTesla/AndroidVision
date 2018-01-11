package com.androidFRC.androidVision;

/**
 * Class That Interfaces With The C++ Code
 */
public class NativePart
{
    //Sets Up Libraries Static-ly (on Run)
    static
    {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("JNIpart");
    }

    //Sets Up the Values for the C++ Side Enums
    //TODO: Is this really the best way to go about this?
    public enum DISP_MODE
    {
        RAW(0),
        THRESH(1),
        TARGETS(2),
        TARGETS_PLUS(3);

        private final int number;
        DISP_MODE(int number) {this.number = number;}
        public int getNumber() {return number;}
    }

    //Calls Native Code for Processing Frame
    public static native void processFrame(
            int tex1,
            int tex2,
            int w,
            int h,
            int mode,
            int h_min,
            int h_max,
            int s_min,
            int s_max,
            int v_min,
            int v_max,
            TargetsInfo destInfo);

    //Calls Native Code for Processing Frame and Receiving the Image in a Byte Array
    public static native void processFrameAndSetImage(
            int tex1,
            int tex2,
            int w,
            int h,
            int mode,
            int h_min,
            int h_max,
            int s_min,
            int s_max,
            int v_min,
            int v_max,
            byte[] out_dis,
            TargetsInfo destInfo);

    /**
     * These Classes are Used by the C++ Code, For Communicating Back Found Targets to Java
     * Classes referenced from native code, DO NOT CHANGE ANY NAMING!!!!
     */
    public static class TargetsInfo
    {
        /**
         * Enclosed 'Target' Class (Basically A Struct)
         */
        public static class Target
        {
            public double centroidX;
            public double centroidY;
            public double width;
            public double height;
        }

        public int numTargets;
        public final Target[] targets;

        /**
         * Constructor For TargetInfo, Sets Up A Target Array of 3
         */
        public TargetsInfo()
        {
            targets = new Target[3];
            for (int i = 0; i < targets.length; i++)
            {
                targets[i] = new Target();
            }
        }
    }
}
