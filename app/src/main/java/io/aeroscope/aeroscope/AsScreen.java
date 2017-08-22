package io.aeroscope.aeroscope;

import io.aeroscope.oscilloscope.Screen;

import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_SECS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.RAW_X_STEPS;
import static io.aeroscope.aeroscope.AeroscopeConstants.RAW_Y_STEPS;
import static io.aeroscope.aeroscope.AeroscopeConstants.X_DIVISIONS;
import static io.aeroscope.aeroscope.AeroscopeConstants.Y_DIVISIONS;

/**
 * Created on 2017-03-22.
 */

public class AsScreen implements Screen {
    
    int rawXSteps = RAW_X_STEPS;   // 512
    int rawYSteps = RAW_Y_STEPS;   // 256
    
    int xDivisions = X_DIVISIONS;  //  10
    int yDivisions = Y_DIVISIONS;  //  8
    
    float scaledXmin = -(X_DIVISIONS/2f) * DEFAULT_SECS_PER_DIV;   // set by setScreenTimeBounds( tMin, tMax )
    float scaledXmax =  (X_DIVISIONS/2f) * DEFAULT_SECS_PER_DIV;   // set by setScreenTimeBounds( tMin, tMax )
    float scaledYmin = -(Y_DIVISIONS/2f) * DEFAULT_VOLTS_PER_DIV;  // set by setScreenVoltageBounds( vMin, vMax )
    float scaledYmax =  (Y_DIVISIONS/2f) * DEFAULT_VOLTS_PER_DIV;  // set by setScreenVoltageBounds( vMin, vMax )
    
    AeroscopeDevice asDeviceRef;  // parent entity
    
    
    
    void setAsDeviceRef( AeroscopeDevice device ) {  // called by parent to set reference
        asDeviceRef = device;
    }
    
    
    
    @Override
    public boolean setRawXYSteps( int xSteps, int ySteps ) {  // default is OK for now
        rawXSteps = xSteps;
        rawYSteps = ySteps;
        return true;  // TODO: maybe return false if unsupported
    }
    
    @Override
    public boolean setRawXSteps( int xSteps ) {               // default is OK for now
        rawXSteps = xSteps;
        return true;  // TODO: maybe return false if unsupported
    }
    
    @Override
    public boolean setRawYSteps( int ySteps ) {               // default is OK for now
        rawYSteps = ySteps;
        return true;  // TODO: maybe return false if unsupported
    }
    
    public int getRawXSteps() {
        return rawXSteps;
    }           // not part of Interface
    
    public int getRawYSteps() {
        return rawYSteps;
    }           // not part of Interface
    
    @Override
    public boolean setScaledXmin( float xMin ) {
        scaledXmin = xMin;
        return true;
    }
    
    @Override
    public boolean setScaledXmax( float xMax ) {
        scaledXmax = xMax;
        return true;
    }
    
    @Override
    public boolean setScaledYmin( float yMin ) {
        scaledYmin = yMin;
        return true;
    }
    
    @Override
    public boolean setScaledYmax( float yMax ) {
        scaledYmax = yMax;
        return true;
    }
}
