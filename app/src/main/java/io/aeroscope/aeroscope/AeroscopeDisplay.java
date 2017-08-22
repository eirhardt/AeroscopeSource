package io.aeroscope.aeroscope;

import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.aeroscope.oscilloscope.Trigger;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.schedulers.Timestamped;
import rx.subjects.BehaviorSubject;

import static io.aeroscope.aeroscope.AeroscopeConstants.CANCEL_FRAME;
import static io.aeroscope.aeroscope.AeroscopeConstants.CLEAR_CAL_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.DATA_RX_PRIORITY;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEEP_SLEEP_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_OFFSET_VALUE;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_TIME_BASE_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_VERT_SENS_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.DUMMY_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.FETCH_POWER_STATE;
import static io.aeroscope.aeroscope.AeroscopeConstants.FPGA_OFF_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.FULL_FRAME_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.MAX_FRAME_SIZE;
import static io.aeroscope.aeroscope.AeroscopeConstants.PACKETS_PER_FRAME;
import static io.aeroscope.aeroscope.AeroscopeConstants.PACKET_HEADER_SOF_4096;
import static io.aeroscope.aeroscope.AeroscopeConstants.POWER_FULL;
import static io.aeroscope.aeroscope.AeroscopeConstants.POWER_ON_DELAY_MS;
import static io.aeroscope.aeroscope.AeroscopeConstants.PowerState;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_START_ADDRS_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_START_ADDRS_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.RESET_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.RUN_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.RUN_MODE;
import static io.aeroscope.aeroscope.AeroscopeConstants.SET_NAME;
import static io.aeroscope.aeroscope.AeroscopeConstants.SET_NAME_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.SINGLE_FRAME_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.STOP_CANCEL_DELAY_MS;
import static io.aeroscope.aeroscope.AeroscopeConstants.STOP_INDEX;
import static io.aeroscope.aeroscope.AeroscopeConstants.STOP_MODE;
import static io.aeroscope.aeroscope.AeroscopeConstants.asDataCharId;
import static io.aeroscope.aeroscope.AeroscopeConstants.asOutputCharId;

/*
* Subscriptions:
** dataNotifSub: for Data (frame) notifications; subscribed in onResume() & elsewhere I think, unsubscribed in onPause() NEW
** stateChangeSub: for connection state change notifications; subscribed in onStart(), unsubscribed in onStop()
** heartSub: for heartbeat; subscribed in onResume(), unsubscribed in onPause()
** frameRelayerSub: to relay frames for drawing; subscribed in onResume(), unsubscribed in onPause()
** connectionFlasherSub: probably not needed; subscribed in onResume(), unsubscribed in onPause()
** buttonSub: button presses: subscribed in onResume(), unsubscribed in onPause()
** connSub: BLE connection; unsubscribed in onStop()
** outNotifSub: subscribed in setUpNotifications(); added unsubscribe in new onDestroy() NEW
*
** frameRelayer: call its onCompleted() in onDestroy() to make sure it's done
** selectedScope.buttonDownSubject: same
* */



public class AeroscopeDisplay extends AppCompatActivity implements ServiceConnection, ScreenFragment.OnScreenInteractionListener {
    
    private final static String LOG_TAG = "AeroscopeDisplay       "; // tag for logging (23 chars max)
    
    // set by onServiceConnected() to AeroscopeBluetoothService:
    private AeroscopeBluetoothService asBleServiceRef;       // reference to the BLE Service instance
    private RxBleClient asBleClientRef;                      // reference to the BLE Client set by onServiceConnected() TODO: need?
    
    AtomicBoolean asBleServiceConnected = new AtomicBoolean( false );   // whether Bluetooth Service is bound (set true; false on disconnect) TODO: need?
    AeroscopeDevice selectedScope;                         // set when user selects a device from scan results
    int heartbeatTick;                                     // "serial number" of heartbeat tick (note an int, not a long) TODO: unused? keep?
    
    // Android UI declarations
    Intent bindAsBleIntent;      // can pass extra data to the Service if we need to  TODO: need?
    Intent receivingIntent;      // made global
    TextView chosenDevice;       // where name of chosen device is displayed
    ImageView batteryIndicator;
    Spinner aeroscopeTimeBaseSpinner;
    Spinner aeroscopeVerticalSensSpinner;
    Spinner aeroscopeCommandSelectSpinner;
    Button triggerType;
    Switch ACDC;
    Button startStopButton;

    int selectedSensitivityIndex = DEFAULT_VERT_SENS_INDEX;  // array index of selected Vertical Sensitivity from Spinner
    int selectedTimebaseIndex = DEFAULT_TIME_BASE_INDEX;     // array index of selected Time Base from Spinner TODO: restore these in onResume(?)
    int selectedCommandIndex;                                // array index of selected Command from Spinner
    byte[] userSelectedCommand;                              // command string for user selection from command spinner
    
    Handler cancelHandler;                                   // for scheduling operations with postDelayed() etc.

    //**********************   Trigger & Coupling Mode Dialogues   ********************///

    final CharSequence[] triggerOptions = {" Rising "," Falling "," NR "," Auto "};



    // Screen Fragment stuff
    FragmentManager fragManager;
    ScreenFragment screenFragment;
    Entry userSelectedDataPoint;     // tapping on a point on the curve, e.g.
    
    // Observable BLE stuff
    // The Subscriptions we may or may not use:
    Subscription connSub, heartSub, outNotifSub, dataNotifSub, buttonSub,
            stateChangeSub, frameRelayerSub, frameRendererSub; //  TODO: which do we need?
    Observable<Long> heartbeat;
    volatile RxBleConnection.RxBleConnectionState bleConnectionState;
    FrameSubscriber frameRelayerSubscriber;           // made in onCreate()
    BehaviorSubject<DataOps.DataFrame> frameRelayer;  // subscribe to this to get new Frames as they are made available (by calling its onNext())
    Observable<DataOps.DataFrame> frameRenderer;      // receives Frames from frameRelayer and renders to scope (maybe someday)
    ArrayList<Timestamped<byte[]>> packetArrayList;   // where arriving packets are assembled into an ArrayList (not really)
    
    DataOps.DataFrame frameReturned;                  // used in onDataNotificationReceived(); here to save object allocation overhead(?)
    
    
    
    class FrameSubscriber extends Subscriber<DataOps.DataFrame> {  // got rid of TestSubscriber
        @Override // when subscriber is activated but has not received anything
        public void onStart() {
            super.onStart( );
            Log.d( LOG_TAG, "FrameSubscriber onStart() invoked" );
        }
        @Override
        public void onNext( DataOps.DataFrame frame ) {
            renderFrame( frame );
        }
        @Override
        public void onError( Throwable e ) {
            if( BuildConfig.DEBUG )
                Log.d( LOG_TAG, "FrameSubscriber onError() invoked with error: " + e.getMessage( ) ); // NEW
            handleFrameError( e );  // TODO: processing of a Frame error (use special TestSubscriber method calls?)
        }
        @Override
        public void onCompleted() {
            if( BuildConfig.DEBUG ) Log.d( LOG_TAG, "FrameSubscriber onCompleted() invoked" );
        }
    }
    
    
    // LIFECYCLE METHODS
    @Override
    @SuppressWarnings( "static-access" )  // OK to call static methods with asBleServiceRef (instance reference)
    protected void onCreate( Bundle savedInstanceState ) {  // first called when Activity is launched
        super.onCreate( savedInstanceState );

        setContentView( R.layout.activity_aeroscope_display );
        chosenDevice = ( TextView ) findViewById( R.id.deviceName );
        batteryIndicator = (ImageView) findViewById( R.id.batteryImg );
        aeroscopeTimeBaseSpinner = ( Spinner ) findViewById( R.id.timebaseSpinner );
        aeroscopeVerticalSensSpinner = ( Spinner ) findViewById( R.id.verticalSpinner );
        aeroscopeCommandSelectSpinner = ( Spinner ) findViewById( R.id.aeroscopeCommands );

        triggerType = (Button) findViewById(R.id.triggerButton);



        // Fragments for the Aeroscope screen. Others?
        fragManager = getFragmentManager( );
        screenFragment = ( ScreenFragment ) fragManager.findFragmentById( R.id.screen_id );

        heartbeat = Observable  // subscribe to this (in onResume()) to start heartbeat; unsubscribe (in onPause()) to stop --Works fine
                .interval( AeroscopeConstants.HEARTBEAT_SECOND, TimeUnit.SECONDS ) // was 6 sec, changed to 15
                .observeOn( AndroidSchedulers.mainThread( ) )
                .doOnNext( this::doHeartbeat );
        
        // TODO: initialize user preferences here (using saved values or defaults if they don't exist yet)
        // Something like:
        // mySettings = new AeroscopeSettings();
        // mySettings.load();   // inserts default values for any settings that aren't defined
        // for any settings that might be changed, copy their values to local variables
        // pass a reference to the settings object to AeroscopeDevice so it can see them
        
        frameRelayer = BehaviorSubject.create( );
        frameRelayerSubscriber = new FrameSubscriber( );  // its onNext() calls renderFrame()
        
        packetArrayList = new ArrayList<>( PACKETS_PER_FRAME[PACKET_HEADER_SOF_4096] );  // initially empty, with enough room for largest (4K) frame; 216 packets
    
        cancelHandler = new Handler( );

    
    
        // LISTENERS
        // handles timeBase TODO: set the default setting (which will eventually probably be in Preferences)
        ArrayAdapter<CharSequence> timeBaseAdapter = ArrayAdapter.createFromResource( this, R.array.timePerDiv, android.R.layout.simple_spinner_item );
        timeBaseAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        aeroscopeTimeBaseSpinner.setAdapter( timeBaseAdapter );
        aeroscopeTimeBaseSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener( ) {
                    @Override
                    public void onItemSelected( AdapterView<?> adapterView, View view, int i, long l ) {
                        selectedTimebaseIndex = i;
                        //selectedScope.asTimeBase.setTimeBaseByIndex( i );  // also sets rollMode -- handled by updateChart()
                        screenFragment.updateChart( selectedSensitivityIndex, selectedTimebaseIndex ); // TODO: fix updateChart
                    }
                    @Override
                    public void onNothingSelected( AdapterView<?> adapterView ) { }  // TODO: what does this do?
                }
        );
        
        // handles verticalSens
        // TODO: hopefully this is the last place where we're seeing trigger level anomalies:
        // After changing vertical sensitivity, the displayed trigger level apparently isn't the actual trigger level being used
        ArrayAdapter<CharSequence> verticalSensAdapter = ArrayAdapter.createFromResource( this, R.array.voltsPerDiv, android.R.layout.simple_spinner_item );
        verticalSensAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        aeroscopeVerticalSensSpinner.setAdapter( verticalSensAdapter );
        aeroscopeVerticalSensSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener( ) {
                    @Override
                    public void onItemSelected( AdapterView<?> adapterView, View view, int i, long l ) {
                        selectedSensitivityIndex = i;
                        //selectedScope.asChannel.setVertSensByIndex( i );  // handled by updateChart()
                        // need to updated scaled Vmin and Vmax to get correct new hardware raw trigger level: NEW: added to updateChart()
                        screenFragment.updateChart( selectedSensitivityIndex, selectedTimebaseIndex ); // TODO: fix updateChart
                    }
                    @Override
                    public void onNothingSelected( AdapterView<?> adapterView ) { }
                }
        );
    
        // handles aeroscopeCommands TODO: set the default setting (which will eventually probably be in Preferences)
        ArrayAdapter<CharSequence> commandsAdapter = ArrayAdapter.createFromResource( this, R.array.aeroscopeCommands, android.R.layout.simple_spinner_item );
        commandsAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        aeroscopeCommandSelectSpinner.setAdapter( commandsAdapter );
        aeroscopeCommandSelectSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener( ) {  // AIC to implement Listener
            @Override
            // NOTE default selection is auto-executed during Spinner initialization
            // with recent changes, the dummy "Command..." is at the top of the command list
            // Allegedly this is called when you change the selection with setSelection(), too
            public void onItemSelected( AdapterView<?> adapterView, View view, int i, long l ) {
                userSelectedCommand = AeroscopeConstants.USER_COMMAND_ARRAY[i];  // get the byte[20] (note this is an instance member)
                selectedCommandIndex = i;                                        // branch Stop/Run/Button: special handling of some commands (this, too)
                
                switch( selectedCommandIndex ) {  // process the command
                    
                    case DUMMY_INDEX:  // what to do here? First try: just let it fall through to STOP.
                    
                    case STOP_INDEX:  // 'S': User has selected a STOP command: stop sending frames (if a frame has been requested
                                      // but not triggered, it will be sent when triggered unless first CANCELed.)
                        Log.d( LOG_TAG, "Command Spinner selected STOP");
                        selectedScope.sendCommand( STOP_MODE );                          // send the STOP command (changed from userSelectedCommand)
                        dataNotifSub.unsubscribe();                                      // taking this out causes crazy traces
                        selectedScope.isRunning.set( false );
                        startStopButton.setText("Run");                                  // NEW
                        //  after STOP, wait 1 second then CANCEL
                        if( cancelHandler.postDelayed( () -> selectedScope.sendCommand( CANCEL_FRAME ), STOP_CANCEL_DELAY_MS ) ) {  // true on successful enqueue
                            Log.d( LOG_TAG, "Command Spinner STOP scheduled CANCEL in " + STOP_CANCEL_DELAY_MS + " ms" );
                        } else {
                            Log.d( LOG_TAG, "Command Spinner STOP error scheduling CANCEL" );
                        }
                        break;
                    
                    case RUN_INDEX:  // 'R': user selected RUN: scope will continually acquire and send data frames
                        startStopButton.setText("...");  // NEW -- why isn't it displayed?
                        Log.d( LOG_TAG, "Command Spinner selected RUN");
                        if( selectedScope.powerState != PowerState.DEVICE_POWER_FULL ) {  // NEW
                            selectedScope.sendCommand( POWER_FULL );
                            SystemClock.sleep( POWER_ON_DELAY_MS );  // wait for FPGA init; try 1500 ms NEW
                            // refresh operating parameters, which seem to be reset to defaults
                            // but note you can't issue the same command twice in a row
                            //aeroscopeTimeBaseSpinner.setSelection( selectedTimebaseIndex );
                            //aeroscopeVerticalSensSpinner.setSelection( selectedSensitivityIndex );
                            selectedScope.asTimeBase.setTimeBaseByIndex( selectedTimebaseIndex );
                            selectedScope.asChannel.setVertSensByIndex( selectedSensitivityIndex );
                            screenFragment.updateChart( selectedSensitivityIndex, selectedTimebaseIndex );
                            // TODO: what about other parameters like coupling and trigger?
                        }
                        if( dataNotifSub == null || dataNotifSub.isUnsubscribed() ) {
                            setUpDataNotification( );                         // sets dataFrameSynced false, establishes dataNotifSub
                        }
                        selectedScope.sendCommand( RUN_MODE );                // send the RUN command (changed from userSelectedCommand)
                        selectedScope.isRunning.set( true );
                        startStopButton.setText("Stop");
                        break;
                    
                    case SET_NAME_INDEX:  // 'N': up to 19 characters, null-terminated (no effect on RUN/STOP status, presumably)
                        Log.d( LOG_TAG, "Command Spinner selected SET_NAME");
                        AlertDialog.Builder nameAlert = new AlertDialog.Builder(AeroscopeDisplay.this);
                        nameAlert.setTitle("Set Aeroscope Name");
                        nameAlert.setMessage("Enter a new name for your Aeroscope (19 chars max)");
                        // Set an EditText view to get user input
                        final EditText nameInput = new EditText(AeroscopeDisplay.this);
                        nameInput.setText( selectedScope.bleDevice.getName(), TextView.BufferType.EDITABLE );
                        nameAlert.setView(nameInput);
                        nameAlert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                byte[] newName = nameInput.getText().toString().getBytes( StandardCharsets.US_ASCII );
                                if( newName.length > 19 ) newName = Arrays.copyOf( newName, 19 );  // truncate excessive length
                                byte[] nameCmd = Arrays.copyOf( SET_NAME, 20 );  // 'N' & 19 nulls (null will indicate end of name if not full 19 chars)
                                System.arraycopy( newName, 0, nameCmd, 1, newName.length );  // copy from newName[0] -> nameCmd[1] for length of newName
                                selectedScope.sendCommand( nameCmd );
                                chosenDevice.setText( selectedScope.bleDevice.getName( ) );  // update displayed name
                            }
                        });
                        nameAlert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // Canceled.
                            }
                        });
                        nameAlert.show();
                        break;
                    
                    case DEEP_SLEEP_INDEX:  // 'ZZ' lowest power mode--doesn't advertise; have to attach charger or press button to wake
                        Log.d( LOG_TAG, "Command Spinner selected Sleep");
                        selectedScope.sendCommand( userSelectedCommand );                // send the SLEEP command
                        selectedScope.isRunning.set( false );
                        Toast.makeText( AeroscopeDisplay.this, selectedScope.bleDevice.getName() + " is going to sleep...", Toast.LENGTH_SHORT ).show();
                        AeroscopeDisplay.this.finish();                                  // avoid "Unfortunately..."
                        break;
                    
                    case FULL_FRAME_INDEX:  // 'L': send current full frame buffer & stop (doesn't capture any new samples)
                        // Alex: the most reliable way to make FULL FRAME work is from STOP mode, request a SINGLE FRAME,
                        // wait to begin receiving that frame, and then request a FULL FRAME.
                        // Doesn't work for me so far, but STOP, FULL, SINGLE does (with expanded display on FULL that goes away on SINGLE)
                        Log.d( LOG_TAG, "Command Spinner selected Full Frame");
                        if( dataNotifSub == null || dataNotifSub.isUnsubscribed() ) {
                            setUpDataNotification( );                                    // sets dataFrameSynced false  NEW
                        }
                        selectedScope.sendCommand( userSelectedCommand );                // send the command (scope should respond by sending 4K frame)
                        selectedScope.isRunning.set( false );
                        startStopButton.setText("Run");                                  // NEW
                        break;                                                           // TODO: why doesn't this always work?
                    
                    case SINGLE_FRAME_INDEX:  // 'F': send next frame captured & stop (will wait for a trigger unless CANCELed)
                        // Alex: If single is pressed in RUN mode, I recommend putting the scope into STOP mode and then send a FULL FRAME request.
                        Log.d( LOG_TAG, "Command Spinner selected Single Frame");
                        if( dataNotifSub == null || dataNotifSub.isUnsubscribed() ) {
                            setUpDataNotification( );                         // sets dataFrameSynced false  NEW
                        }
                        selectedScope.sendCommand( userSelectedCommand );                // send the command (scope should respond by sending 4K frame)
                        selectedScope.isRunning.set( false );
                        startStopButton.setText("Run");                                  // NEW
                        break;                                                           // TODO: why doesn't this always work?
                    
                    case RESET_INDEX:  // 'ZR': resets MCU & power-cycles FPGA
                        Log.d( LOG_TAG, "Command Spinner selected Reset");
                        selectedScope.sendCommand( userSelectedCommand );                // send the Reset command (scope should disconnect)
                        selectedScope.isRunning.set( false );
                        Toast.makeText( AeroscopeDisplay.this, selectedScope.bleDevice.getName() + " reset, closing...", Toast.LENGTH_SHORT ).show();
                        AeroscopeDisplay.this.finish();                                  // avoid "Unfortunately..." (calls all the end-of-life methods, reportedly)
                        break;
                    
                    case CLEAR_CAL_INDEX:  // 'CX': clear calibration parameters (apparently no effect on RUN/STOP)
                                           // (not sure of the usefulness given that scope automatically applies calibration parameters)
                        selectedScope.sendCommand( userSelectedCommand );                // send the Clear Calibration Parameters command
                        Arrays.fill( selectedScope.asChannel.calibrationValue, 0 );      // zero the parameter array
                        selectedScope.asChannel.currentCalValue = 0;                     // and the current value
                        Log.d( LOG_TAG, "Clear Calibration Parameters command ran" );
                        break;
                    
                    case FPGA_OFF_INDEX:  // 'PO': turn off power to FPGA; disables sending frame data
                        selectedScope.sendCommand( userSelectedCommand );
                        selectedScope.isRunning.set( false );  // can't be running if data can't be sent, right?
                        startStopButton.setText("Run");                                  // NEW
                        break;
                        
                    default: // currently for Cancel Frame ('X'), Calibrate ('CI'), Full Power ('PF'), Send Telemetry ('QTI'),
                             // Get Version ('QVR'), Fetch Error Log ('QE'), Fetch Cal Params ('QC'), Fetch Power State ('QP'), Clear Error Log ('EX')
                        selectedScope.sendCommand( userSelectedCommand );                // send the command (none of the above)
                        
                }  // command switch
                
            }  // onItemSelected handler
            @Override
            public void onNothingSelected( AdapterView<?> adapterView ) { }
        } );  // end of AIC and set...Listener
        
        //TODO: SharedPreferences ?
        receivingIntent = getIntent( );  // TODO: need this?
        
    } // onCreate
    
    @Override
    protected void onStart() {
        super.onStart( );

        int defaultValue = -1;  // initialize to illegal index (must supply a defaultValue in next line)
        int positionValue = receivingIntent.getIntExtra( "selectedScopeIndex", defaultValue );  // array index in (1-item) Vector of discovered Aeroscopes (always 0)
        
        selectedScope = asBleServiceRef.asDeviceVector.elementAt( positionValue ); //Our reference to the user-selected scope
        chosenDevice.setText( selectedScope.bleDevice.getName( ) );  // name of chosen device to UI TextView
    
        // INITIATE CONNECTION TO BLUETOOTH, SET UP CONNECTION STATE MONITORING, OTHER NOTIFICATIONS
        connSub = selectedScope.connectBle( ); // returns a Subscription; connSubscriber does basically nothing but store the connection in bleConnection var
        stateChangeSub = selectedScope.bleDevice.observeConnectionStateChanges( )
                .subscribeOn( Schedulers.io( ) )  // TODO: unnecessary; remove?
                .observeOn( AndroidSchedulers.mainThread( ) )
                .subscribe( connState -> {
                            bleConnectionState = connState;  // save updated connection state in volatile variable
                            updateConnectionIndicator( );
                            Log.d( LOG_TAG, "New connection state is: " + connState.toString() );
                            if( connState == RxBleConnection.RxBleConnectionState.CONNECTED )
                                doOnConnected( );
                            else if( connState == RxBleConnection.RxBleConnectionState.DISCONNECTED )
                                doOnDisconnected();
                            // Could handle CONNECTING & DISCONNECTING here
                            // (have seen a crash at transition from DISCONNECTED to CONNECTING
                        }
                );
    
        Toast.makeText( this, "Connecting...", Toast.LENGTH_LONG ).show();

        //Analog & Digital Coupling Mode
        ACDC = (Switch) findViewById(R.id.ACDCSwitch);
        ACDC.setChecked(selectedScope.asChannel.getDcCoupling());
        ACDC.setText("DC");
        ACDC.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean switchOn) {
                if (switchOn) {
                    selectedScope.setDcCoupled(true);
                    ACDC.setText("DC");
                } else {
                    selectedScope.setDcCoupled(false);
                    ACDC.setText("AC");
                }
            }
        });

        startStopButton = (Button) findViewById(R.id.startStop);
        startStopButton.setText("RUN");
    }
    
    
    // Handle making a connection (send a STOP, wait for full power?)
    void doOnConnected( ) {
        Log.d( LOG_TAG, "doOnConnected() entered; setting up notifications & sending STOP" );  // NEW
        setUpNotifications();  // Moved up to here so we can receive Output messages from scope
        selectedScope.sendCommand( STOP_MODE ); // TODO: need to make UI reflect this?
        aeroscopeCommandSelectSpinner.setSelection( DUMMY_INDEX );  // changed from STOP_INDEX
        int powerReadCount = 0;
        do { // wait for FULL POWER
            SystemClock.sleep( 250L );  //  NEW substitute for Thread.sleep()
            selectedScope.sendCommand( FETCH_POWER_STATE );  // sets the powerState variable
            // queries are apparently being queued but not sent due to all the sleeping, so Power State isn't getting updated
        } while( selectedScope.powerState != PowerState.DEVICE_POWER_FULL && ++powerReadCount < 10 );  // NEW: reduce count from 20
        
        //selectedScope.powerState = PowerState.DEVICE_POWER_FULL;  // set the flag  // this version should set the flag when message received
        
        Log.d( LOG_TAG, "Reads of power state on connecting: " + powerReadCount );
        //setUpNotifications();                                           // set Output & Data notifications (moved up)
        selectedScope.sendCommand( AeroscopeConstants.SEND_TELEMETRY ); // get Telemetry (for initial battery voltage)
        selectedScope.asChannel.setOffsetDAC( DEFAULT_OFFSET_VALUE );   // 32768 to center trace
        doHeartbeat( -1L );                                             // see if we can speed up battery indicator
    }
    // Handle a disconnect (saw "Unfortunately, Aeroscope has stopped" after the Toast)
    void doOnDisconnected( ) {
        // TODO: reconnect? other?
        Toast.makeText( this, "Connection to " + selectedScope.bleDevice.getName() + " lost...", Toast.LENGTH_LONG ).show();
        // TODO: save stuff?
        // TODO: go back to MainActivity?
        this.finish();  // close this Activity and return to parent (MainActivity)
    }
    
    
    @Override
    protected void onPause() {
        super.onPause( );
        heartSub.unsubscribe( );             // shut off heartbeat

        dataNotifSub.unsubscribe();          // NEW: stop Data

        frameRelayerSub.unsubscribe( );      // stop sending frames TODO: stop notifications
        connectionFlasherSub.unsubscribe();  // stop the connection indicator
        if( buttonSub != null ) buttonSub.unsubscribe();  // stop the Button Down notifications
        //frameRendererSub.unsubscribe( );   // stop rendering any received frames  // TODO: try it with this?
        //TODO: need to save current settings for vertical sensitivity, timebase, mode?
    }
    
    
    @Override
    protected void onResume() {
        super.onResume( );
        if( connSub == null || connSub.isUnsubscribed() ) connSub = selectedScope.connectBle( );  // NEW
        // TODO: reestablish notifications etc.? Make sure connected?
        heartSub = heartbeat.subscribe( );
        frameRelayerSub = frameRelayer
                .observeOn( AndroidSchedulers.mainThread() )   // TODO: investigate
                .onBackpressureLatest()                        // NEW: don't buffer frames, just send the latest when requested
                .subscribe( frameRelayerSubscriber );          // resume frame processing TODO: this help? May have stopped error
        //frameRendererSub = frameRenderer.subscribe( );       // resume rendering of received frames  // TODO: try it with this?
        connectionFlasherSub = connectionFlasher.subscribe();  // re-enable the connection state indicator
        buttonSub = selectedScope.buttonDownSubject.subscribe( this::doOnButtonDown );  // seems to work!
        
        if( dataNotifSub == null || dataNotifSub.isUnsubscribed() ) setUpDataNotification();  // NEW: in case disabled NEWER (was setUpNotifications)
        
        //TODO: restore vertical sensitivity, timebase, mode -- do these do anything (can't use same selection twice in a row)??
        aeroscopeTimeBaseSpinner.setSelection( selectedTimebaseIndex );
        aeroscopeVerticalSensSpinner.setSelection( selectedSensitivityIndex );
        aeroscopeCommandSelectSpinner.setSelection( selectedCommandIndex );
    }
    
    @Override
    protected void onStop() {
        stateChangeSub.unsubscribe(); // NEW; we weren't unsubscribing this anywhere before (onStart() subscribes)
        //probably where we should disconnect the bluetooth (it's connected in onStart())
        connSub.unsubscribe( );
        Log.d( LOG_TAG, "onStop() unsubscribed connSub to disconnect BLE" );
        super.onStop( );        // some advise calling this last--trying it NEW
    }

    @Override
    protected void onDestroy() {
        if( outNotifSub != null && !outNotifSub.isUnsubscribed() ) outNotifSub.unsubscribe();  // never unsubscribed anywhere NEW
        if( frameRelayer != null ) frameRelayer.onCompleted();          // make sure the Subject is done NEW
        if( selectedScope.buttonDownSubject != null ) selectedScope.buttonDownSubject.onCompleted();  // NEW
        super.onDestroy();
    }
    
    /* END OF LIFECYCLE METHODS */
    
    
    
    // HEARTBEAT (called by the heartbeat Observable's doOnNext())
    void doHeartbeat( Long interval ) {
        Log.d( LOG_TAG, "doHeartbeat() entered with interval: " + interval );
        heartbeatTick = interval.intValue( );  // update the sequential value just received
        // TODO: future firmware may eliminate need (auto-sends Telemetry every 30 sec?)
        selectedScope.sendCommand( AeroscopeConstants.SEND_TELEMETRY );   // get Telemetry (for battery voltage)
        updateBatteryIndicator();
        selectedScope.readBleSignalStrength();  // readable as selectedScope.bleSignalStrength // NEW: seems to be working
        updateSignalIndicator();
        // TODO: anything else on the 6 (or 15) second heartbeat?
    }
    
    
    // UTILITY METHODS
    
    // called by heartbeat or whenever we want to update the battery/charging condition UI
    void updateBatteryIndicator() {
        float actualBattVolts = 4.16568f * (selectedScope.getBatteryCondition( ) / 255f);
        float batteryPercentage = (actualBattVolts / 4.16568f) * 100f;
        
        Log.d( LOG_TAG, "updateBatteryIndicator() battery percentage: " + batteryPercentage );
        
        if (batteryPercentage > 80f) {
            batteryIndicator.setImageResource(R.drawable.onehundred);
        } else if (batteryPercentage > 65f) {
            batteryIndicator.setImageResource(R.drawable.seventyfive);
        } else if (batteryPercentage > 35f) {
            batteryIndicator.setImageResource(R.drawable.fifty);
        } else if (batteryPercentage > 20f) {
            batteryIndicator.setImageResource(R.drawable.twentyfive);
        } else {
            batteryIndicator.setImageResource(R.drawable.ten);
        }

        if (selectedScope.chargerConnected) {
            batteryIndicator.setImageResource(R.drawable.charging);
        }
        
        batteryIndicator.invalidate();  // added NEW

    }
    
    // called to update RSSI indicator (if we have one)
    void updateSignalIndicator() {
        Log.d( LOG_TAG, "updateSignalIndicator() called" );
        // TODO: whatever
    }
    
    // called to update Connection State indicator
    void updateConnectionIndicator( ) {
        Log.d( LOG_TAG, "updateConnectionIndicator() called" );
        // TODO: whatever
    }
    
    
    // onNext() invoked when the Button Down Observable emits a timestamp
    // Note Spinner commands take care of updating the isRunning flag
    void doOnButtonDown( Long timeMs ) {
        Log.d( LOG_TAG, "doOnButtonDown() at: " + timeMs.toString() );
        if( selectedScope.isRunning.get() ) {                          // we're running, so Stop
            aeroscopeCommandSelectSpinner.setSelection( STOP_INDEX );  // select the Command Spinner STOP command
        } else {
            aeroscopeCommandSelectSpinner.setSelection( RUN_INDEX );   // select the Command Spinner RUN command
        }
    }
    
    // Method to read the Output characteristic once (seems to have been working OK; presume 1-item Observable)
    // TODO: probably don't need this with subscription to notifications
    void readOutputCharacteristic() {
        selectedScope.getConnectionObservable( )
                .flatMap( rxBleConnection -> rxBleConnection.readCharacteristic( asOutputCharId ) )
                .timestamp( )
                .observeOn( AndroidSchedulers.mainThread( ) )
                .subscribe( response -> onOutputMessageReceived( response, false ),
                        this::onOutputError ); // added boolean "isNotification" param to onOutputMessageReceived()
    }
    
    
    // Method to set up Output (and Data) notifications (called when connection state becomes CONNECTED)
    // Called from Connection State Observer in onStart()
    void setUpNotifications() {
        // Output characteristic notifications: was reporting error, apparently because wasn't connected
        outNotifSub = selectedScope.getConnectionObservable( )
                .subscribeOn( Schedulers.io() )  // try this to avoid queueing delay on main thread(?)
                .flatMap( rxBleConnection -> rxBleConnection.setupNotification( asOutputCharId ) )
                .doOnNext( this::outputNotificationHasBeenSetUp )
                .doOnError( this::outputNotificationSetupError )  // error: setupNotification on a null ref (maybe not connected?)
                .flatMap( notificationObservable -> notificationObservable )  // transform the Observable into a string of items
                .timestamp( )
                //.observeOn( AndroidSchedulers.mainThread( ) )  // maybe we don't need this because doesn't seem to affect UI
                .doOnUnsubscribe( () -> Log.d( LOG_TAG, "Output Notification was unsubscribed" ) )
                .subscribe( this::onOutputNotificationReceived, this::onOutputNotificationError );
        
        
        // This next block handles the probe's transmission of Data back to the application/user
        // This version receives and delivers individual packets of a Frame
        // TODO: experiment with threading to hopefully speed up
        DataOps.dataFrameSynced = false; // initially we have to wait for a SOF packet to sync Frame reception  NEW
        dataNotifSub = selectedScope.getConnectionObservable( )
                .subscribeOn( Schedulers.computation( ) )  // NEW try this to maybe dedicate 1 of the 4 cores to packet/frame handling
                .flatMap( rxBleConnection -> rxBleConnection.setupNotification( asDataCharId ) )
                .doOnNext( notificationObservable -> runOnUiThread( this::dataNotificationHasBeenSetUp ) )
                .doOnError( setupThrowable -> runOnUiThread( this::dataNotificationSetupError ) )
                .flatMap( notificationObservable -> notificationObservable )
                .timestamp( )
                //.observeOn(AndroidSchedulers.mainThread())  // NEW try removing this (but beware of UI action downstream!)
                .doOnUnsubscribe( () -> Log.d( LOG_TAG, "doOnUnsubscribe() invoked by dataNotifSub" ) )
                .subscribe( this::onDataNotificationReceived, this::onDataNotificationError ); // these should execute on selected thread
    }  // setUpNotifications
    
    
    // NEW: just set up Data, not Output notification
    void setUpDataNotification() {
        DataOps.dataFrameSynced = false; // initially we have to wait for a SOF packet to sync Frame reception  NEW
        dataNotifSub = selectedScope.getConnectionObservable( )
                .subscribeOn( Schedulers.computation( ) )  // try this to dedicate 1 of the 4 cores to packet/frame handling
                .flatMap( rxBleConnection -> rxBleConnection.setupNotification( asDataCharId ) )
                .doOnNext( notificationObservable -> runOnUiThread( this::dataNotificationHasBeenSetUp ) )
                .doOnError( setupThrowable -> runOnUiThread( this::dataNotificationSetupError ) )
                .flatMap( notificationObservable -> notificationObservable )
                .timestamp( )
                //.observeOn(AndroidSchedulers.mainThread())  // NEW try removing this (but beware of UI action downstream!)
                .doOnUnsubscribe( () -> Log.d( LOG_TAG, "doOnUnsubscribe() invoked by dataNotifSub" ) )
                .subscribe( this::onDataNotificationReceived, this::onDataNotificationError ); // these should execute on selected thread
    }
    
    
    
    void outputNotificationHasBeenSetUp( Observable<byte[]> notifObs ) { // not running on UI thread any more
        Log.d( LOG_TAG, "outputNotificationHasBeenSetUp has finished for Output; result: " + notifObs.toString( ) );
    }
    
    void outputNotificationSetupError( Throwable setupError ) { // not running on UI thread any more
        Log.d( LOG_TAG, "Error setting up Output notifications: " + setupError.getMessage( ) ); // "attempt to setupNotification on a null ref"
        // onError() can't throw unchecked exception:
    }
    
    void onOutputNotificationReceived( Timestamped<byte[]> outputMessage ) {
        Log.d( LOG_TAG, "onOutputNotificationReceived() about to call onOutputMessageReceived" );
        onOutputMessageReceived( outputMessage, true );  // handles both notifications and direct reads of Output (not currently using the latter)
    }
    
    void onOutputNotificationError( Throwable error ) { // "2 exceptions occurred"
        Log.d( LOG_TAG, "onOutputNotificationError: " + error.getMessage( ) );
    }
    
    
    //********************************************************************************************\\
    // When Data packets are received (run on a computation Scheduler)
    void onDataNotificationReceived( Timestamped<byte[]> dataPacket ) {  //
        // empty packetArrayList is created in onCreate()
        Thread.currentThread( ).setPriority( DATA_RX_PRIORITY );  // NEW let's try 8 (of 10 max)
        packetArrayList = DataOps.addPacket( packetArrayList, dataPacket ); // returns null or a complete packet buffer
        if( packetArrayList != null ) {  // we have a new complete packet buffer
            frameReturned = DataOps.pBuf2dFrame( packetArrayList );  // global variable
            // TODO: calculate & log frame rate using frame timestamps?
            // TODO: if we implement bad frame detection in pBuf2dFrame, we could get a null returned
            // maybe: if( frameReturned != null ) frameRelayer.onNext( frameReturned );
            frameRelayer.onNext( frameReturned );  // send the new frame to the BehaviorSubject (seems to work OK)
        }
    }
    //********************************************************************************************\\
    
    // ALERT: this is no longer run on main thread; we're trying a computation Scheduler
    void onDataNotificationError( Throwable error ) {
        // First try using DEBUG
        if( BuildConfig.DEBUG ) Log.d( LOG_TAG, "onDataNotificationError: " + error.getMessage( ) );
    }
    
    void dataNotificationHasBeenSetUp() { //
        Log.d( LOG_TAG, "dataNotificationHasBeenSetUp has finished for Data" );  // logged OK
    }
    
    void dataNotificationSetupError() { //
        Log.d( LOG_TAG, "Error setting up Data notifications" );
        // onError() can't throw unchecked exception:
        //throw new RuntimeException( "Data notification setup error" );  // TODO: for now
    }
    //*****************************************
    
    
    // Boolean specifies whether message is an unsolicited Notification or a Response to a read command
    // called by both readOutputCharacteristic() subscribe and onOutputNotificationReceived() handler
    void onOutputMessageReceived( Timestamped<byte[]> message, boolean isNotification ) {  // NEW: boolean arg
        String msgType = isNotification ? "Notification" : "Response    ";
        String fullMsg = message.getTimestampMillis( ) + " Output " + msgType + ": " + HexString.bytesToHex( message.getValue( ) );
        Log.d( LOG_TAG, fullMsg );
        selectedScope.handleOutputFromAeroscope( message ); // seems to work
        Log.d( LOG_TAG, "onOutputMessageReceived() returned from handleOutputFromAeroscope" );
    }
    
    void onOutputError( Throwable error ) { // called by readOutputCharacteristic() subscribe
        Log.d( LOG_TAG, "Error in Output message from Aeroscope: " + error.getMessage( ) );
    }
    
    void onWriteInputSuccess( byte[] bytesWritten ) { // handler for write Input characteristic success
        Log.d( LOG_TAG, "Message written to Aeroscope Input: " + HexString.bytesToHex( bytesWritten ) );
    }
    
    void onWriteInputError( Throwable writeError ) { // handler for write Input characteristic error
        Log.d( LOG_TAG, "Write error in Input message to Aeroscope: " + writeError.getMessage( ) );
    }
    
    void onWriteStateSuccess( byte[] bytesWritten ) {
        Log.d( LOG_TAG, "Message written to Aeroscope State: " + HexString.bytesToHex( bytesWritten ) );
    }
    
    void onWriteStateError( Throwable writeError ) {
        Log.d( LOG_TAG, "Write error in State message to Aeroscope: " + writeError.getMessage( ) );
    }
    
    
    // method called by frameRenderer's onNext() to render each received frame
    // should be running this on new computation thread (must display on UI thread)
    void renderFrame( DataOps.DataFrame frame ) {  // TODO: need?
        screenFragment.renderNewFrame( frame );
    }
    
    
    void handleFrameError( Throwable frameError ) { // we think this runs on UI thread
        Log.d( LOG_TAG, "Error in delivering completed frame: " + frameError.getMessage() ); // getMessage NEW
    }
    
    
/*------------------------------------------------------------------------------------------------*/

    // Implementation of the OnScreenInteractionListener interface defined in ScreenFragment
    // Needed for communication from ScreenFragment to its containing Activity and therefrom to the world
    // (maybe to tell this activity that user has pinch-zoomed, or something?)
    // Screen Fragment Interaction
    @Override
    public void onScreenInteraction() {}
    @Override
    public void onEntrySelected( Entry selectedPoint ) {  // when user highlights a data point
        userSelectedDataPoint = selectedPoint;
    }
    
    @Override
    public void updateOffsetValue( int newValue, int newIndex ) {  // save the new uncorrected Y offset in array
        //selectedScope.asChannel.setOffsetDAC( newValue, selectedSensitivityIndex );  // is this THE BUG?
        selectedScope.asChannel.updateOffsetValue( newValue, newIndex );
        selectedSensitivityIndex = newIndex;
        Log.d( LOG_TAG, "AeroscopeDisplay.updateOffsetValue() called asChannel.updateOffsetValue() with new (no Calibration) value: " + newValue );
    }
    
    @Override
    public float getTriggerLevel( ) { return selectedScope.asTrigger.level; }
    
    @Override  // returns false and clips HARDWARE trigger level to 0..255 if requested SCALED level is outside hardware range.
    public boolean setTriggerLevel( float vTrig ) { return selectedScope.asTrigger.setLevel( vTrig ); }
    
    @Override
    public boolean setRawTriggerPos( int address ) {  // set hardware trigger X position
        return selectedScope.asTrigger.setRawTriggerLocation( address );
    }
    
    @Override
    public int getReadStartAddress( ) {
        return (selectedScope.fpgaRegisterBlock[READ_START_ADDRS_LO] & 0xFF) + 256 * (selectedScope.fpgaRegisterBlock[READ_START_ADDRS_HI] & 0x0F);
    }
    
    @Override  // returns false if address out of buffer range
    public boolean setReadStartAddress( int address ) {
        if( address >= 0 && address < MAX_FRAME_SIZE ) {
            selectedScope.fpgaRegisterBlock[READ_START_ADDRS_LO] = (byte) (address & 0xFF);
            selectedScope.fpgaRegisterBlock[READ_START_ADDRS_HI] = (byte) ( (address >>> 8) & 0x0F );
            selectedScope.copyFpgaRegisterBlockToAeroscope();
            return true;
        } else return false;
    }
    
    @Override
    public int getCalibrationValue( int vertIndex ) { return selectedScope.asChannel.calibrationValue[vertIndex]; }
    
    @Override
    public void setScreenVoltageBounds( float vMin, float vMax) {  // communicate Y range to the asScreen instance
        selectedScope.asScreen.scaledYmin = vMin;
        selectedScope.asScreen.scaledYmax = vMax;
    }
    @Override
    public void setScreenTimeBounds( float tMin, float tMax) {  // communicate Y range to the asScreen instance
        selectedScope.asScreen.scaledXmin = tMin;
        selectedScope.asScreen.scaledXmax = tMax;
    }
    @Override
    public int getFpgaContents( int regAddress ) { return selectedScope.fpgaRegisterBlock[regAddress]; }
    
    @Override
    public void setVerticalSensitivityByIndex( int index ) {
        selectedSensitivityIndex = index;
        selectedScope.asChannel.setVertSensByIndex( index );
    }
    
    @Override
    public void setTimeBaseByIndex( int index ) {
        selectedTimebaseIndex = index;
        selectedScope.asTimeBase.setTimeBaseByIndex( index );
    }
    
    @Override  // read what we think is the actual value in the DAC (as reflected in RAM copy)
    public int getOffsetDAC( ) {  // note it's a 16-bit unsigned value  TODO: unused, eliminate(?) (used for testing?)
        return (selectedScope.fpgaRegisterBlock[DAC_CTRL_LO] & 0xff) + 256 * (selectedScope.fpgaRegisterBlock[DAC_CTRL_HI] & 0xff);
    }
    
    @Override  // get the Roll Mode flag
    public boolean inRollMode( ) {
        return selectedScope.rollMode;
    }

    @Override  // get the Raw Trigger Address
    public int getRawTriggerLocation() { return selectedScope.asTrigger.getRawTriggerLocation(); }

    
/*------------------------------------------------------------------------------------------------*/
    
    
    
    
    // Added for connection state indication TODO: implement something
    volatile boolean onOffToggle;  // for alternate flashing
    Subscription connectionFlasherSub;
    Observable<Long> connectionFlasher = Observable.interval( 500L, TimeUnit.MILLISECONDS )
            .doOnNext( nextInt -> {
                // display lit or unlit indicator of appropriate color
                switch( bleConnectionState.toString() ) {
                    case "RxBleConnectionState{CONNECTING}":  // flashing green
                        if( onOffToggle ) ;  // display green, else display nothing
                        break;
                    case "RxBleConnectionState{CONNECTED}":  // steady green
                        // display steady green indicator
                        break;
                    case "RxBleConnectionState{DISCONNECTING}":  // flashing red
                        if( onOffToggle ) ;  // display red, else display nothing
                        break;
                    case "RxBleConnectionState{DISCONNECTED}":  // steady red
                        // display the steady red indicator
                        break;
                    default:
                        Log.d( LOG_TAG, "ERROR: unrecognized value of ConnectionState: " + bleConnectionState.toString() );
                }
                onOffToggle = !onOffToggle;
            } );
    
    
    // SERVICE CONNECTION to AeroscopeBluetoothService-- seems to be working OK
    
    @Override
    @SuppressWarnings( "static-access" )  // OK to access static method getRxBleClient() with instance ref asBleServiceRef
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Log.d( LOG_TAG, "Entering onServiceConnected()" ); //
        // We've bound to Service, cast the IBinder and get Service instance
        AeroscopeBluetoothService.LocalBinder asBleBinder = (AeroscopeBluetoothService.LocalBinder) service;
        // (casting it makes compiler aware of existence of getService() method)
        asBleServiceRef = asBleBinder.getService(); // returns the instance of the Service
        if( asBleServiceRef == null ) throw new RuntimeException( LOG_TAG
                + ": onServiceConnected returned null from getService()" );
    
        // ****** NEW pass this to Service for use in Toasts in handleScanError()
        //asBleServiceRef.setCallingContext( AeroscopeDisplay.this );
    
        asBleClientRef = asBleServiceRef.getRxBleClient(); // static member accessed by instance reference--OK!
        if( asBleClientRef == null ) throw new RuntimeException( LOG_TAG
                + ": onServiceConnected returned null from getRxBleClient()" );
        
        asBleServiceConnected.set( true ) ; // success: set "bound" flag
        Log.d( LOG_TAG, "Finished onServiceConnected()" ); // reached here OK
    }

    @Override
    // typically happens when the process hosting the service has crashed or been killed
    // doesn't remove the ServiceConnection; binding remains active and onServiceConnected() is called when service is next running
    public void onServiceDisconnected( ComponentName name ) {
        // TODO: what else goes here?
        asBleServiceConnected.set( false );  // variable doesn't seem to be used, but anyway
        Log.d( LOG_TAG, "onServiceDisconnected() was called with component " + name.flattenToString() );
    }



    public void openTriggerDialogue(View userPush) {

        // Rising = 0, Falling = 1, NR = 2, Auto = 3

        EnumSet<Trigger.Mode> selectedTriggerOptions = selectedScope.asTrigger.getEnabledModes();

        boolean[] selectedBoxes = new boolean[4];

        // rewrite without "if"

        selectedBoxes[0] = selectedScope.asTrigger.isEnabled(Trigger.Mode.RISING);
        selectedBoxes[1] = selectedScope.asTrigger.isEnabled(Trigger.Mode.FALLING);
        selectedBoxes[2] = selectedScope.asTrigger.isEnabled(Trigger.Mode.NOISE_REDUCED);
        selectedBoxes[3] = selectedScope.asTrigger.isEnabled(Trigger.Mode.AUTO);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose Trigger Options")
                .setMultiChoiceItems(triggerOptions, selectedBoxes, new DialogInterface.OnMultiChoiceClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                                switch(indexSelected) {
                                    case 0:
                                        if(isChecked) selectedTriggerOptions.add(Trigger.Mode.RISING);
                                        else selectedTriggerOptions.remove(Trigger.Mode.RISING);
                                        break;
                                    case 1:
                                        if(isChecked) selectedTriggerOptions.add(Trigger.Mode.FALLING);
                                        else selectedTriggerOptions.remove(Trigger.Mode.FALLING);
                                        break;
                                    case 2:
                                        if(isChecked) selectedTriggerOptions.add(Trigger.Mode.NOISE_REDUCED);
                                        else selectedTriggerOptions.remove(Trigger.Mode.NOISE_REDUCED);
                                        break;
                                    case 3:
                                        if(isChecked) selectedTriggerOptions.add(Trigger.Mode.AUTO);
                                        else selectedTriggerOptions.remove(Trigger.Mode.AUTO);
                                        break;
                                }
                            }
                        }
                )
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                //  Your code when user clicked on OK
                                //  You can write the code  to save the selected item here

                                selectedScope.asTrigger.enableJustModeSet(selectedTriggerOptions);
                            }
                        }
                )
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                //  if the user clicks "Cancel", then we do nothing.
                            }
                        }
                )
                .create();
        dialog.show();
    }

    // Method to alternate between the probe's main capture mode and the probe's stopped mode, updating the Button's Text as Needed
    // Seems now to work interchangeably with Spinner (still with caveat of no repeated spinner operations allowed in succession)
    public void toggleAeroscopeFrameStream (View userPress) {

        if (selectedScope.isRunning.get()) {
            //If the user selects STOP:
            Log.d( LOG_TAG, "User pressed start/stop button (STOP)");
            selectedScope.sendCommand( STOP_MODE );                // send the STOP command
            dataNotifSub.unsubscribe();                            // taking this out causes crazy traces
            selectedScope.isRunning.set( false );
            startStopButton.setText("Run");
            //  after STOP, wait 1 second then CANCEL
            if( cancelHandler.postDelayed( () -> selectedScope.sendCommand( CANCEL_FRAME ), STOP_CANCEL_DELAY_MS ) ) {  // true on successful enqueue
                Log.d( LOG_TAG, "Stop/start button scheduled CANCEL in " + STOP_CANCEL_DELAY_MS + " ms" );
            } else {
                Log.d( LOG_TAG, "Stop/start button error scheduling CANCEL" );
            }
        } else {
            //If the user selects RUN:
            startStopButton.setText("..."); //transitory phase
            Log.d( LOG_TAG, "User pressed start/stop button (RUN)");
            if( selectedScope.powerState != PowerState.DEVICE_POWER_FULL ) {  // NEW
                selectedScope.sendCommand( POWER_FULL );
                SystemClock.sleep( POWER_ON_DELAY_MS );  // wait for FPGA init; try 1500 ms NEW
                selectedScope.asTimeBase.setTimeBaseByIndex( selectedTimebaseIndex );
                selectedScope.asChannel.setVertSensByIndex( selectedSensitivityIndex );
                screenFragment.updateChart( selectedSensitivityIndex, selectedTimebaseIndex );
            }
            if( dataNotifSub == null || dataNotifSub.isUnsubscribed() ) {
                setUpDataNotification( );  // sets dataFrameSynced false, establishes dataNotifSub
            }
            selectedScope.sendCommand( RUN_MODE );     // send the RUN command
            selectedScope.isRunning.set( true );
            startStopButton.setText("Stop");
        }

    }

} //End of AeroscopeDisplay