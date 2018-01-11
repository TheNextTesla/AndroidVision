package com.androidFRC.androidVision.comm.messages;

import com.androidFRC.androidVision.comm.VisionUpdate;

/**
 * Message Packaging for Passing Along Target Information
 */
public class TargetUpdateMessage extends VisionMessage
{
    private VisionUpdate mUpdate;
    private long mTimestamp;

    /**
     * Creates the Target Update Message, Which Is Used to Pass Along a VisionUpdate
     * @param update - Actual VisionUpdate (Payload)
     * @param timestamp - Reference Timestamp (Useful for Latency Calculations)
     */
    public TargetUpdateMessage(VisionUpdate update, long timestamp)
    {
        mUpdate = update;
        mTimestamp = timestamp;
    }

    @Override
    public String getType()
    {
        return "targets";
    }

    @Override
    public String getMessage()
    {
        return mUpdate.getSendableJsonString(mTimestamp);
    }
}
