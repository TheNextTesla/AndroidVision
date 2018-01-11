package com.androidFRC.androidVision;

/**
 * The Interface (Which Is Connected to A 'Broadcast Receiver')
 * Informs Implementing Classes of When These Actions Happen
 */
public interface RobotEventListener
{
    void shotTaken();
    void wantsVisionMode();
    void wantsIntakeMode();
}
