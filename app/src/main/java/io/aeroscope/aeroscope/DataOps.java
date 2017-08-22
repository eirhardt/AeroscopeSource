package io.aeroscope.aeroscope;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import rx.schedulers.Timestamped;

import static io.aeroscope.aeroscope.AeroscopeConstants.FRAME_SIZE;
import static io.aeroscope.aeroscope.AeroscopeConstants.PACKETS_PER_FRAME;
import static io.aeroscope.aeroscope.AeroscopeConstants.PACKET_HEADER_COF;
import static io.aeroscope.aeroscope.AeroscopeConstants.PACKET_HEADER_SOF_4096;
import static io.aeroscope.aeroscope.AeroscopeConstants.RAW_Y_STEPS;

/**
 * Created on 2017-02-26.
 */

// class for operations on data packets and frames
class DataOps {
    
    private final static String LOG_TAG = "DataOps                ";           // 23 characters
    private static ArrayList<Timestamped<byte[]>> packetBufUnderConstruction;  // holds accumulated packets for addPacket()
    private static int totalPacketsReceived, packetsReceivedThisBuffer, buffersReturned, framesReturned;
    
    static boolean dataFrameSynced;  // false until we get a clean start of frame
    
    static { // initializer
        //packetBufUnderConstruction = new ArrayList<>( PACKETS_PER_FRAME[PACKET_HEADER_SOF_4096] );  // 216 packets in 4K frame
        // TODO: above suddenly started causing init failure, presumably using constants before defined? [maybe because I didn't Clean Project?]
        packetBufUnderConstruction = new ArrayList<>( 216 );  // 216 packets in 4K frame
        totalPacketsReceived = packetsReceivedThisBuffer = buffersReturned = framesReturned = 0;
    }
    
    // Constructor -- is this called? needed?
    DataOps() {
        packetBufUnderConstruction = new ArrayList<>( PACKETS_PER_FRAME[PACKET_HEADER_SOF_4096] );
    }
    
    // Class for assembling Aeroscope Data Frames
    static class DataFrame {
        
        static long nextSeqNo = 0L;  // frame sequence no. counter
        
        // instance fields (we may wind up omitting some)
        // ** means 'set by constructor'; **** means set by pBuf2dFrame
        long sequenceNo;     // ** frame sequence number (starts at 0)
        long firstTimeStamp; // **** timestamp from first packet in the frame (milliseconds)
        long lastTimeStamp;  // **** timestamp from last packet in the frame
        int expectedLength;  // ** size of frame as specified by header byte
        int actualLength;    // **** # bytes actually received in frame
        boolean complete;    // **** complete, syntactically correct (only valid when completed or broken) (?)
        byte header;         // **** first byte of first packet in a frame // why do we need? TODO
        byte subtrigger;     // **** 2nd byte of first packet TODO: find out what we do with this
        byte[] data;         // **** frame data (excluding header & subtrigger, possibly including end padding)
        // considered a ByteBuffer but don't think it offers any advantages and prob slows things down
        
        // Constructors
        DataFrame( int size ) {
            sequenceNo = ++nextSeqNo;             // starts at 0
            firstTimeStamp = lastTimeStamp = -1L; // -1 for undefined
            expectedLength = size;                // data size value specified by header byte (may be changed by events)
            actualLength = 0;                     // running count of bytes received into frame
            complete = false;                     // set true when done receiving well-formed frame
            data = new byte[size];                // Java allows array size 0
            //Log.d( LOG_TAG, "Frame #" + sequenceNo + " constructed" ); // seems to work
        }
        
        DataFrame() {
            this( 0 );
        }                // no-arg constructor starts data at zero-length
        
        byte[] getData() {
            return data;
        }
        
        long getSequenceNo() {
            return sequenceNo;
        }
        
        long getFirstTimeStamp() {
            return firstTimeStamp;
        }
        
        long getLastTimeStamp() {
            return lastTimeStamp;
        }
        
        int getExpectedLength() {
            return expectedLength;
        }
        
        int getActualLength() {
            return actualLength;
        }
        
        boolean isComplete() {
            return complete;
        }
        
        byte getHeader() {
            return header;
        }
        
        byte getSubtrigger() {
            return subtrigger;
        }
        
        int getTransmissionTimeMillis() {
            return ( int ) (lastTimeStamp - firstTimeStamp);
        } // ms to receive packets
    } // DataFrame object
    
    
    
    // Method to add a received Data packet to the ArrayList
    // called with:
    //     packetArrayList = DataOps.addPacket( packetArrayList, dataPacket ); // returns null or a complete packet buffer
    // should be running on a computation scheduler
    // dataFrameSynced detects if reception is stopped/restarted in middle of a frame (initialized false in setUpNotifications() & setUpDataNotification())
    static ArrayList<Timestamped<byte[]>> addPacket( ArrayList<Timestamped<byte[]>> buffer, Timestamped<byte[]> packet ) {
        totalPacketsReceived++;  // TODO: it is theoretically possible for this to overflow
        //Log.d( LOG_TAG, "addPacket received packet #" + totalPacketsReceived ); // message looks OK
        if( packet.getValue()[0] == PACKET_HEADER_COF ) {    // this is a continuation packet (most will be)
            if( dataFrameSynced ) {                          // ignore the packet if we have not yet synced
                packetsReceivedThisBuffer++;
                packetBufUnderConstruction.add( packet );
            }
            return ( ArrayList<Timestamped<byte[]>> ) null;  // cast to return type (not necessary, but allowed for clarity)
        } else {                                             // this is start of a new frame
            dataFrameSynced = true;                          // we have sync
            if (packetBufUnderConstruction.size() > 0) {     // before starting the new frame, handle the previous one, if any
                // speedup idea: use 2 buffers and switch back and forth between them, rather than copying one to return it
                // may not be worth it since it only happens once per frame, not once per packet
                ArrayList<Timestamped<byte[]>> returnedPacketBuf = new ArrayList<>( packetBufUnderConstruction ); // copy current buffer
                packetBufUnderConstruction.clear( );         // empty it for next frame
                packetBufUnderConstruction.add( packet );    // add first packet of new frame
                packetsReceivedThisBuffer = 1;               // this is first packet of new buffer
                buffersReturned++;                           // TODO: it is theoretically possible for this to overflow
                //Log.d( LOG_TAG, "addPacket returned buffer #" + buffersReturned ); // message looks OK
                return returnedPacketBuf;                    // and return previous buffer
            } else {                                         // the packet buffer under construction is empty
                packetBufUnderConstruction.add(packet);
                packetsReceivedThisBuffer = 1;
                // Log.d( LOG_TAG, "SOF packet added to initially empty buffer.");
                return ( ArrayList<Timestamped<byte[]>> ) null;
            }
        }
    } // addPacket
    
    // Getters
    static int getBuffersReturned() { return buffersReturned; }
    static int getPacketsReceivedThisBuffer() { return packetsReceivedThisBuffer; }
    static int getTotalPacketsReceived() { return totalPacketsReceived; }
    static int getFramesReturned() { return framesReturned; }
    
    
    // Function that maps a completed packet buffer to a frame (should be on computation scheduler)
    static DataFrame pBuf2dFrame( ArrayList<Timestamped<byte[]>> pBuf ) {
        
        int frameSize;
        Iterator<Timestamped<byte[]>> packetIterator = pBuf.iterator();
        Timestamped<byte[]> currentPacket = packetIterator.next();      // get first packet
        byte[] packetPayload = currentPacket.getValue( );               // unwrap the Timestamp
        long packetTimestamp = currentPacket.getTimestampMillis( );
        
        byte packetHeader = packetPayload[0];   // legal header values are 1, 5, 6, 9 for 16, 256, 512, 4K frames
        byte subtrigger = packetPayload[1];
        
        frameSize = FRAME_SIZE[packetHeader];   // presumably faster than previous switch (upcast byte to int for array index)
        
        DataFrame newFrame = new DataFrame( frameSize );  // initializes expectedLength, actualLength
        newFrame.firstTimeStamp = packetTimestamp;
        newFrame.lastTimeStamp = packetTimestamp; // could be a 1-packet frame
        newFrame.header = packetHeader;
        newFrame.subtrigger = subtrigger;
        
        for( int i = 2; i < packetPayload.length; i++ ) { // data in first packet starts at index 2
            newFrame.data[i-2] = packetPayload[i];
            if( ++newFrame.actualLength >= newFrame.expectedLength ) break;  // done
        }
        // done with first packet, do rest (if any)
        gotAllBytes:
        while( packetIterator.hasNext() ) {
            currentPacket = packetIterator.next();   // get next packet data
            newFrame.lastTimeStamp = currentPacket.getTimestampMillis();
            packetPayload = currentPacket.getValue();
            for( int i = 1; i < packetPayload.length; i++ ) { // data in subsequent packets starts at index 1
                newFrame.data[newFrame.actualLength] = packetPayload[i];
                if( ++newFrame.actualLength >= newFrame.expectedLength ) break gotAllBytes;  // done
            }
        } // while there are more packets
        
        // did we get the expected number of bytes?
        if( newFrame.actualLength == newFrame.expectedLength ) newFrame.complete = true;
        framesReturned++;             // TODO: it is theoretically possible for this to overflow
        //Log.d( LOG_TAG, "Finished constructing frame " + framesReturned + " of " + newFrame.actualLength + " bytes"); // looks OK
        return newFrame;

        
    } // pBuf2dFrame
    
    
    
    static class TestDataFrame extends DataFrame {
        TestDataFrame( int size ) {
            super( size );
        }
        void setData( byte[] newData ) { data = newData; }
        void setSequenceNo( int newSeqNo ) { sequenceNo = newSeqNo; }
        void setFirstTimeStamp( long newFirstT ) { firstTimeStamp = newFirstT; }
        void setLastTimeStamp( long newLastT ) { lastTimeStamp = newLastT; }
        void setExpectedLength( int newExpected ) { expectedLength = newExpected; }
        void setActualLength( int newActual ) { actualLength = newActual; }
        void setComplete( boolean isComplete ) { complete = isComplete; }
        void setHeader( byte newHeader ) { header = newHeader; }
        void setSubtrigger( byte newSubTrig ) { subtrigger = newSubTrig; }
    }
    
    /** Factory Method to create and return a DataFrame for testing
     *
     * @param nPoints: number of data points in the Frame--usually 512 but can be anything;
     *               other Aeroscope-supported values are 1024, 2048, 4096 (and 16 in Roll mode)
     *               (there's a question about 256 as a supported size, and 1K and 2K too)
     * @param yZeroPos: vertical position (0-255) of the X axis. 127 for centered in the display
     * @param amplitude: amplitude (in pixels) of the waveform. With a centered X axis, setting
     *                 to 128 will make the waveform occupy the entire height of the display.
     *                 Suggested setting: 100 (with a centered X axis)
     * @param cycles: number of full cycles of the waveform to generate. Note this is a double
     *              so you can have a non-integral number of cycles.
     * @return : a TestDataFrame object initialized with the standard initial settings provided by
     *          the DataFrame constructor, EXCEPT that
     *              the data array is filled
     *              the header byte is set to the correct value if size is one of the supported
     *                  values, else set to 0xFF
     *              the subtrigger byte is set to 0x7F, for easy identification
     *              actualLength is set equal to expectedLength, and
     *              complete is set true.
     *          Methods are provided to change any fields as desired.
     *
     *          EXAMPLE USE: DataFrame myTestFrame = DataOps.createTestFrame( 512, 127, 100, 2.5 );
     */
    static TestDataFrame createTestFrame( int nPoints, int yZeroPos, int amplitude, double cycles ) {
        
        TestDataFrame testFrame = new TestDataFrame( nPoints );
        
        double deltaX = 2 * cycles * Math.PI / nPoints;
        for( int i = 0; i < nPoints; i++ ) {
            testFrame.data[i] = (byte) ( yZeroPos + amplitude * Math.sin( i * deltaX ) + 0.5 ); // rounded (note values >127 read as negative bytes)
        }
        switch( nPoints ) {
            case 16: testFrame.header = 1; break;
            case 256: testFrame.header = 2; break;
            case 512: testFrame.header = 3; break;
            case 4096: testFrame.header = 4; break;
            default: testFrame.header = -1; // =0xFF
        }
        testFrame.subtrigger = 127; // = 0x7F
        testFrame.actualLength = testFrame.expectedLength;  // change this if you want
        testFrame.complete = true;                          // change if you want
        return testFrame;
        
    }

    
    static float scaledVolts( int rawVolts, int rawZeroPoint, float voltsPerDiv ) { // raw values 0..255 are displayable
        return (rawVolts - rawZeroPoint) * voltsPerDiv / 32f;
    }
    static int rawVolts( float scaledVolts, int rawZeroPoint, float voltsPerDiv ) {
        return Math.round( 32f * scaledVolts/voltsPerDiv + rawZeroPoint );  // rounded to nearest int
    }
    
    

    // Using the above analysis, convert a frame of samples into scaled time and voltage values
    
    /** Convert raw data and time values into scaled values based on the set scale factors
     *
     * @param frame: a DataFrame of samples
     * @param vMin: the minimum scaled voltage on the vertical axis
     * @param voltsPerDiv: the vertical scale in volts/division
     * @param time0: the RAW time value (0..511, typically) corresponding to scaled time = 0
     *               Used to indicate the trigger point (pre-trigger events are in negative time)
     * @param secsPerDiv: the horizontal scale in seconds/division
     * @return: a list of data points in MPAndroidChart Entry form, ready to begin graphing
     *
     * Note that since we will specify Vmin and Vmax chart axis values, data points may be off scale
     */
    static List<Entry> getScaledData( final DataFrame frame, float vMin, float voltsPerDiv, int time0, float secsPerDiv ) {
        final int vertDivs = 8;                       // 8 divisions on vertical voltage axis
        final int horizDivs = 10;                     // 10 divisions on the horizontal time axis
        final float vertRawSteps = 256f;              // raw steps on vertical voltage axis (floats to avoid integer divide(?))
        final float horizRawSteps = 512f;             // raw steps on horizontal time axis (floats to avoid integer divide(?))
        final float vMax = vMin + (vertDivs * voltsPerDiv);
        final float Vrange = vMax - vMin;             // voltage range of vertical axis
        final float tRange = horizDivs * secsPerDiv;  // voltage range of horizontal time axis
        final float tMin = (-time0) * (tRange/horizRawSteps); // scaled time value at left end of horiz axis
    
        List<Entry> dataPoints = new ArrayList<>( frame.actualLength );
        for(int i=0; i<frame.actualLength; i++) {
            float tVal = tMin + (i/horizRawSteps) * tRange;
            float vVal = vMin + ( (frame.data[i] & 0xFF) / vertRawSteps ) * Vrange;  // watch the negative bytes!
            dataPoints.add( new Entry(tVal, vVal) );
        }
        return dataPoints;
    }
    
    // New version with different signature (used for live data, not test frame)
    // First Rebuild operation: try to handle subtrigger
    static List<Entry> getScaledData( final DataFrame frame, float tMin, float tMax, float vMin, float vMax ) {
        float tRange = tMax - tMin;
        float tStep = tRange/frame.actualLength;
        float vRange = vMax - vMin;
        float vStep = vRange/256f;  // TODO: parameterize?
        
        float tShift = (frame.subtrigger/64f) * tStep;  // time shift is s/64 sample intervals
        
        List<Entry> dataPoints = new ArrayList<>( frame.actualLength );  // TODO: reuse ArrayList to save time?
        for(int i=0; i<frame.actualLength; i++) {
            float tVal = tMin + i * tStep + tShift;     // adjust by the subtrigger-derived delta (have to add, not subtract)
            float vVal = vMin + ( frame.data[i] & 0xFF ) * vStep;  // watch the negative bytes!
            dataPoints.add( new Entry(tVal, vVal) );
        }
        return dataPoints;
        
    }
    
    // get the raw vertical value for a scaled value -- note result is a float; may have to Math.round to nearest integer
    // REMOVED: ad hoc solution: constrain result to 0..255 (converting scaledV = scaledVmax would yield 256)
    // caller may want to know if value was off-scale
    static float rawVolts( float scaledV, float scaledVmin, float scaledVmax ) {
        float volts = (scaledV - scaledVmin) / (scaledVmax - scaledVmin) * RAW_Y_STEPS;  // 256 raw steps
        //if( volts < 0f ) return 0f;
        //if( volts > 255f ) return 255f;  // NEW allow returning out-of-range values TODO: OK?
        return volts;
    }
    
    static float scaledVolts( float rawV, float scaledVmin, float scaledVmax ) { // valid rawV values 0..255
        return scaledVmin + (rawV / RAW_Y_STEPS) * (scaledVmax - scaledVmin );
    }
    
} // class DataOps

