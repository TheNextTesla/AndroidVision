package com.team254.cheezdroid.comm;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that Represents the Updates for the Vision Targets
 */
public class VisionUpdate
{
    protected List<CameraTargetInfo> m_targets;
    protected long m_captured = 0;

    /**
     * Constructs and Instantiates the ArrayList With Default Size 3
     * @param capturedAtTimestamp - The Vision Updates Found at a Specific Moment / Frame
     */
    public VisionUpdate(long capturedAtTimestamp)
    {
        m_captured = capturedAtTimestamp;
        m_targets = new ArrayList<>(3);
    }

    /**
     * Adds Another Homogeneous Vision Vector (Target Info) to the List
     * @param t - New CameraTargetInfo
     */
    public void addCameraTargetInfo(CameraTargetInfo t)
    {
        m_targets.add(t);
    }

    /**
     * Converts the Internal List of Target Information into JSON
     * @param timestamp - Timestamp for Capture Time Latency
     * @return The JSON Object converted into a String
     */
    public String getSendableJsonString(long timestamp)
    {
        long captured_ago = (timestamp - m_captured) / 1000000L;  // nanos to millis
        JSONObject j = new JSONObject();
        try
        {
            j.put("capturedAgoMs", captured_ago);
            JSONArray arr = new JSONArray();
            for (CameraTargetInfo t : m_targets)
            {
                if (t != null)
                {
                    arr.put(t.toJson());
                }
            }
            j.put("targets", arr);
        }
        catch (JSONException e)
        {
            Log.e("VisionUpdate", "Could not encode JSON");
        }

        return j.toString();
    }
}
