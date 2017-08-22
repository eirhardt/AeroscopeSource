package io.aeroscope.aeroscope;

import android.util.Log;

import java.util.EnumSet;
import java.util.Vector;

import io.aeroscope.oscilloscope.TimeBase;
import io.aeroscope.oscilloscope.Trigger;

import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_RAW_TRIGGER_LEVEL;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_RAW_TRIGGER_LOC;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_SCALED_TRIGGER_LEVEL;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_TRIGGER_MODES;
import static io.aeroscope.aeroscope.AeroscopeConstants.SUPPORTED_TRIGGER_MODES;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_CTRL;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_PT;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_LO;

/**
 * Created on 2017-03-19
 *
 * Note that for initial development, we should leave the Trigger X Location at its default, middle of sample buffer
 */

class AsTrigger implements Trigger {

    static final String LOG_TAG = "AsTrigger              "; // 23 chars

    AeroscopeDevice asDeviceRef;

    EnumSet<Mode> enabledModes = EnumSet.copyOf( DEFAULT_TRIGGER_MODES );
    // note set of supported modes is in AeroscopeConstants

    // Note that in SCALED time units, the Trigger Location is always 0.0
    float level = DEFAULT_SCALED_TRIGGER_LEVEL;  // scaled trigger level (in "raw" units, it's 128, in default scale, sl. > 0f)
    int rawLevel = DEFAULT_RAW_TRIGGER_LEVEL;    // = 128
    int rawLocation = DEFAULT_RAW_TRIGGER_LOC;   // = 0x800 (AKA Trigger X Location)
    
    void setAsDeviceRef( AeroscopeDevice device ) { asDeviceRef = device; }  // called by AeroscopeDevice constructor
    
    // Method to set Trigger Location address in scope memory (defaults to center: 0x800)  TODO: range check: should be within Write Sample Depth (return false if not)
    boolean setRawTriggerLocation( int addressInBuffer ) {
        rawLocation = addressInBuffer;
        asDeviceRef.fpgaRegisterBlock[TRIGGER_XPOS_LO] = (byte) ( addressInBuffer & 0xFF );
        asDeviceRef.fpgaRegisterBlock[TRIGGER_XPOS_HI] = (byte) ( (addressInBuffer >>> 8) & 0x0F );
        asDeviceRef.copyFpgaRegisterBlockToAeroscope();
        return true;
    }
    int getRawTriggerLocation( ) { return rawLocation; }


    // Method to set "raw" (0-255) trigger level  TODO: need?
    void setRawTriggerLevel( byte rawLevel ) {
        // asDeviceRef.sendChangedState( ( byte ) TRIGGER_PT, rawLevel );  TODO: use a different call (this was only use of deleted sendChangedState())
    }

    @Override  // now returns false if the requested level was out of hardware range
               // and constrains raw trigger level to 0..255 (with corresponding scaled value)
    public boolean setLevel( float scaledTriggerLevel ) {  // scaled units, of course. false if off-scale
        boolean outOfRange = false;
        level = scaledTriggerLevel;

        int rawTrig = Math.round( DataOps.rawVolts( scaledTriggerLevel,
                asDeviceRef.asScreen.scaledYmin, asDeviceRef.asScreen.scaledYmax ) );  // no longer constrained to 0..255

        if( rawTrig < 0 ) {
            outOfRange = true;
            rawTrig = 0;
            level = asDeviceRef.asScreen.scaledYmin;
        } else if( rawTrig > 255 ) {
            outOfRange = true;
            rawTrig = 255;   // TODO: parameterize?
            level = asDeviceRef.asScreen.scaledYmax * (255f/256f);  // TODO: parameterize?
        }

        asDeviceRef.fpgaRegisterBlock[TRIGGER_PT] = (byte) rawTrig;        // set the hardware trigger level
        asDeviceRef.copyFpgaRegisterBlockToAeroscope();
        Log.d( LOG_TAG, "setLevel() new scaled level: " + level + "; new raw level: " + rawTrig );
        return outOfRange;
    }

    @Override
    public float getLevel() { return level; }  // scaled; initialized to DEFAULT_SCALED_TRIGGER_LEVEL

    @Override
    public boolean enableMode( Mode mode ) {  // enables a trigger mode (true if success or already enabled, false if unimplemented)
        if( SUPPORTED_TRIGGER_MODES.contains( mode ) ) {
            enabledModes.add( mode );
            switch( mode ) {
                case AUTO:          // set bit 0
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] | 0x01);
                    break;
                case RISING:        // set bit 1
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] | 0x02);
                    break;
                case FALLING:       // set bit 2
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] | 0x04);
                    break;
                case NOISE_REDUCED: // set bit 4 -- UPDATE: it's now bit 5
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] | 0x20);
                    break;
                default:
                    return false;   // supplied mode argument is not recognized
            }
            asDeviceRef.copyFpgaRegisterBlockToAeroscope();
            return true;
        } else return false;        // the specified mode is not supported
    }

    @Override
    public boolean disableMode( Mode mode ) { // disables a trigger mode (true if success, false if unimplemented)
        if( SUPPORTED_TRIGGER_MODES.contains( mode ) ) {
            enabledModes.remove( mode );
            switch( mode ) {
                case AUTO:          // clear bit 0
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] & 0xFE);
                    break;
                case RISING:        // clear bit 1
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] & 0xFD);
                    break;
                case FALLING:       // clear bit 2
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] & 0xFB);
                    break;
                case NOISE_REDUCED: // clear bit 4 -- UPDATE: it's now bit 5
                    asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] = (byte) (asDeviceRef.fpgaRegisterBlock[TRIGGER_CTRL] & 0xDF);
                    break;
                default:
                    return false;   // supplied mode argument is not recognized
            }
            asDeviceRef.copyFpgaRegisterBlockToAeroscope();
            return true;
        } else return false;        // the specified mode is not supported
    }

    @Override
    public boolean enableModeSet( EnumSet<Mode> modeSet ) { // first verifies all modes are supported, then enables them
        for( Mode mode : modeSet ) {
            if( !SUPPORTED_TRIGGER_MODES.contains( mode ) ) return false;  // found an unsupported mode
        }
        for( Mode mode : modeSet ) enableMode( mode );
        return true;
    }

    @Override
    public boolean disableModeSet( EnumSet<Mode> modeSet ) {  // unlike enableModeSet(), don't check for unsupported modes; they're "disabled"
        for( Mode mode : modeSet ) disableMode( mode );
        return true;
    }

    @Override
    public boolean enableJustModeSet( EnumSet<Mode> modeSet ) {  // enables just the supported modes in the supplied set; disables others
        for( Mode mode : modeSet ) { // first verifies all modes are supported, then enables them
            if( !SUPPORTED_TRIGGER_MODES.contains( mode ) ) return false;  // found an unsupported mode
        }
        for( Mode mode : SUPPORTED_TRIGGER_MODES ) {
            if( modeSet.contains( mode ) ) {
                enableMode( mode );
            } else {
                disableMode( mode );
            }
        }
        return true;
    }

    @Override
    public boolean isEnabled( Mode mode ) { // tells if the mode is enabled
        return enabledModes.contains( mode );
    }

    @Override
    public boolean isSupported( Mode mode ) { // tells if the mode is supported
        return SUPPORTED_TRIGGER_MODES.contains( mode );
    }

    @Override
    public EnumSet<Mode> getEnabledModes() { // returns the set of enabled modes
        return enabledModes;
    }

    @Override
    public EnumSet<Mode> getSupportedModes() { return SUPPORTED_TRIGGER_MODES; }

    @Override
    public boolean setCascadePos( CascadePos position ) { // false if unimplemented
        return false;
    }

    @Override
    public CascadePos getCascadePos() {  // null if unsupported
        return null;
    }

    @Override
    public boolean setCascadeSource( TimeBase timeBase ) {  // false if unimplemented
        return false;
    }

    @Override
    public TimeBase getCascadeSource() {  // unimplemented
        return null;
    }

    @Override
    public boolean setCascadeDelaySecs( float delaySecs ) {  // false if unimplemented
        return false;
    }

    @Override
    public float getCascadeDelaySecs() {
        return 0f;
    }



    @Override
    public boolean connectTimeBase( TimeBase timeBase ) {  // false if unimplemented
        return false;
    }

    @Override
    public boolean disconnectTimeBase( TimeBase timeBase ) {
        return false;
    }

    @Override
    public Vector<TimeBase> getConnectedTimeBases() {  // null if unimplemented
        return null;
    }

    @Override
    public boolean sendTriggerSignal( long timeStampNs ) {
        return false;
    }
}
