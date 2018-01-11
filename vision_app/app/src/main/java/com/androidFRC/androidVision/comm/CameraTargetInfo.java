package com.androidFRC.androidVision.comm;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class That Represents 2D Image Positions (In a homogeneous vector form)
 */
public class CameraTargetInfo
{
    protected double m_y;
    protected double m_z;

    /**
     * Class Coordinate Frame:
     * +x is out the camera's optical axis
     * +y is to the left of the image
     * +z is to the top of the image
     * We assume the x component of all targets is +1.0 (since this is homogeneous)
     * @param y - 'Y' of the Vector
     * @param z - 'Z' of the Vector
     */
    public CameraTargetInfo(double y, double z)
    {
        m_y = y;
        m_z = z;
    }

    /**
     * Reformats Double To Minimize Loss
     * @param value - Double to Format
     * @return Adjusted Double
     */
    private double doubleize(double value)
    {
        double leftover = value % 1;
        if (leftover < 1e-7)
        {
            value += 1e-7;
        }
        return value;
    }

    public double getY()
    {
        return m_y;
    }

    public double getZ()
    {
        return m_z;
    }

    /**
     * Creates JSON Object of the Camera Target Information Vector
     * @return The JSON Object of the Camera Target Information Vector
     */
    public JSONObject toJson()
    {
        JSONObject j = new JSONObject();
        try
        {
            j.put("y", doubleize(getY()));
            j.put("z", doubleize(getZ()));
        }
        catch (JSONException e)
        {
            Log.e("CameraTargetInfo", "Could not encode Json");
        }
        return j;
    }
}