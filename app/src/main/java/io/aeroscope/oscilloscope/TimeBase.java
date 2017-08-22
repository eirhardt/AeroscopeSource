package io.aeroscope.oscilloscope;


import android.support.annotation.Nullable;

/**
 * Created on 2017-01-01.
 */

public interface TimeBase {
    
    float getSecondsPerDiv();
    boolean setSecondsPerDiv( float secsPerDiv ); // returns false (& doesn't change value) if outside the implementation's range
    boolean setTimeBaseByIndex( int arrayIndex ); // select from a list of values; false if unimplemented or unavailable
    String getSecondsPerDivDescription();
    
    float getMinSecondsPerDiv(); // the implementing class determines these 2 constants
    float getMaxSecondsPerDiv();
    boolean isSupportedSecondsPerDiv( float secsPerDiv );
    
    boolean isRunning(); // true if triggered and not done yet; false when done or reset
    boolean setRunning( boolean state ); // allows setting the state of Running; returns false if unimplemented
    
    
    
    boolean setTrigger( @Nullable Trigger trig ); // installs trig as the Trigger (null == none) TODO: DO WE NEED? False if unavailable(?)
    boolean onTriggered( long trigNs ); // receives the nanosecond timestamp when trigger occurred; false if unimplemented
    
    float secsSinceTriggered(); // current value in seconds along X axis: 0 before trigger, # X divs * secsPerDiv at end before reset
    float secsSinceReset();     //
    
    boolean reset(); // cancels anything in progress and gets ready for the next trigger; false if unimplemented(?)
    
}

