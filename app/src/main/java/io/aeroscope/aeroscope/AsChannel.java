package io.aeroscope.aeroscope;

import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Arrays;

import io.aeroscope.oscilloscope.Channel;
import io.aeroscope.oscilloscope.LineType;
import io.aeroscope.oscilloscope.Screen;
import io.aeroscope.oscilloscope.TimeBase;
import io.aeroscope.oscilloscope.Trigger;

import static io.aeroscope.aeroscope.AeroscopeConstants.AVAILABLE_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_DC_COUPLED;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_OFFSET_VALUE;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VERT_SENS_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.FRONT_END_CTRL;
import static io.aeroscope.aeroscope.AeroscopeConstants.FRONT_END_CTRL_BYTE;
import static io.aeroscope.aeroscope.AeroscopeConstants.MAX_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.MIN_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.VERT_SENS_DESCRIPTION;

/**
 * Created on 2017-03-18.
 */

public class AsChannel implements Channel {
    
    static final String LOG_TAG = "AsChannel              ";  // 23 chars
    
    // Members (defaults etc. are in AeroscopeConstants)
    
    private float voltsPerDiv = DEFAULT_VOLTS_PER_DIV;      // initially 100 mV/div
    int vSensArrayIndex = DEFAULT_VERT_SENS_INDEX;          // array index for control byte, text description etc.; initially 0

    private boolean dcCoupled = DEFAULT_DC_COUPLED;         // initially true
    private boolean inverted = false;                       // safe to assume initially off (not supported)
    
    int currentCalValue;                                    // NEW updated when we change vertical scale (initially 0)
    int currentDacOffset;                                   // NEW UNCORRECTED Y axis offset TODO: do we need?
    
    int[] calibrationValue;                                 // as reported by Aeroscope Cal function; indexed by vSensArrayIndex
    int[] dacOffset;                                        // UNCORRECTED offset to set display range (32K midpoint) indexed by vSensArrayIndex
    
    TimeBase assignedTimeBase;
    Trigger assignedTrigger;
    Screen assignedScreen;
    LineType lineType;
    Color traceColor;
    
    AeroscopeDevice asDeviceRef;
    void setAsDeviceRef( AeroscopeDevice device ) { asDeviceRef = device; }
    
    
    // constructor
    // Note we rely on hardware initialization to set initial state; TODO: software?
    // problem is that talking to hardware requires a connection
    public AsChannel() {
        calibrationValue = new int[AVAILABLE_VOLTS_PER_DIV.length];  // size the array (initialized to 0's)
        Arrays.fill( calibrationValue, 0 );                          // calibration correction for each voltage range
        dacOffset = new int[AVAILABLE_VOLTS_PER_DIV.length];
        Arrays.fill( dacOffset, DEFAULT_OFFSET_VALUE );              // 0x8000 (a positive int)
    }
    
    
    
    // method to return the array index where the supplied vertical sensitivity is found
    private int vSensIndex ( float value ) {  // returns the index where the value is found, else -1
        for( int vIndex = 0; vIndex < AVAILABLE_VOLTS_PER_DIV.length; vIndex++ ) {
            if( AVAILABLE_VOLTS_PER_DIV[vIndex] == value ) return vIndex;  // TODO: careful about floating equality: tolerance?
        }
        return -1;  // requested value not found in available values
    }
    
    
    // upon entry, have already set new voltsPerDiv and vSensArrayIndex (= argument listIndex)
    // ultimately puts the corrected offset value in the DAC
    private void updateVerticalSensitivity( int listIndex ) {  // listIndex = 0 for 100 mV/div, 1 for 200 mV/div, etc.
        currentCalValue = calibrationValue[listIndex];   // new values
        currentDacOffset = dacOffset[listIndex];
        
        // NOTE when writing front-end control register, must preserve bit 7, the AC/DC selector
        // Also must update the in-memory copy of the registers and TODO: propagate to screen display(?)
        byte currentVertCtrl = asDeviceRef.fpgaRegisterBlock[FRONT_END_CTRL];  // register 0x03
        byte currentCoupling = (byte) (currentVertCtrl & 0x80);                // extract bit 7 (coupling)
        byte newVertCtrl = (byte) (FRONT_END_CTRL_BYTE[listIndex] | currentCoupling);
        asDeviceRef.fpgaRegisterBlock[FRONT_END_CTRL] = newVertCtrl;
        
        // have to update hardware trigger level to new Y range NEW: added to updateChart()
        
        //setOffsetDAC( currentDacOffset + currentCalValue );  // IS THIS, FINALLY, THE BUG?????
        // Note this copies the entire register block to the Aeroscope
        setOffsetDAC( currentDacOffset );    // THIS WORKS: actually it wasn't a bug, it was an erroneous spec. The scope takes care of adding calibration correction!
        
        Log.d( LOG_TAG, "Calibration: updateVerticalSensitivity(index) called setOffsetDAC(currentDacOffset): " + currentDacOffset );  //  FIXED
    }
    
    private void updateCoupling( boolean dcEnabled ) {  // called by the Interface routine setDcCoupling()
        // NOTE must preserve bits 0-6 of the register and update in-memory copy
        byte bit7 = dcEnabled? (byte) 0x80 : (byte) 0x00;
        byte currentVertCtrl = (byte) (asDeviceRef.fpgaRegisterBlock[FRONT_END_CTRL] & 0x7F);
        byte newVertCtrl = (byte) (currentVertCtrl | bit7);
        asDeviceRef.fpgaRegisterBlock[FRONT_END_CTRL] = newVertCtrl;
        asDeviceRef.copyFpgaRegisterBlockToAeroscope();
    }
    
    // method just stores the uncorrected offset value in the dacOffset array at index position
    // called ultimately by updateChart()
    void updateOffsetValue( int offset, int index ) {  // when, e.g., Y axis midpoint is changed, store the (uncorrected) DAC offset value
        dacOffset[index] = offset;
    }
    
    // method to set a RAW final value into the offset calibration DAC  NOT USED
    void setOffsetDAC( int correctedOffset, int vertIndex ) {  // To center the axis, set it to 32768 (+ any calibration value)
        vSensArrayIndex = vertIndex;
        currentCalValue = calibrationValue[vertIndex];  // TODO: is any of this necessary?
        currentDacOffset = dacOffset[vertIndex];
        setOffsetDAC( correctedOffset );  // TODO: handle returned value?
    }
    
    // method that just constrains actual HW DAC value to 16 unsigned bits and writes it to RAM & HW
    int setOffsetDAC( int correctedOffset ) {  // give it the actual final DAC value; returns the actual, possibly clipped value put in the DAC
        int newDacValue = Math.min( correctedOffset, 0xFFFF );  // clip it to avoid 16-bit overflow
        asDeviceRef.fpgaRegisterBlock[DAC_CTRL_LO] = (byte) (newDacValue & 0xff);
        asDeviceRef.fpgaRegisterBlock[DAC_CTRL_HI] = (byte) ((newDacValue >>> 8) & 0xff);
        asDeviceRef.copyFpgaRegisterBlockToAeroscope();  // including the front end control byte from updateVerticalSensitivity()
        Log.d( LOG_TAG, "setOffsetDAC( offset ) called with Corrected Offset: " + correctedOffset + "; used Calibration value: " + currentCalValue );
        return newDacValue;
    }
    
    // method to retrieve the current CORRECTED DAC value from the hardware
    int getOffsetDAC( ) {  // note it's a 16-bit unsigned value
        return (asDeviceRef.fpgaRegisterBlock[DAC_CTRL_LO] & 0xff) + 256 * (asDeviceRef.fpgaRegisterBlock[DAC_CTRL_HI] & 0xff);
    }
    
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS
    
    @Override
    public boolean setVoltsPerDiv( float vPerDiv ) {  // returns true if legal and set OK
        int index = vSensIndex( vPerDiv );
        if( index >= 0 ) {
            voltsPerDiv = vPerDiv;
            vSensArrayIndex = index;
            updateVerticalSensitivity( index );
            return true;
        } else return false;
    }
    
    @Override  // use an index into an array of available values
    public boolean setVertSensByIndex( int arrayIndex ) { // false if unavailable or unimplemented
        if( arrayIndex >= 0 && arrayIndex < AVAILABLE_VOLTS_PER_DIV.length ) {  // legal range test
            voltsPerDiv = AVAILABLE_VOLTS_PER_DIV[arrayIndex];
            vSensArrayIndex = arrayIndex;
            updateVerticalSensitivity( arrayIndex );  // update control register values & send to hardware
            // Hmm: do we need to adjust raw trigger level here?
            return true;
        } else return false;
    }
    
    
    @Override
    public float getVoltsPerDiv() {
        return voltsPerDiv;
    }
    
    @Override
    public String getVoltsPerDivDescription() { return VERT_SENS_DESCRIPTION[vSensArrayIndex]; }
    
    @Override
    public float getMinVoltsPerDiv() {
        return MIN_VOLTS_PER_DIV;
    }
    
    @Override
    public float getMaxVoltsPerDiv() {
        return MAX_VOLTS_PER_DIV;
    }
    
    @Override
    public boolean isSupportedVoltsPerDiv( float vPerDiv ) {
        return vSensIndex( vPerDiv ) >= 0;
    }
    
    
    @Override
    public boolean setDcCoupling( boolean dcEnable ) {  // returns true if success (false if, say, unimplemented)
        updateCoupling( dcEnable );
        dcCoupled = dcEnable;
        return true;
    }
    
    @Override
    public boolean getDcCoupling() {
        return dcCoupled;
    }
    
    @Override
    public boolean invertInput( boolean invertState ) {  // false if unimplemented (like Aeroscope)
        //inverted = invertState;
        return false;
    }
    
    @Override
    public boolean isInverted() {
        return inverted;
    }
    
    @Override
    public boolean sumToChannel( @Nullable Channel chan ) {
        return false;  // unsupported
    }
    
    @Override
    public boolean assignTimeBase( TimeBase theTimeBase ) {
        return false;  // TODO: revisit
    }
    @Override
    public TimeBase getAssignedTimeBase( ) { return assignedTimeBase; }
    
    @Override
    public boolean assignTrigger( Trigger theTrigger ) {
        return false;  // TODO: revisit
    }
    @Override
    public Trigger getAssignedTrigger( ) { return assignedTrigger; }
    
    @Override
    public boolean assignScreen( Screen theScreen ) {
        return false;  // TODO: revisit
    }
    @Override
    public Screen getAssignedScreen( ) { return assignedScreen; }
    
    
    @Override  // TODO: is this actually even a Channel function?
    public boolean setDivsFromTop( float divsFromTop ) {
        return false;  // TODO: revisit?
    }
    
    @Override
    public boolean setTraceType( LineType type ) {
        return false;  // unsupported
    }
    @Override
    public LineType getLineType( ) { return lineType; }
    
    @Override
    public boolean setTraceColor( Color traceColor ) {
        return false;  // unsupported
    }
    @Override
    public Color getTraceColor( ) { return traceColor; }
    
}
