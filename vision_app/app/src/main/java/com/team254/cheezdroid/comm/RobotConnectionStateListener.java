package com.team254.cheezdroid.comm;

/**
 * Interface that Defines the Method Headers for VisionTrackerActivity's Reciever
 * TODO: Is this *really* necessary?  Seems Like This Could Be Merge With The Other Reciever
 */
public interface RobotConnectionStateListener
{
    void robotConnected();
    void robotDisconnected();
}
