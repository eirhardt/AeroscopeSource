package io.aeroscope.aeroscope;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.List;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.BLUE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.MAGENTA;
import static android.graphics.Color.WHITE;
import static android.graphics.Color.YELLOW;
import static io.aeroscope.aeroscope.AeroscopeConstants.AVAILABLE_SECS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.AVAILABLE_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_COUNTS_PER_ADC_BIT;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_OFFSET_VALUE;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_RAW_TRIGGER_LOC;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_READ_START_ADDRS;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_SECS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_TIME_BASE_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_TRIGGER_PCT;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VERT_SENS_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.FRAME_SIZE;
import static io.aeroscope.aeroscope.AeroscopeConstants.HORIZ_SCALE_FACTOR;
import static io.aeroscope.aeroscope.AeroscopeConstants.RAW_X_STEPS;
import static io.aeroscope.aeroscope.AeroscopeConstants.RAW_Y_STEPS;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_SAMPLE_DEPTH;
import static io.aeroscope.aeroscope.AeroscopeConstants.TIME_BASE_DESCRIPTION;
import static io.aeroscope.aeroscope.AeroscopeConstants.TIME_DESCRIPTION_UNITS;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.VERT_SCALE_FACTOR;
import static io.aeroscope.aeroscope.AeroscopeConstants.VERT_SENS_DESCRIPTION;
import static io.aeroscope.aeroscope.AeroscopeConstants.VOLT_DESCRIPTION_UNITS;
import static io.aeroscope.aeroscope.AeroscopeConstants.WRITE_SAMPLE_DEPTH;
import static io.aeroscope.aeroscope.AeroscopeConstants.X_DIVISIONS;
import static io.aeroscope.aeroscope.AeroscopeConstants.X_FORMAT_STRINGS;
import static io.aeroscope.aeroscope.AeroscopeConstants.Y_DIVISIONS;
import static io.aeroscope.aeroscope.AeroscopeConstants.Y_FORMAT_STRINGS;

/*
*
* A Fragment contributes its own layout to the Activity it is part of.
* Android calls onCreateView() when it's time for the Fragment to draw (inflate) its layout.
* The implementation of onCreateView() must return a View that is the root of the Fragment's layout.
* Here, this is inflated from the XML layout resource ID R.layout.fragment_screen (in file fragment_screen.xml)
*
* You can add the Fragment to the Activity layout in the Activity's XML layout file, or programmatically
* See activity_aeroscope_display.xml, where it's declared inside the ConstraintLayout
* In this fragment tag, android:name="io.aeroscope.aeroscope.ScreenFragment" gives the name of the class
* to instantiate for the fragment.
*
* A Fragment can access the parent Activity instance with getActivity(), and do things like
* finding a View in the parent: View listView = getActivity().findViewById(R.id.list_id);
*
* The parent Activity can call the Fragment's methods by first using FragmentManager to find the Fragment
* and set a variable (screenFragment) to reference it. Then call its methods:
*   screenFragment.replaceChartData( newData );
*
* Fragment communication to parent Activity:
*   --Define a callback interface in the Fragment, say, "OnScreenInteractionListener" with methods of your choice
*   --Have the receiving Activity implement the interface, e.g., by overriding methods
*     like void onEntrySelected( Entry selectedPoint )
*   --The Fragment's onAttach() instantiates OnScreenInteractionListener
*     by casting the Activity that is passed into onAttach() as said Listener
*   --This includes techniques to assure that the parent Activity actually implements the interface (see text)
*   --Once that's done, the Listener created can be used to call the interface's methods.
*
*
* IDs:
*     Chart View (contained by FrameLayout in fragment_screen.xml: "@+id/chart_id"
*         given the variable name "chart" in this onCreateView()
*     Fragment (contained by ConstraintLayout in activity_aeroscope_display.xml: "@+id/screen_id"
*         given the variable name "screenFragment" in AeroscopeDisplay's onCreate()
*
* Gestures:
*   Have this class implement OnChartGestureListener, and set itself as the listener:
*      chart.setOnChartGestureListener(this);
*
* */

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ScreenFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link ScreenFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ScreenFragment extends Fragment
        implements OnChartValueSelectedListener, OnChartGestureListener {
    
    private final static String LOG_TAG = "ScreenFragment         "; //  23 chars
    
    // added
    View fragView;  // moved from initialization
    private LineChart chart;
    Activity parentActivity;       // don't know if needed; could speed up a bit
    AsValueFormatter labelScaler;  // applies a scale factor to axis labels before drawing
    float xHighlight, yHighlight;  // point highlighted by user touch
    TextView settingsLine;         // where we write info on-screen TODO: need?
    Description chartDescription;  // text on chart
    String trigDescr;  // for display of trigger voltage in Description
    
    
    // for "real" frame rendering
    float currentSecsPerDiv;  // TODO: keep the AsScreen object updated on these?????
    int currentHorizIndex;    // array index for settings
    float currentVoltsPerDiv;
    int currentVertIndex;
    float currentVMin;
    float currentVMid;        // keep track of voltage offset midpoint
    float currentVMax;
    float currentTMin;
    float currentTMid;        // and for time axis
    float currentTMax;
    float currentTrigLevel;   // SCALED trigger level
    float currentRawV0;       // Note: FLOAT! The raw Y value corresponding to scaled Volts = 0
    float currentRawT0;       // Note: FLOAT! The raw X value corresponding to scaled Time = 0
    boolean isFirstFrame;     // is this the first frame rendered?
    float displayedTrig;      // with current Voltage scaling applied
    
    LimitLine trigTimeLine, trigLevelLine;
            
    long fullFrameTimestamp;
    List<Entry> fullFrameList = new ArrayList<>( 4_096 );
    boolean fullFrameValid;     // do we have usable data?  TODO: use or kill
    RollModeList rollModeList;  // for accumulating & displaying entries in Roll Mode
    
    LineDataSet dataSet;
    LineData lineData;
    String vertSensDescr;  // text description of the selected volts/div
    String timeBaseDescr;  // text description of the selected secs/div
    int currentTriggerPct; // percentage along the X axis where trigger (t=0) is (generally 10, 50, 90)
    
    Handler screenHandler = new Handler();
    
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    
    private String mParam1;
    private String mParam2;
    
    private OnScreenInteractionListener screenListener;  // type name changed from "Fragment" to "Screen", instance from mListener
    
    public ScreenFragment() {
        // Required empty public constructor
    }
    
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ScreenFragment.
     */
    public static ScreenFragment newInstance( String param1, String param2 ) {
        ScreenFragment fragment = new ScreenFragment( );
        Bundle args = new Bundle( );
        args.putString( ARG_PARAM1, param1 );
        args.putString( ARG_PARAM2, param2 );
        fragment.setArguments( args );
        return fragment;
    }
    
    @Override
    // Note: for Fragments, we don't do findViewById() here. See onCreateView()
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        if( getArguments( ) != null ) {
            mParam1 = getArguments( ).getString( ARG_PARAM1 );
            mParam2 = getArguments( ).getString( ARG_PARAM2 );
        }
        parentActivity = getActivity();  // TODO: try this here. Need?
        labelScaler = new AsValueFormatter();
    }
    
    @Override
    // The container parameter passed is the parent ViewGroup (from the Activity's layout)
    // in which this fragment layout will be inserted.  Presumably this is the top-level
    // ConstraintLayout in activity_aeroscope_display.xml.
    public View onCreateView( LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState ) {
        // Inflate the layout for this fragment
        // container is the (Activity) ViewGroup to be the parent of the inflated layout
        // boolean is false: see https://developer.android.com/guide/components/fragments.html
        
        fragView = inflater.inflate( R.layout.fragment_screen, container, false ); // moved from the return statement --Works
        //settingsLine = (TextView) fragView.findViewById( R.id.settings_line );  // TODO: don't need with Description
        
        return fragView;
        // returned View is inserted in place of the <fragment> element in the Activity layout
    }
    

    @Override
    public void onAttach( Context context ) {
        super.onAttach( context );
        if( context instanceof OnScreenInteractionListener ) {  // does calling context (i.e., Activity) implement this interface?
            screenListener = ( OnScreenInteractionListener ) context;
        } else {
            throw new RuntimeException( context.toString( ) + " must implement OnScreenInteractionListener",
                    new ClassCastException( "Trying to set up ScreenFragment<->Activity link" ) );
        }
        Log.d(LOG_TAG, "onAttach( context ) called; screenListener is " + screenListener.toString());
    }
    // HOLY CRAP: today's Android tools or support or whatever update broke the app until I added the following DEPRECATED version!
    // Above version wasn't being called.
    @Override
    @SuppressWarnings( "deprecation" )  //  This version of Fragment is deprecated: maybe switch to "support Fragment"
    public void onAttach( Activity activity ) {
        super.onAttach( activity );
        if( activity instanceof OnScreenInteractionListener ) {  // does calling context (i.e., Activity) implement this interface?
            screenListener = ( OnScreenInteractionListener ) activity;
        } else {
            throw new RuntimeException( activity.toString( ) + " must implement OnScreenInteractionListener",
                    new ClassCastException( "Trying to set up ScreenFragment<->Activity link" ) );
        }
        Log.d(LOG_TAG, "onAttach( activity ) called; screenListener is " + screenListener.toString());  // fixed, I think
    }
    
    @Override
    public void onStart() {  // makes the Fragment visible to user
        super.onStart();
        initializeChart();  // this was causing NPE in onCreateView() because it calls updateChart()
                            // which invokes (indirectly) selectedScope before it exists; seems to work here
        // generateTestChart();  // seems to work fine
    }
    
    @Override
    public void onDetach() {
        super.onDetach( );
        Log.d(LOG_TAG, "onDetach() called; about to set screenListener null");
        screenListener = null;
    }
    
    
    // IMPLEMENTATION OF THE OnChartValueSelectedListener INTERFACE
    @Override
    // e is the selected data Entry, h is the corresponding Highlight object
    public void onValueSelected( Entry e, Highlight h ) {  // called when a value has been selected on the chart
        //Log.d( LOG_TAG, "selected Entry: " + e.toString() + " Highlight: " + h.toString() );  // TODO: eliminate
        xHighlight = e.getX();
        yHighlight = e.getY();
        updateDescription( xHighlight, yHighlight );  // update selected point
        //Log.d( LOG_TAG, "Highlighted point: (" + xHighlight + ", " + yHighlight + ")" );  // saw it work, no need to log
        if( e == null ) Log.d( LOG_TAG, "e is null!" );
        if( screenListener == null ) Log.d( LOG_TAG, "screenListener is null!" );  // TODO: Why is it null? Because of support library update!
        screenListener.onEntrySelected( e );  // send the user-selected entry to AeroscopeDisplay
    }
    @Override
    public void onNothingSelected() {  // called when nothing has been selected or an "un-select" has been made.
        
    }
    // /IMPLEMENTATION OF THE OnChartValueSelectedListener INTERFACE
    
    
    
    
    /** Fragment-to-Activity Communication
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction( Uri uri );  // default skeleton uses a Uri argument
    }
    // Aeroscope Version (implementation in AeroscopeDisplay, called via screenListener.someName)
    public interface OnScreenInteractionListener {
        void onScreenInteraction();  // whatever
        void onEntrySelected( Entry selectedPoint );
        void updateOffsetValue( int newValue, int newIndex );
        //float getTriggerLevel( float vMin, float vMax );  // SCALED value
        float getTriggerLevel( );  // Scaled (from asTrigger)
        //byte setTriggerLevel( float vTrig, float vMin, float vMax );  // SCALED values
        boolean setTriggerLevel( float vTrig );
        boolean setRawTriggerPos( int address );  // set hardware trigger X position
        int getReadStartAddress( );
        boolean setReadStartAddress( int address );
        int getCalibrationValue( int index );  // return the calibration for a Voltage range setting
        void setScreenVoltageBounds( float vMin, float vMax );
        void setScreenTimeBounds( float tMin, float tMax );
        int getFpgaContents( int regAddress );
        void setVerticalSensitivityByIndex( int index );
        void setTimeBaseByIndex( int index );
        int getOffsetDAC( );
        boolean inRollMode( );
        int getRawTriggerLocation();
    }
    
    
    // Test Frame stuff
    DataOps.TestDataFrame testFrame;
    List<Entry> testEntries;
    
    float testVMin = -4f;
    float testVPerDiv = 2f;
    int testRawT0 = 128;
    float testSecsPerDiv = .1f;
    float testVMax;
    
    void generateTestChart() {  // called by onStart()
        testFrame = DataOps.createTestFrame( 512, 128, 100, 2.5 ); // nPoints, rawY0, A, #cycles
        testEntries = DataOps.getScaledData( testFrame, testVMin, testVPerDiv, testRawT0, testSecsPerDiv ); // frame, vMin, volts/div, rawTime0, sec/div
        dataSet = new LineDataSet( testEntries, "Test Label");
        dataSet.setColor( GREEN );
        dataSet.setHighLightColor( WHITE );  // YELLOW best?
        dataSet.setDrawCircles( false );
        dataSet.setValueTextColor(YELLOW);
        dataSet.setDrawValues(true);  // do draw the values for this data set
        lineData = new LineData( dataSet );
        labelScaler.setXScaleByIndex( DEFAULT_VERT_SENS_INDEX );  // TODO: need to adjust to scale
        labelScaler.setYScaleByIndex( DEFAULT_TIME_BASE_INDEX );  // done?
        chart.getAxisLeft().setAxisMinimum( testVMin );
        chart.getAxisLeft().setAxisMaximum( testVMin + (8 * testVPerDiv) );
        chart.setData(lineData);
        chart.highlightValue( /*X value*/ 0f, /*dataSet index*/ 0 );  // this has to come AFTER setData()
        chart.invalidate();
    }
    
    
    // When user changes V or H scales and/or highlighted point or scrolls display
    // Note this is called (among other places) when user changes Vert or Horiz scale in AeroscopeDisplay
    // TODO: changes should feed into supporting classes
    // TODO: handle Roll Mode (done?)
    // Comment inserted to do a baseline commit branching off Jack's More UI branch
    void updateChart( int voltIndex, int timeIndex ) {  // indexes into description arrays (one or both may have changed)
        
        // Need to keep old values for calculations if changing scale
        currentVMid = (currentVMax + currentVMin) / 2f;  // current screen vertical midpoint (scaled units)
        currentTMid = (currentTMax + currentTMin) / 2f;  // current screen horizontal midpoint (scaled units)
        // we want to retain midpoints when changing scales
        // NEW: if we're switching to a lower-resolution timebase, need to adjust X midpoint to an integral multiple of new scale
        // Note that here, currentHorizIndex is actually the previous one we're switching FROM
        if( timeIndex > currentHorizIndex ) {  // greater index means longer timebase (less resolution)
            float newSecsPerDiv = AVAILABLE_SECS_PER_DIV[timeIndex];          // the new time increment
            // adjust center time to closest multiple of new secs/div
            currentTMid = Math.round( currentTMid/newSecsPerDiv ) * newSecsPerDiv;
        }
    
        // calculate the new DAC offset value to place currentVMid at center of Y axis
        // To elaborate: for any scaled midpoint voltage Y, we want the number of DAC counts to add to the midpoint DAC value of 0x8000
        // so that if, for example, Y=0 we add nothing.
        // Y(volts) * (ADC bits/volt) * (DAC counts/ADC bit) = DAC counts (to add to midpoint DAC value)
        // Is this redundant with what changing ranges does?
        int newDacOffset = Math.round( currentVMid * ( RAW_Y_STEPS ) / ( Y_DIVISIONS * AVAILABLE_VOLTS_PER_DIV[voltIndex] ) // note NEW voltIndex
                * DAC_COUNTS_PER_ADC_BIT[voltIndex] )
                + DEFAULT_OFFSET_VALUE;      // = 32768
                // + screenListener.getCalibrationValue( voltIndex );  // no, Channel takes care of this
        screenListener.updateOffsetValue( newDacOffset, voltIndex );   // stores the new offset in AsChannel array
        Log.d( LOG_TAG, "updateChart() called updateOffsetValue() with no Calibration offset: " + newDacOffset );
    
        // now start dealing with new values of voltIndex and timeIndex
        
        currentVertIndex = voltIndex;  // save for future use
        currentHorizIndex = timeIndex;
    
        vertSensDescr = VERT_SENS_DESCRIPTION[voltIndex];  // e.g., "50mv"  -- displays OK in Description
        timeBaseDescr = TIME_BASE_DESCRIPTION[timeIndex];  // e.g., "50ms"  -- displays OK in Description
        
        currentVoltsPerDiv = AVAILABLE_VOLTS_PER_DIV[voltIndex];  // should be fine
        currentSecsPerDiv  = AVAILABLE_SECS_PER_DIV[timeIndex];
        
        // propagate new values to the Channel and Timebase classes  NEW
        screenListener.setVerticalSensitivityByIndex( voltIndex );
        screenListener.setTimeBaseByIndex( timeIndex );
        
        labelScaler.setYScaleByIndex( voltIndex );  // should be fine
        labelScaler.setXScaleByIndex( timeIndex );
        
        // TODO: handle offsets, fix #divisions (remove next 2 lines because only places set are in initializeChart() and onChartFling()
        // BUT: what about when we change Y scale? Keep the same midpoint?
        // currentVMin = (-4f) * currentVoltsPerDiv;   // try to center the X axis -- works but variable # divisions
        // currentVMax = currentVMin + (8f * currentVoltsPerDiv);  // TODO: parameterize 8? (no of vertical divisions)
        // when we change scale, should we keep same offset voltage?
        currentVMin = currentVMid - currentVoltsPerDiv * (Y_DIVISIONS / 2f);  // here, 4 divisions on each side of midpoint
        currentVMax = currentVMid + currentVoltsPerDiv * (Y_DIVISIONS / 2f);
        currentTMin = currentTMid - currentSecsPerDiv * (X_DIVISIONS / 2f);   // 5 divisions on each side of Time midpoint
        currentTMax = currentTMid + currentSecsPerDiv * (X_DIVISIONS / 2f);   // TODO: adjust this depending on whether trigger is at 10%, 50%, or 90%
        //currentTMin = -currentSecsPerDiv * (currentTriggerPct/10f);  // 10 divs on time axis
        //currentTMax = currentTMin + (10f *  currentSecsPerDiv);
        
        // NEW: set Read Memory Start Address
        int readMemoryStartAddrs = Math.round( screenListener.getRawTriggerLocation() - ((0f - currentTMin)/(currentTMax - currentTMin)) * RAW_X_STEPS );
        screenListener.setReadStartAddress( readMemoryStartAddrs );  // set buffer read start address to make T=0 at trigger point
        
        chart.getXAxis().setAxisMinimum( currentTMin );
        chart.getXAxis().setAxisMaximum( currentTMax );
        chart.getAxisLeft().setAxisMinimum( currentVMin );
        chart.getAxisLeft().setAxisMaximum( currentVMax );
        Log.d(LOG_TAG, "Resetting Y axis limits to :" + currentVMin + " -> " + currentVMax ); // seems correct
    
        // Draw the vertical dotted line at T=0
        if(trigTimeLine != null )
                chart.getXAxis().removeLimitLine( trigTimeLine );
        if( currentTMin <= 0f && currentTMax >= 0f ) {       // we know that t=0 is visible on the X axis, so add limit line
            float trigLimitIndex = 0f;                       // limit line at t = 0
            chart.getXAxis().removeAllLimitLines();
            trigTimeLine = new LimitLine( trigLimitIndex );
            trigTimeLine.enableDashedLine( 5f, 7f, 0f );     // line length, space length, phase
            trigTimeLine.setLineColor( YELLOW );
            trigTimeLine.setLineWidth( 1.5f );
            chart.getXAxis().addLimitLine( trigTimeLine );
            trigTimeLine.setEnabled( true );
        }

        lineData = new LineData( dataSet );  // was this the missing magic?  (causes NPE if not loaded)
        chart.setData( lineData );           // and this? Apparently
        updateDescription( xHighlight, yHighlight );
        chart.notifyDataSetChanged();        // how about this? HELPS
        isFirstFrame = true;                 // mark as first frame with updated parameters
        chart.invalidate();                  // redraw
        
        // keep the values in asScreen up to date -- harmless, just set members in screen object
        screenListener.setScreenTimeBounds( currentTMin, currentTMax );
        screenListener.setScreenVoltageBounds( currentVMin, currentVMax );
        
        // recalculate and reset the raw hardware trigger level for the new voltage limits NEW
        screenListener.setTriggerLevel( screenListener.getTriggerLevel() );
        
        if( screenListener.inRollMode() ) {
            rollModeList = new RollModeList( 512 );  // TODO: parameterize
        }
        
    }
    
    // method to update just the bottom description line. Note no need to invalidate & redraw chart
    void updateDescription( float x, float y ) {
        float displayedX = x * HORIZ_SCALE_FACTOR[currentHorizIndex];
        String xUnits = TIME_DESCRIPTION_UNITS[currentHorizIndex];
        float displayedY = y * VERT_SCALE_FACTOR[currentVertIndex];
        String yUnits = VOLT_DESCRIPTION_UNITS[currentVertIndex];
        displayedTrig = currentTrigLevel * VERT_SCALE_FACTOR[currentVertIndex];
        trigDescr = String.format( "TRIG: " + Y_FORMAT_STRINGS[currentVertIndex]  // for use in Description line at bottom of screen
                + VOLT_DESCRIPTION_UNITS[currentVertIndex], displayedTrig );
    
        String highlightCoords = String.format( "   X: " + X_FORMAT_STRINGS[currentHorizIndex] + xUnits  // NEW
                + "   Y: " + Y_FORMAT_STRINGS[currentVertIndex] + yUnits, displayedX, displayedY );
        chartDescription.setText( trigDescr + "   V: " + vertSensDescr + "   H: " + timeBaseDescr + highlightCoords );
    }
    
    
    // Method called from AeroscopeDisplay upon receipt of a frame TODO: figure out threading
    // Call chain: in AeroscopeDisplay, frameRelaySubscriber's onNext() calls renderFrame(), which calls this
    // NEW: if it's a Full Frame (normally 4K) scale & store it instead of trying to render it???
    // Called:
    //     --for each (display) frame received in Run mode
    //     --for the single (display) frame received after a single shot (F) command (scope enters Stop mode)
    //     --for the full buffer (normally 4K) received after a full frame (L) command (scope enters Stop mode)
    // TODO: why doesn't this always work?
    void renderNewFrame( DataOps.DataFrame frame ) {
        
        // TODO: scaled data don't depend on current Y axis settings?
    
        if( frame.expectedLength == 4_096 ) {  // this is a Full Frame  TODO: handle other sizes
        
            isFirstFrame = true;  // TODO: do we need this? NEW (& see below post-draw reset)
        
            // NOTE Read Memory Start Address corresponds to currentTMin! Need to calculate appropriate X range
            // Think something's wrong: displayed trace expands time scale (but not grid mark values)
            float xRange = (currentTMax - currentTMin);          // length of the on-screen time axis in scaled units
            float readDepth = FRAME_SIZE[screenListener.getFpgaContents( READ_SAMPLE_DEPTH )];   // code is an index into the Frame Size array value
            float writeDepth = FRAME_SIZE[screenListener.getFpgaContents( WRITE_SAMPLE_DEPTH )];
            float xFullRange = xRange * writeDepth / readDepth;  // length of the full-buffer time axis in scaled units
            float stepSize = xRange / readDepth;                 // size of each time step in scaled units
            float rawTriggerLoc = (screenListener.getFpgaContents( TRIGGER_XPOS_LO ) & 0xFF) + 256f * (screenListener.getFpgaContents( TRIGGER_XPOS_HI ) & 0xFF);
            float fullTMin = -rawTriggerLoc * stepSize;          // starting T value on the full-frame graph (neg b/c trigger point is T=0)
            float fullTMax = fullTMin + xFullRange;              // ending T value on the full-frame graph
        
            fullFrameList = DataOps.getScaledData( frame, fullTMin, fullTMax, currentVMin, currentVMax );  // get the Full Frame in chartable form
            fullFrameTimestamp = frame.lastTimeStamp;  // when last packet was received
            Log.d( LOG_TAG, "Full Frame stored with timestamp: " + fullFrameTimestamp );
        
            drawFrame( fullFrameList );
        
            isFirstFrame = true;  // TODO: do we need this? NEW get ready to draw a normal frame?
        
        } else if( screenListener.inRollMode() ) {  // we're in Roll Mode (16-entry frame)
            if( frame.expectedLength == 16 ) {      // frame has the expected length TODO: parameterize?
                rollModeList.addFrame( frame, currentTMin, currentTMax, currentVMin, currentVMax );
                currentTMin = rollModeList.frameStartTime;
                currentTMax = rollModeList.frameEndTime;
                chart.getXAxis().setAxisMinimum( currentTMin );
                chart.getXAxis().setAxisMaximum( currentTMax );
                drawFrame( rollModeList );
            } // if 16-byte frame (otherwise ignore, I guess)
        }
        
        else  { // it's not a Full Frame or Roll Mode, process normally
        
            List<Entry> entries = DataOps.getScaledData( frame, currentTMin, currentTMax, currentVMin, currentVMax );
            //isFirstFrame = true;  // TODO: test this scheme to speed redraws up -- seems to not be a problem
            drawFrame( entries );
        }
    
    }
    
    
    // method to generate a chart from list of scaled data points
    private void drawFrame( List<Entry> entries ) {
        if( isFirstFrame ) {  // seems to work
            dataSet = new LineDataSet( entries, "Unused Label" );  // constructor requires these args
            dataSet.setColor( GREEN );
            dataSet.setHighLightColor( MAGENTA );  // tried YELLOW, MAGENTA
            dataSet.setDrawCircles( false );
            dataSet.setValueTextColor( YELLOW );
            dataSet.setDrawValues( true );         // do draw the values for this data set
            isFirstFrame = false;
        } else {  // not the first frame
            dataSet.setValues( entries );          // calls notifyDataSetChanged()
        }
        lineData = new LineData( dataSet );
        chart.setData( lineData );
        chart.notifyDataSetChanged( );             // this help?
        chart.highlightValue( /*X value*/ xHighlight, /*dataSet index*/ 0 );
        chart.invalidate( );
    }
    
    
    
    
    // Class to format axis labels before drawing them
    // TODO: it's being called with wrong axis values at some scales (e.g., multiples of 9 instead of 10)
    static class AsValueFormatter implements IAxisValueFormatter {  // instantiated as labelScaler; works
        
        float xScaleFactor = 1f;  // initialize to no scaling
        float yScaleFactor = 1f;  // initialize to no scaling
        
        public String getFormattedValue( float value, AxisBase axis ) {
            String scaledValueString;
            float scaledValue = 0f;
            //Log.d(LOG_TAG, "Entering AsValueFormatter with value: " + value);
            if( axis instanceof XAxis ) {
                scaledValue = value * xScaleFactor;
            }
            if( axis instanceof YAxis ) {
                scaledValue = value * yScaleFactor;
            }
            //Log.d(LOG_TAG, "Scaled axis value: " + scaledValue);
            scaledValueString = String.valueOf( Math.round( scaledValue ) );  // failed attempt to fix scaling for some values
            //Log.d(LOG_TAG, "Converted to int String: " + scaledValueString);
            return scaledValueString;
        }
        
        // setters for the X and Y label scalers (can set by value or by index into the options arrays)
        public void setXScaleFactor( float xScale ) { xScaleFactor = xScale; }
        public void setYScaleFactor( float yScale ) { yScaleFactor = yScale; }
        public void setXScaleByIndex( int arrayIndex ) { xScaleFactor = HORIZ_SCALE_FACTOR[arrayIndex]; }
        public void setYScaleByIndex( int arrayIndex ) { yScaleFactor = VERT_SCALE_FACTOR[arrayIndex]; }
    }
    
    
    
    void initializeChart() {  // was in onCreateView; seems OK making it into a method
        
        chart = (LineChart) fragView.findViewById( R.id.chart_id ); // chart View inside FrameLayout in fragment_screen.xml
        if( chart == null ) Log.d(LOG_TAG, "null chart returned in initializeChart()");  // was getting null here
        chart.setKeepPositionOnRotation(true);  // maintain the zoom/scroll position after orientation change TODO: right?
        chart.setBackgroundColor( BLACK );
        chart.setNoDataText( "No data available to display" );
        chart.setGridBackgroundColor(YELLOW);
        
        chart.setDrawBorders( false );
        chart.getLegend().setEnabled( false );
        
        chart.getXAxis().setTextColor( YELLOW );
        chart.getXAxis().setValueFormatter( labelScaler );
        chart.getXAxis().setLabelCount( X_DIVISIONS + 1, true );  // X-axis labels -- try 11 for 10 divisions
    
        chart.getAxisLeft().setTextColor( YELLOW );    // only using Left Y axis
        chart.getAxisLeft().setValueFormatter( labelScaler );
        chart.getAxisLeft().setLabelCount( Y_DIVISIONS + 1, true );
        chart.getAxisLeft().setDrawZeroLine( true );   // NEW blue horizontal line at 0 volts  TODO: inaccurate position sometimes?
        chart.getAxisLeft().setZeroLineWidth( 2f );    // NEW 3 too fat
        chart.getAxisLeft().setZeroLineColor( BLUE );  // NEW
    
        // NEW
        //trigTimeLine = new LimitLine( 0f );            // draw limit line at trigger point (t=0)
        // above may be wrong since the docs say the parameter is xIndex, not xValue (and it's being drawn at far left of trace)
        // Here in initializeChart(), try setting the INDEX to 256, middle of the frame
        trigTimeLine = new LimitLine( 256f );            // draw limit line at trigger point (t=0) TODO: parameterize?
        trigTimeLine.enableDashedLine( 5f, 6f, 0f );     // line length, space length, phase
        trigTimeLine.setLineColor( YELLOW );
        trigTimeLine.setLineWidth( 1.5f );
        trigTimeLine.setEnabled( true );                 // NEW try this
        chart.getXAxis().addLimitLine( trigTimeLine );   // not appearing
        
        // not using Right Y Axis
        chart.getAxisRight().setEnabled( false );

        // AxisLine doesn't seem to add anything, removed
    
        chartDescription = new Description();
        chartDescription.setTextColor( Color.YELLOW );
        chartDescription.setTextSize( 12f );
        chartDescription.setTypeface( Typeface.MONOSPACE );
        chartDescription.setTextAlign( Paint.Align.RIGHT );
        chartDescription.setEnabled( true );
        chart.setDescription( chartDescription );  // haven't put any text in it yet
        
                                 // try this: no, it starts in the lower right corner & goes offscreen
        
        // Drawing parameters etc.
        currentSecsPerDiv = DEFAULT_SECS_PER_DIV;      // TODO: may want to restore saved values
        currentHorizIndex = DEFAULT_TIME_BASE_INDEX;
        currentVoltsPerDiv = DEFAULT_VOLTS_PER_DIV;
        currentVertIndex = DEFAULT_VERT_SENS_INDEX;
        currentRawT0 = 256f;  //  initially t = 0 mid-axis  TODO: WHAT ARE THESE FOR?
        currentRawV0 = 128f;  //  initially v = 0 mid-axis  ??
        currentVMin = -4f * DEFAULT_VOLTS_PER_DIV;  // 4 divisions above & below 0 axis
        currentVMax = 4f * DEFAULT_VOLTS_PER_DIV;
        currentTriggerPct = DEFAULT_TRIGGER_PCT;    // 50%  TODO: handle changes in this
        currentTMin = -currentSecsPerDiv * (currentTriggerPct/10f);  // 10 divs on time axis
        currentTMax = currentTMin + (10f *  currentSecsPerDiv);
        Log.d( LOG_TAG, "Time axis initial min and max values: " + currentTMin + ", " + currentTMax );  //  TODO: remove
        
        xHighlight = 0;  // initially at trigger point
        
        labelScaler.setXScaleByIndex( currentHorizIndex );  // set label scalers
        labelScaler.setYScaleByIndex( currentVertIndex );
        
        vertSensDescr = VERT_SENS_DESCRIPTION[DEFAULT_VERT_SENS_INDEX];  // initialize to default text labels, e.g.
        timeBaseDescr = TIME_BASE_DESCRIPTION[DEFAULT_TIME_BASE_INDEX];  // "500ns" and "100mV"
        
        // Interaction Setup
        // this first try disables everything but the Highlight crosshairs
        chart.setTouchEnabled( true );                  // enable touch interaction (it defaults true, but...)
        chart.setDragEnabled( true );                   // NEW
        chart.setPinchZoom( false );                    // disable 2D pinch zooming
        chart.setScaleEnabled( false );                 // NEW: try enabling pinch //disable zooming by gesture(?) NOT WORKING
        chart.setDoubleTapToZoomEnabled( false );       // no zooming by double-tapping chart
        chart.setDragDecelerationEnabled( false );      // no "inertia" with swipes(?)
        chart.setOnChartValueSelectedListener( this );  // see implementation of onValueSelected() and onNothingSelected()
        chart.setHighlightPerTapEnabled( true );        // turn on tap to set highlight
        chart.setHighlightPerDragEnabled( false );      // turn off drag to set highlight
        chart.setOnChartGestureListener( this );
    
        // NEW how about this to check frame rate etc?
        chart.setLogEnabled(true);
        
        isFirstFrame = true;
        
        // Ah, here is where the initial fake frame is displayed! TODO: replace?
        renderNewFrame( DataOps.createTestFrame( 512, 127, 64, 3.0 ) );  // seems to work to initialize & avoid null data exception
        
        // added -- seems to work Except NPE on trying to pass initial call to selectedScope
        //updateChart( currentVertIndex, currentHorizIndex );
        
    }
    
    // Implementation of OnChartGestureListener interface
    // Apparently just sliding your finger on the screen if you're not actually dragging an object is considered a 'FLING"
    @Override
    public void onChartGestureStart( MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture ) {
        
    }
    
    @Override
    public void onChartGestureEnd( MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture ) {
        
    }
    
    @Override  // added after fixing the non-bug and some user feedback  TODO: doesn't work right if trace is shifted vertically
    public void onChartLongPressed( MotionEvent me ) {
        if( me.getX( ) > 0.9f * chart.getWidth() ) {             // near RH edge of screen, set trigger level
            // Hey, wait, maybe Y=0 is at TOP of chart window
            currentTrigLevel = currentVMin + ( (chart.getHeight() - me.getY()) / chart.getHeight() ) * ( currentVMax - currentVMin );
            screenListener.setTriggerLevel( currentTrigLevel );  // send value to asTrigger and to hardware
            updateChart( currentVertIndex, currentHorizIndex );  // we're not changing scale here
            displayTrigLevelLine( );                             // display the trigger level line for a time
        }
    }
    
    @Override
    public void onChartDoubleTapped( MotionEvent me ) {
        Log.d( LOG_TAG, "onChartDoubleTapped() invoked");
        // if in RH 10% of screen, reset Trigger to 0?
        if( me.getX() > 0.9f * chart.getWidth() ) {  // near RH edge; reset Trigger to 0 (or open Trigger options?)
            currentTrigLevel = 0f;
            screenListener.setTriggerLevel( 0f );    // send value to asTrigger and to hardware
            displayTrigLevelLine();                  // display the trigger level line for a time
            
        } else {  // in LH 90% of screen NOTE this throws off trigger level and t=0 line
            // Rezero the Y axis -- works
            currentVMax = (Y_DIVISIONS / 2f) * AVAILABLE_VOLTS_PER_DIV[currentVertIndex];
            currentVMin = -currentVMax;
            screenListener.setScreenVoltageBounds( currentVMin, currentVMax ); // keep asScreen informed
            // Rezero the X axis
            currentTMax = (X_DIVISIONS / 2f) * AVAILABLE_SECS_PER_DIV[currentHorizIndex];
            currentTMin = -currentTMax;
            screenListener.setScreenTimeBounds( currentTMin, currentTMax );    // keep asScreen informed
            
            screenListener.setReadStartAddress( DEFAULT_READ_START_ADDRS );    // Recenter display on zero
            screenListener.setRawTriggerPos( DEFAULT_RAW_TRIGGER_LOC );        // set trigger position to middle of sample memory
            screenListener.setTriggerLevel( currentTrigLevel );                // NEW (desperation) send value to asTrigger and to hardware WORKS??????
        }
        updateChart( currentVertIndex, currentHorizIndex );  // we're not changing scale here, just limits (takes care of DAC too)
    }
    
    @Override
    public void onChartSingleTapped( MotionEvent me ) {  // at RHS of screen, just display trigger level
        if( me.getX() > 0.9f * chart.getWidth() ) {      // near RH edge
            displayTrigLevelLine();                      // display the trigger level line for a time
        }
    }
    
    @Override
    // Motion events describe movements in terms of an action code and a set of axis values.
    // The action code specifies the state change that occurred such as a pointer going down or up.
    // The axis values describe the position and other movement properties.
    // IDEA: drag near right edge of chart to set Trigger DONE
    // IDEA: single-tap near RHS to show trigger level, double-tap to reset it to 0 (or maybe open Trigger options?)
    public void onChartFling( MotionEvent me1, MotionEvent me2, float velocityX, float velocityY ) {
        // (in tests, finger drags are logged as Fling no matter how slow; maybe because not dragging an object?)
        Log.d( LOG_TAG, "onChartFling() entered" );
        if( me1 == null || me2 == null ) {
            Log.d( LOG_TAG, "Bad motion event(s) on Fling: me1 = " + me1.toString() + "; me2 = " + me2.toString() );
        } else {  // neither motion event is null
            float dX = me2.getX( ) - me1.getX( );
            // the sign of dY is backwards TODO: Because Y=0 is at TOP of screen? Think so
            float dY = me1.getY( ) - me2.getY( );
            Log.d( LOG_TAG, "Fling with dX = " + dX + "; dY = " + dY );
            if( Math.abs( dY/dX ) > 3f ) {         // a vertical fling: at least 3 times the vertical movement as horizontal
                
                float dragAsFractionOfHeight = dY / chart.getHeight( );      // neg should be drag DOWN, pos for drag UP
                float dragAsScaledYUnits = dragAsFractionOfHeight * (currentVMax - currentVMin);  // **** sync w/asScreen?
                
                // here we check if the drag was near the right edge of the chart
                float minTrigXPos = 0.9f * chart.getWidth();                 // the right 10% is area for setting Trigger
                if( me1.getX() > minTrigXPos && me2.getX() > minTrigXPos ) { // drag was in rightmost 10% of chart area -- trigger level
                    
                    currentTrigLevel = screenListener.getTriggerLevel( );    // gets SCALED trigger level from asTrigger instance (no side effects)
                    currentTrigLevel += dragAsScaledYUnits;                  // wants addition here because Y increases downward (because backwards sign of me2 above?)
                    
                    // TODO: keep line visible as we drag it?
                    
                    // Guard against drags off screen TODO: right?  seems OK
                    if( currentTrigLevel >= currentVMax ) currentTrigLevel = currentVMax * (255f/256f);  // TODO: parameterize
                    else if( currentTrigLevel < currentVMin ) currentTrigLevel = currentVMin;
                    
                    screenListener.setTriggerLevel( currentTrigLevel );      // propagate to asTrigger & hardware
                    displayTrigLevelLine();                                  // display the new trig level line for a while
                    
                } else {  // drag was in left 90% of chart area
    
                    currentVMax = currentVMax - dragAsScaledYUnits;
                    currentVMin = currentVMin - dragAsScaledYUnits;
                    
                    // constrain the voltage labels to rounded division increments
                    int nVMaxDivs = Math.round( currentVMax/currentVoltsPerDiv ); // number of divs from 0, rounded to closest int
                    currentVMax = nVMaxDivs * currentVoltsPerDiv;                 // round to nearest whole number of divs
                    currentVMin = currentVMax - ( Y_DIVISIONS * currentVoltsPerDiv );
                    
                    screenListener.setScreenVoltageBounds( currentVMin, currentVMax );  // update asScreen variables
                    screenListener.setTriggerLevel( currentTrigLevel );      // the same (but shifted) SCALED trigger level requires a new HARDWARE level in asTrigger
                    
                }
                updateChart( currentVertIndex, currentHorizIndex );  // we're not changing scale here, just limits (takes care of DAC too)
                
            } else if( Math.abs( dX/dY ) > 3f ) {  // it was a horizontal gesture
                // This doesn't fix the trigger position issue
                
                float dragAsFractionOfWidth = dX / chart.getWidth( );         // neg should be drag LEFT, pos for drag RIGHT?
                //Log.d( LOG_TAG, "Calculated drag as fraction of chart width: " + dragAsFractionOfWidth );
                float dragAsScaledXUnits = dragAsFractionOfWidth * (currentTMax - currentTMin);
                //Log.d( LOG_TAG, "Calculated drag as scaled X units: " + dragAsScaledXUnits );
                
                float previousTMin = currentTMin;  // save
                currentTMax = currentTMax - dragAsScaledXUnits;
                currentTMin = currentTMin - dragAsScaledXUnits;
    
                // constrain the time labels to rounded division increments
                int nTMaxDivs = Math.round( currentTMax/currentSecsPerDiv );  // no. of divs from 0 rounded to nearest int
                currentTMax = nTMaxDivs * currentSecsPerDiv;
                currentTMin = currentTMax - ( X_DIVISIONS * currentSecsPerDiv );
                /*float deltaT = currentTMin - previousTMin;  // change in scaled time axis (neg for Right drag, pos for Left)
                
                // WAIT--are we updating the Read Start Address? TODO: works?
                // e.g., a Right drag of 1 screen width should reduce the Read Start Address by 512
                // note setReadStartAddress() returns false if out of buffer range
                // but we have to consider rounding to integral X divisions
                int deltaAddress = Math.round( ( deltaT/currentSecsPerDiv ) * ( (float)RAW_X_STEPS/(float)X_DIVISIONS ) );
                screenListener.setReadStartAddress( screenListener.getReadStartAddress() + deltaAddress );  // sign right? Think so.
                screenListener.setScreenTimeBounds( currentTMin, currentTMax );  // update asScreen variables*/
                        
                // reset the hardware trigger point to the X=0 point on axis  WAIT--have to do this AFTER updating TMin and TMax w/screenListener!
                // WAIT--why are we doing this? For now, shouldn't trigger X pos remain at 0x800?
                //int newTriggerXPos = Math.round( screenListener.getReadStartAddress() - (currentTMin / (currentTMax - currentTMin)) * RAW_X_STEPS );
                //screenListener.setRawTriggerPos( newTriggerXPos );
    
                updateChart( currentVertIndex, currentHorizIndex );  // we're not changing scale here, just limits (takes care of DAC too)
    
            } // if horizontal gesture
        }
    }
    
    @Override  // apparently the pinch zoom  NOT WORKING
    public void onChartScale( MotionEvent me, float scaleX, float scaleY ) {
//  NOTE probably have to chart.setScaleEnabled( true )
//        int newVertIndex = currentVertIndex;
//        float deltaX = me.getX();
//        float deltaY = me.getY();
//        if( Math.abs( deltaY/deltaX ) > 1f ) {  // vertical pinch
//            if( deltaY > 0f ) {  // perhaps enlarge in Y direction (more sensitive)
//                newVertIndex = Math.max( 0, currentVertIndex-1 );
//            } else {
//                newVertIndex  = Math.min( AVAILABLE_VOLTS_PER_DIV.length-1, currentVertIndex+1 );
//            }
//            screenListener.setVerticalSensitivityByIndex( newVertIndex );
//            currentVertIndex = newVertIndex;
//            updateChart( currentVertIndex, currentHorizIndex );
//        }
    }
    
    @Override  // Callback when the chart is moved/translated with a drag gesture (does this mean not ANY drag gesture?)
    public void onChartTranslate( MotionEvent me, float dX, float dY ) {  // probably of no use
    }
    
    /*ChartTouchListener.ChartGesture is an enum with values like DRAG, DOUBLE_TAP, etc. */
    
    void displayTrigLevelLine() {
        // remove any existing trigger level line  NEW
        if( trigLevelLine != null ) chart.getAxisLeft( ).removeLimitLine( trigLevelLine );
        float labelTrig = currentTrigLevel * VERT_SCALE_FACTOR[currentVertIndex];
        String trigLabel = String.format( "Trigger: " + Y_FORMAT_STRINGS[currentVertIndex]
                + VOLT_DESCRIPTION_UNITS[currentVertIndex], labelTrig );
        // updateDescription( xHighlight, yHighlight );  // NEW updateChart() calls it
        trigLevelLine = new LimitLine( currentTrigLevel, trigLabel );
        trigLevelLine.enableDashedLine( 10f, 5f, 0f );  // line length, space length, phase
        trigLevelLine.setLabelPosition( LimitLine.LimitLabelPosition.LEFT_TOP );
        trigLevelLine.setTextColor( YELLOW );
        trigLevelLine.setTypeface( Typeface.MONOSPACE );
        trigLevelLine.setTextSize( 10f );
        trigLevelLine.setLineWidth( 1.5f );
        chart.getAxisLeft().addLimitLine( trigLevelLine );
    
        screenHandler.postDelayed( () -> {  // remove Limit Line after 2.5 sec
            chart.getAxisLeft( ).removeLimitLine( trigLevelLine );
            updateChart( currentVertIndex, currentHorizIndex );  // TODO: maybe just chart.invalidate()?
        }, 2500L );
    }
    
}