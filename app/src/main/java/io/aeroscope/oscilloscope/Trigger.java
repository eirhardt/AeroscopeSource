package io.aeroscope.oscilloscope;

import java.util.EnumSet;
import java.util.Vector;

/**
 * Created on 2017-01-01.
 *
 * Idea: monitors trigger inputs and at the appropriate time calls onTriggered() of any TimeBases it's connected to
 *
 * Note: long SystemClock.elapsedRealtimeNanos() returns elapsed ns since system boot, guaranteed monotonic, includes sleep
 * It and long SystemClock.elapsedRealtime() (ms) are recommended for general purpose interval timing
 */

public interface Trigger {
    
    
    static enum Mode { AUTO, MANUAL, RISING, FALLING, FREE_RUN, EXTERNAL, NOISE_REDUCED, CASCADED }
    static enum CascadePos { START, END }             // this trigger cascades from start or end of another TimeBase(?)
    
    boolean setLevel( float triggerLevel );           // false if unavailable (would be weird)
    float getLevel();                                 // scaled units, of course

    boolean enableMode( Mode mode );                  // enables a trigger mode (true if success, false if unimplemented)
    boolean disableMode( Mode mode );                 // disables a trigger mode (true if success, false if wasn't enabled, or unimplemented)
    boolean enableModeSet( EnumSet<Mode> modeSet );   // enables the set of modes, first verifying they're all supported (false if not)
    boolean disableModeSet( EnumSet<Mode> modeSet );  // disables the set of modes, NOT verifying they're all supported
    boolean enableJustModeSet( EnumSet<Mode> modeSet );  // enables just the modes in the supplied set, disabling other supported modes
    boolean isEnabled( Mode mode );                   // tells if the mode is enabled
    boolean isSupported( Mode mode );                 // tells if the mode is supported
    EnumSet<Mode> getEnabledModes();                  // returns the set of enabled modes
    EnumSet<Mode> getSupportedModes();                // returns the set of supported modes
    
    boolean setCascadePos( CascadePos position );     // false if unimplemented
    CascadePos getCascadePos();                       // null if unimplemented
    boolean setCascadeSource( TimeBase timeBase );    // false if unimplemented
    TimeBase getCascadeSource();                      // null if unimplemented
    boolean setCascadeDelaySecs( float delaySecs );   // false if unimplemented
    float getCascadeDelaySecs();                      // 0f if unimplemented
    
    boolean connectTimeBase( TimeBase timeBase );     // false if unimplemented
    boolean disconnectTimeBase( TimeBase timeBase );  // false if unimplemented
    Vector<TimeBase> getConnectedTimeBases();         // null if unimplemented
    boolean sendTriggerSignal( long timeStampNs );    // nanosecond timestamp for trigger instant
    
}
