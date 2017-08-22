package io.aeroscope.oscilloscope;

/**
 * Created on 2017-01-01.
 */

/*
* Parameters of interest for a screen:
*     --raw X resolution in # of steps (512)
*     --raw Y resolution in # of steps (256)
*
*
* */

public interface Screen {
    
    abstract class Cursor {
        boolean visible;   //
        boolean movable;   // can freeze position, or enable movement
        int position;      // X or Y coordinate, as appropriate (maybe negative means "off screen"?)
        LineType lineType; // regular, bold, dotted etc.
        
        void draw( int newPos ) {     // draws the axis at the new position, erasing previous and updating position variable
            // undraw old
            // draw new
            position = newPos;
        }
        
        
    }
    
    class XCursor extends Cursor { // vertical, for X-axis (time) measurements
        //@Override
        void draw() {}
    }
    
    class YCursor extends Cursor { // horizontal, for Y-axis (voltage) measurements
        //@Override
        void draw() {}
    }
    
    // change default values
    boolean setRawXYSteps( int xDivs, int yDivs );  // sets # of raw steps along X and Y axes
    boolean setRawXSteps( int xDivs );              // sets # of raw steps along X axis
    boolean setRawYSteps( int yDivs );              // sets # of raw steps along Y axis
    
    int getRawXSteps();
    int getRawYSteps();
    
    boolean setScaledXmin( float xMin );
    boolean setScaledXmax( float xMax );
    boolean setScaledYmin( float yMin );
    boolean setScaledYmax( float yMax );
    
}
