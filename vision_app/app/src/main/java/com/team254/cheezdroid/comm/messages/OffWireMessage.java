package com.team254.cheezdroid.comm.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message Interpreter Class
 */
public class OffWireMessage extends VisionMessage
{
    private String mType;
    private String mMessage = "{}";
    private boolean mValid = false;

    /**
     * Creates a New OffWireMessage, Which Interprets A Received Message
     * Gives Type and Content Dynamically Based Off message Content
     * @param message - The Received Message
     */
    public OffWireMessage(String message)
    {
        try
        {
            JSONObject reader = new JSONObject(message);
            mType = reader.getString("type");
            mMessage = reader.getString("message");
            mValid = true;
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Did the JSON Reader Run to Completion
     * @return mValid - If the JSON Reader Ran to Completion
     */
    public boolean isValid()
    {
        return mValid;
    }

    @Override
    public String getType()
    {
        return mType == null ? "unknown" : mType;
    }

    @Override
    public String getMessage()
    {
        return mMessage;
    }
}
