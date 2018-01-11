package com.androidFRC.androidVision.comm.messages;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstract Class Framework For the Vision Messages
 */
public abstract class VisionMessage
{
    public abstract String getType();

    public abstract String getMessage();

    /**
     * Procedure for Putting the Message into String JSON
     * @return A Compatible JSON String (Hopefully)
     */
    public String toJson()
    {
        JSONObject j = new JSONObject();
        try
        {
            j.put("type", getType());
            j.put("message", getMessage());
        }
        catch (JSONException e)
        {
            Log.e("VisionMessage", "Could not encode JSON");
        }
        return j.toString();
    }
}
