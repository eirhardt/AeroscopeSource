package io.aeroscope.oscilloscope;

import android.graphics.Color;
import android.support.annotation.Nullable;

/**
 * Created on 2017-01-01.
 *
 */

public interface Channel {
    
    boolean setVoltsPerDiv( float vPerDiv );  // changed from void; true if success
    boolean setVertSensByIndex( int arrayIndex );  // false if unavailable or unimplemented
    float getVoltsPerDiv();
    boolean isSupportedVoltsPerDiv( float vPerDiv );
    String getVoltsPerDivDescription();
    
    float getMinVoltsPerDiv(); // the implementing class determines these 2 constants
    float getMaxVoltsPerDiv();
    
    boolean setDcCoupling( boolean enabled );  // changed from void; true if success
    boolean getDcCoupling( );
    
    boolean invertInput( boolean invertState ); // true to change sign of input (returns false if unimplemented)
    boolean isInverted( );
    
    boolean sumToChannel( @Nullable Channel chan ); // (can add inverted to get difference) null to disable; false if unsupported
    
    boolean assignTimeBase( TimeBase theTimeBase );  // false if unsupported
    TimeBase getAssignedTimeBase( );
    
    boolean assignTrigger( Trigger theTrigger );  // false if unsupported
    Trigger getAssignedTrigger( );
    
    boolean assignScreen( Screen theScreen );  // false if unsupported
    Screen getAssignedScreen( );
    
    boolean setTraceType( LineType type );  // false if unsupported
    LineType getLineType( );
    
    boolean setTraceColor( Color traceColor );  // false if unsupported
    Color getTraceColor( );
    
    
    // TODO: is this actually even a Channel function?
    boolean setDivsFromTop( float divsFromTop );  // where this channel's Y=0 line appears on screen (?)  // false if unsupported
    
}
