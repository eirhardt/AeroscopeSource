package io.aeroscope.oscilloscope;

import java.util.Vector;


/**
 * Policies
 *  simulated hardware features are referred to as on a physical oscilloscope:
 *  Channels, Screens, etc. are numbered (externally) starting with 1
 *
 *  Concept: Oscilloscope object is a frame that keeps track of what exists and what is
 *  connected to what. A Trigger, for example, doesn't have to know what TimeBase it's connected to:
 *  the Oscilloscope manages that. Is this right?
 */

public abstract class Oscilloscope {
    
    Vector<Channel> channel; // use Vectors for thread safety(?)
    Vector<Screen> screen;
    Vector<Trigger> trigger;
    Vector<TimeBase> timebase;
    
    
    public Oscilloscope( int numChannels, int numTimeBases, int numTriggers, int numScreens ) {
        channel = new Vector<>( numChannels );
        timebase = new Vector<>( numTimeBases );
        trigger = new Vector<>( numTriggers );
        screen = new Vector<>( numScreens );
    
    }
    
    
    // Note: numbered objects (channels, etc.) are numbered externally starting at 1, internally at 0
    
    public void installChannel( int number, Channel chan ) { // First channel is "Channel 1"
        channel.set( number-1, chan ); // different channels could have different subtypes of Channel
    }
    
    public void installTimeBase( int number, TimeBase tBase ) { // First TimeBase is "TimeBase 1"
        timebase.set( number-1, tBase ); // different channels could have different subtypes of TimeBase
    }
    
    public void installTrigger( int number, Trigger trig ) { // First trigger is "Trigger 1"
        trigger.set( number-1, trig ); // different channels could have different subtypes of Trigger
    }
    
    public void installScreen( int number, Screen scrn ) { // First screen is "Screen 1"
        screen.set( number-1, scrn ); // different screens could have different subtypes of Screen
    }
    
    
    
    public void connectChanToTrig( Channel chan, Trigger trig ) {
        
    }
    
    public void connectTrigToTimeB( Trigger trig, TimeBase tBase ) {
        
    }
    
    public void connectTimeBToScrn( TimeBase tBase, Screen scrn ) {
        
    }
    
    public void connectChanToScrn( Channel chan, Screen scrn ) {
        
    }
    
}
