package com.androidFRC.androidVision.comm.messages;

/**
 * Empty Message Periodically Sent So Both Sides Know the State of the Connection
 */
public class HeartbeatMessage extends VisionMessage
{
    private static HeartbeatMessage sInst = null;

    /**
     * Singleton Instance Method
     * Message Type is "heartbeat" and the Content is and Empty JSON String
     * @return Instance of HeartbeatMessage
     */
    public static HeartbeatMessage getInstance()
    {
        if (sInst == null)
        {
            sInst = new HeartbeatMessage();
        }
        return sInst;
    }

    @Override
    public String getType()
    {
        return "heartbeat";
    }

    @Override
    public String getMessage()
    {
        return "{}";
    }
}
