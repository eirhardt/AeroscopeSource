package io.aeroscope.aeroscope;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 2017-07-02.
 *
 * Have to keep track of starting time as it rolls along
 *
 */

public class RollModeList extends ArrayList<Entry> {
    
    final int FRAME_SIZE = 16;  // in Roll Mode, frames are 16 samples
    final int FRAME_SLOTS = 32; // 32 screen slots x 16 samples/slot = 512 samples
    
    /*In Roll Mode, the screen is divided into 32 16-sample "slots"
    * We start writing at the left edge (slot 0), and keep appending 16-sample frames until slots 0-31 are filled.
    * At this point, each new frame is handled by throwing away the slot 0 data, and shifting other slots
    * left by one. This frees up slot 31 for the new frame.
    * This requires adjusting the starting and ending X values that we send to the chart drawing module for each new frame.
    * The adjusted values are entered in frameStartTime and frameEndTime, and the calling routine
    * fetches them to update its currentTMin and currentTMax values before redrawing the chart.
    * */
    int frameSlot;              // next slot to use: 0 for the leftmost 16-sample packet, 31 for the rightmost
    float frameStartTime;       // start time for the screen display (not just the new frame slot); adjusted for each new frame once screen is full
    float frameEndTime;         // end time for the screen display (not just the new frame slot)
    
    // constructor
    public RollModeList( int size ) {
        super( size );
        frameSlot = 0;
        frameStartTime = 0f;
        frameEndTime = 0f;
    }
    
    // add a 16-sample frame to the right side of the chart, making room on the left edge if necessary
    void addFrame( DataOps.DataFrame newFrame, float currentTMin, float currentTMax, float currentVMin, float currentVMax ) {
    
        float slotWidth = (currentTMax - currentTMin) / FRAME_SLOTS;  // width of each slot in scaled time units
        float slotTMin = currentTMin + frameSlot * slotWidth;         // starting scaled time value for the slot
        float slotTMax = slotTMin + slotWidth;                        // time value of end of slot
    
        // scale the new frame data to fit the chart
        List<Entry> slotData = DataOps.getScaledData( newFrame, slotTMin, slotTMax, currentVMin, currentVMax );  // convert the 16 new points
    
        if( frameSlot >= FRAME_SLOTS ) {                    // array is full
            this.subList( 0, FRAME_SIZE ).clear();          // remove the leftmost slot's data & shift the rest left
            this.frameStartTime = currentTMin + slotWidth;  // increase the frame start time...
            this.frameEndTime = currentTMax + slotWidth;    //     ...and end time for the new chart draw
        } else {                                            // array isn't full yet--no need to adjust drawn frame start & stop times
            frameStartTime = currentTMin;
            frameEndTime = currentTMax;
            frameSlot++;
        }
        this.addAll( slotData );                            // append this frame's data to the end of the array
    
    
    }
    
}
