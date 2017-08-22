package io.aeroscope.aeroscope;

import android.os.SystemClock;
import android.support.annotation.Nullable;

import io.aeroscope.oscilloscope.TimeBase;
import io.aeroscope.oscilloscope.Trigger;

import static io.aeroscope.aeroscope.AeroscopeConstants.*;

/**
 * Created on 2017-03-18.
 */

/*
* Ideally this should be the central repository for timebase-related stuff
* */

public class AsTimeBase implements TimeBase {
    
    // Members
    private float secondsPerDiv = DEFAULT_SECS_PER_DIV;
    private long triggerTime, resetTime;
    private boolean running = false;  // not fully implemented
    private int tBaseArrayIndex = DEFAULT_TIME_BASE_INDEX;  // array index for control byte, text description TODO: maintain elsewhere?
    
    AeroscopeDevice asDeviceRef;
    
    void setAsDeviceRef( AeroscopeDevice device ) {
        asDeviceRef = device;
    }
    
    
    // method to return the array index where the supplied time/div is found (-1 if not found)
    private int timeIndex( float value ) {  // returns the index where the value is found, else -1
        for( int tIndex = 0; tIndex < AVAILABLE_SECS_PER_DIV.length; tIndex++ ) {
            if( AVAILABLE_SECS_PER_DIV[tIndex] == value ) return tIndex;
        }
        return -1;
    }
    
    // method to set the hardware to the specified sample rate
    private void setHwSampleRateByIndex( int listIndex ) {  // listIndex = 0 for 500 ns/div, 1 for 1 us/div, etc.
        // Also must update the in-memory copy of the registers & propagate to screen display
        asDeviceRef.fpgaRegisterBlock[SAMPLER_CTRL] = SAMPLER_CTRL_BYTE[listIndex];
        asDeviceRef.copyFpgaRegisterBlockToAeroscope( );
    }
    
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS
    
    @Override
    public boolean setSecondsPerDiv( float secsPerDiv ) {  // returns true if legal and set OK
        int index = timeIndex( secsPerDiv );
        if( index >= 0 ) {  // found the requested value
            setHwSampleRateByIndex( index );
            secondsPerDiv = secsPerDiv;
            tBaseArrayIndex = index;
            asDeviceRef.rollMode = secondsPerDiv >= .5f;  // 500 ms/div or more --> Roll Mode
            return true;
        } else return false;  // requested value not found in available values
    }
    
    @Override
    public boolean setTimeBaseByIndex( int arrayIndex ) {  // select from a list of values; false if unimplemented or unavailable
        if( arrayIndex >= 0 && arrayIndex < AVAILABLE_SECS_PER_DIV.length ) {
            tBaseArrayIndex = arrayIndex;
            secondsPerDiv = AVAILABLE_SECS_PER_DIV[arrayIndex];
            setHwSampleRateByIndex( arrayIndex );
            asDeviceRef.rollMode = secondsPerDiv >= .5f;  // 500 ms/div or more --> Roll Mode
            return true;
        } else return false;
    }
    

    @Override
    public float getSecondsPerDiv() {
        return secondsPerDiv;
    }
    
    @Override
    public String getSecondsPerDivDescription() { return TIME_BASE_DESCRIPTION[tBaseArrayIndex]; }
    
    @Override
    public float getMinSecondsPerDiv() {
        return MIN_SECS_PER_DIV;
    }
    
    @Override
    public float getMaxSecondsPerDiv() {
        return MAX_SECS_PER_DIV;
    }
    
    @Override
    public boolean isSupportedSecondsPerDiv( float secsPerDiv ) {
        return timeIndex( secsPerDiv ) >= 0;
    }
    
    
    
    
    @Override
    public boolean isRunning() {  // return false if unimplemented(?)
        return running;
    }
    
    @Override
    public boolean setRunning( boolean state ) {
        running = state;
        return false;  // unimplemented(?)
    }
    
    @Override
    public boolean setTrigger( @Nullable Trigger trig ) {
        return false;  // unimplemented(?)
    }
    
    @Override  // TODO: what's the deal with implementation? boolean?
    public boolean onTriggered( long trigNs ) {  // called with timestamp to start the TimeBase
        triggerTime = trigNs;
        return false;
    }
    
    @Override
    public float secsSinceTriggered() {  // time since triggered TODO: maybe -1f if not supported?
        return ( SystemClock.elapsedRealtimeNanos() - triggerTime ) / 1e9f;
    }
    
    @Override
    public float secsSinceReset() {
        return ( SystemClock.elapsedRealtimeNanos() - resetTime ) / 1e9f;
    }
    
    @Override
    public boolean reset() {  // false if unimplemented
        resetTime = SystemClock.elapsedRealtimeNanos();
        return false;
    }
}
