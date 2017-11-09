package com.team254.cheezdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Pair;

/**
 * Manages Shared Preferences, Used for Maintaining Changes for the HSV Ranges for OpenCV
 */
public class Preferences
{

    /**
     * Context Used in Conjunction with the Shared Preferences
     * SharedPreferences, the Android Class That Allows for Managing Persistent Application Settings
     */
    private Context m_context;
    private SharedPreferences m_prefs;

    /**
     * The Different Vision Processor Color Range Values
     * Variables Controlled by this Preferences Class
     */
    private Pair<Integer, Integer> m_h_ranges;
    private Pair<Integer, Integer> m_s_ranges;
    private Pair<Integer, Integer> m_v_ranges;

    /**
     * Constructor for Preferences - Instantiates Preferences
     * @param context - Android Context
     */
    public Preferences(Context context)
    {
        m_context = context;
        m_prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Method to Set Preferences Values Give a "Key" and a New Value
     * @param key - The String that Tells Android's Preferences What Preference to Change
     * @param value - The New Integer Value of the Preference
     */
    private void setInt(String key, int value)
    {
        SharedPreferences.Editor editor = m_prefs.edit();
        editor.putInt(key, value);
        //TODO: Double Check .apply vs (editor.commit())
        editor.apply();
    }

    /**
     * Method to Get Preferences Values Give a "Key" and a Value to Fall Back On (If Key Cannot be Found)
     * @param key - The String that Tells Android's Preferences What Preference to Look At
     * @param defaultValue - The Default Return Value of the Integer
     */
    private int getInt(String key, int defaultValue)
    {
        return m_prefs.getInt(key, defaultValue);
    }

    /**
     * Sets the 'H' Range Integers of Shared Preference
     * Determines (Along with the other HSV Methods) The Range of Colors OpenCV Should Use
     * @param min - Minimum 'H' Value
     * @param max - Maximum 'H' Value
     */
    public void setThresholdHRange(int min, int max)
    {
        setInt(m_context.getString(R.string.threshold_h_min_key), min);
        setInt(m_context.getString(R.string.threshold_h_max_key), max);
        m_h_ranges = new Pair<>(min, max);
    }

    /**
     * Sets the 'S' Range Integers of Shared Preference
     * Determines (Along with the other HSV Methods) The Range of Colors OpenCV Should Use
     * @param min - Minimum 'S' Value
     * @param max - Maximum 'S' Value
     */
    public void setThresholdSRange(int min, int max)
    {
        setInt(m_context.getString(R.string.threshold_s_min_key), min);
        setInt(m_context.getString(R.string.threshold_s_max_key), max);
        m_s_ranges = new Pair<>(min, max);
    }

    /**
     * Sets the 'V' Range Integers of Shared Preference
     * Determines (Along with the other HSV Methods) The Range of Colors OpenCV Should Use
     * @param min - Minimum 'V' Value
     * @param max - Maximum 'V' Value
     */
    public void setThresholdVRange(int min, int max)
    {
        setInt(m_context.getString(R.string.threshold_v_min_key), min);
        setInt(m_context.getString(R.string.threshold_v_max_key), max);
        m_v_ranges = new Pair<>(min, max);
    }

    /**
     * Gets the 'H' Range Integers of Shared Preference
     * @return The 'Pair' of Integers that Represent the Min and Max Values of This Range
     */
    public Pair<Integer, Integer> getThresholdHRange()
    {
        if (m_h_ranges == null)
        {
            Resources res = m_context.getResources();
            m_h_ranges = new Pair<>(getInt(m_context.getString(R.string.threshold_h_min_key), res.getInteger(R.integer.default_h_min)), getInt(m_context.getString(R.string.threshold_h_max_key), res.getInteger(R.integer.default_h_max)));
        }
        return m_h_ranges;
    }

    /**
     * Gets the 'S' Range Integers of Shared Preference
     * @return The 'Pair' of Integers that Represent the Min and Max Values of This Range
     */
    public Pair<Integer, Integer> getThresholdSRange()
    {
        if (m_s_ranges == null)
        {
            Resources res = m_context.getResources();
            m_s_ranges = new Pair<>(getInt(m_context.getString(R.string.threshold_s_min_key), res.getInteger(R.integer.default_s_min)), getInt(m_context.getString(R.string.threshold_s_max_key), res.getInteger(R.integer.default_s_max)));
        }
        return m_s_ranges;
    }

    /**
     * Gets the 'V' Range Integers of Shared Preference
     * @return The 'Pair' of Integers that Represent the Min and Max Values of This Range
     */
    public Pair<Integer, Integer> getThresholdVRange()
    {
        if (m_v_ranges == null)
        {
            Resources res = m_context.getResources();
            m_v_ranges = new Pair<>(getInt(m_context.getString(R.string.threshold_v_min_key), res.getInteger(R.integer.default_v_min)), getInt(m_context.getString(R.string.threshold_v_max_key), res.getInteger(R.integer.default_v_max)));
        }
        return m_v_ranges;
    }

    /**
     * Returns All the Preferences to Their Default Values, as Set in XML
     */
    public void restoreDefaults()
    {
        Resources res = m_context.getResources();
        setThresholdHRange(res.getInteger(R.integer.default_h_min), res.getInteger(R.integer.default_h_max));
        m_h_ranges = null;
        setThresholdSRange(res.getInteger(R.integer.default_s_min), res.getInteger(R.integer.default_s_max));
        m_s_ranges = null;
        setThresholdVRange(res.getInteger(R.integer.default_v_min), res.getInteger(R.integer.default_v_max));
        m_v_ranges = null;
    }
}
