package io.aeroscope.aeroscope;

import android.util.Log;

import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.internal.RxBleLog;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.observers.TestSubscriber;
import rx.schedulers.Timestamped;
import rx.subjects.PublishSubject;

import static io.aeroscope.aeroscope.AeroscopeConstants.AVAILABLE_VOLTS_PER_DIV;
import static io.aeroscope.aeroscope.AeroscopeConstants.BUTTON;
import static io.aeroscope.aeroscope.AeroscopeConstants.CALIBRATION;
import static io.aeroscope.aeroscope.AeroscopeConstants.CMD_TO_AEROSCOPE;
import static io.aeroscope.aeroscope.AeroscopeConstants.CONNECTION_RETRIES;
import static io.aeroscope.aeroscope.AeroscopeConstants.CRITICAL;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.DAC_CTRL_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_DC_COUPLED;
import static io.aeroscope.aeroscope.AeroscopeConstants.DEFAULT_FPGA_REGISTERS;
import static io.aeroscope.aeroscope.AeroscopeConstants.DOWN;
import static io.aeroscope.aeroscope.AeroscopeConstants.ERROR;
import static io.aeroscope.aeroscope.AeroscopeConstants.FRAME_SIZE;
import static io.aeroscope.aeroscope.AeroscopeConstants.FRONT_END_CTRL;
import static io.aeroscope.aeroscope.AeroscopeConstants.FULL;
import static io.aeroscope.aeroscope.AeroscopeConstants.IO_HISTORY_LENGTH;
import static io.aeroscope.aeroscope.AeroscopeConstants.MSG_FROM_AEROSCOPE;
import static io.aeroscope.aeroscope.AeroscopeConstants.NO_AUTO_CONNECT;
import static io.aeroscope.aeroscope.AeroscopeConstants.OFF;
import static io.aeroscope.aeroscope.AeroscopeConstants.POWER;
import static io.aeroscope.aeroscope.AeroscopeConstants.PowerState;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_SAMPLE_DEPTH;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_START_ADDRS_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.READ_START_ADDRS_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.TELEMETRY;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_CTRL;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_PT;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_HI;
import static io.aeroscope.aeroscope.AeroscopeConstants.TRIGGER_XPOS_LO;
import static io.aeroscope.aeroscope.AeroscopeConstants.VERSION;
import static io.aeroscope.aeroscope.AeroscopeConstants.WRITE_SAMPLE_DEPTH;
import static io.aeroscope.aeroscope.DataOps.scaledVolts;


/**
 * Created on 2017-02-03.
 * Started removing dead code 2017-04-02
 */

/*
Dictionary
    Constants
        LOG_TAG ("AeroscopeDevice        ")
    References
        asBleServiceRef (to AeroscopeBluetoothService)
        
    Methods
        void initializeFrame()           resets packet timestamps, sets incoming = false
        String toString()                combines Name and MAC Address (primarily for Scan result display)
        static void setServiceRef()      called by AeroscopeBluetoothService to pass a reference to its instance (asBleServiceRef)
        Subscription connectBle()        connects to device with connSubscriber
        RxBleConnection.RxBleConnectionState getConnectionState() returns current Connection State
        boolean isConnected()            checks device's connection state, returns true only if CONNECTED
        Observable<RxBleConnection> getConnectionObservable() returns Observable of existing connection, if any, or makes a new one
        Observable<RxBleConnection> bleConnectObs (in Dev) uses .share() to wait for Subscriber(s), and unsubscribe when last one unsubscribes
        Observable<DataFrame> frameObservable (in Dev--probably be abandoned) attempts to convert frame Queue into Observable
        Observable<DataFrame> packets2Frames (in Dev--probably best approach) transforms packet Observable into frame Observable
        boolean writeCommand( byte[] commandBytes ) true if able to start a Write to Aeroscope Input Characteristic, else false
        boolean writeFpgaRegister( int address, int data ) calls writeCommand to write a byte into an FPGA register
        INPUT OPERATIONS TO AEROSCOPE
        void writeCommand(byte[])        queues a message to the Aeroscope's Input Characteristic, returns inputSubscription
        void writeFpgaRegister(int address, int data) calls writeCommand() to set a single register in FPGA
        void writeState(byte[])          queues a 20-byte write to the State Characteristic (FPGA register block)
        OUTPUT OPERATIONS FROM AEROSCOPE
        void subscribeToOutput()         sets up subscriber that adds received messages to outputQueue. Do we need?
        void enableOutputNotification()  subscribes to Output notifications, timestamps packet, adds to outNotifQueue
        SAMPLE DATA OPERATIONS FROM AEROSCOPE
        void enableDataNotification()    subscribes to Data notifications via dataSubscriber, which calls handleDataPacket (MAY CHANGE)
        void disableDataNotification()   unsubscribes
        boolean dataNotificationIsEnabled() yes or no
        Observable<DataFrame> getFrameObservable() NEW: returns an Observable that emits DataFrames from the scope
        HELPER METHODS ETC.
        void initializeFrame()           clears running timestamps, sets incoming = false
        void handleDataPacket(Timestamped<byte[]> called by onNext() notifications from Data subscriber
        void processOutput()             handle Output Characteristic message queue from Aeroscope
        void processFrame()              handle display of a frame of data (Jack)
        int getBatteryCondition()        convert battery condition to 1 of 4 "pseudo-enum" conditions (Jack)
        
        
        
       
        void enableDataNotification()       Sets up notifications for Data packets; handleDataPacket() called for each

*/

public class AeroscopeDevice implements Serializable {
    
    private final static String LOG_TAG = "AeroscopeDevice        "; // tag for logging (23 chars max)
    
    // got warning not to use a static member, so took it out
    // however it seems this isn't used for anything
    //private AeroscopeBluetoothService asBleServiceRef; // ref to the Service class instance, passed by constructor (TODO: or not)
    
    // instance variables
    RxBleDevice bleDevice;                          // the Bluetooth hardware
    volatile RxBleConnection bleConnection;         // when this becomes non-null, we have connection (?)
    private volatile RxBleConnection.RxBleConnectionState connectionState; // or when this becomes CONNECTED (better?) (updated by Conn State Subs)
    
    Observable<RxBleConnection> sharedConnectionObservable;

    // Subscription variables for connection, connection state, data, I/O, state
    private AsConnSubscriber connSubscriber;        // connection Subscriber for BLE connection to Aeroscope (not dead yet)
    
    DataOps asDataOps;                      // reference to the DataOps class (unused, apparently, except with static access TODO: eliminate)
    AsChannel asChannel;                    // and the Channel implementation
    AsTimeBase asTimeBase;                  // and TimeBase
    AsTrigger asTrigger;                    // and Trigger
    AsScreen asScreen;                      // and Screen
    
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // New (maybe): vector of commands sent/responses received (for test etc.)
    // highest index is most recent message
    // Called by AsInputSubscriber's onNext() with true (=command)
    //        by
    static class ControlMessage {
        Timestamped<byte[]> message;
        boolean sentToAeroscope;  // true if a command to scope, false if a response from it
        ControlMessage( Timestamped<byte[]> content, boolean trueIfCommand ) {
            message = content;
            sentToAeroscope = trueIfCommand;
        }
    }
    static Vector<ControlMessage> ioHistory = new Vector<>( IO_HISTORY_LENGTH );  // holds last several messages sent to & received from Aeroscope
    synchronized static void updateIoHistory( Timestamped<byte[]> content, boolean trueIfCommand ) {
        ioHistory.add( new ControlMessage( content, trueIfCommand ) );            // add latest to end of Vector
        while( ioHistory.size() > IO_HISTORY_LENGTH ) ioHistory.remove( 0 );      // trim the Vector if needed, discarding oldest
        Log.d( LOG_TAG, "Updated I/O history with " + (trueIfCommand? "command to" : "response from") + " Aeroscope" );
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    AtomicBoolean writeCommandInProgress; // currently sending/executing a command on Aeroscope Input Characteristic TODO: need?
    
    // Hardware Parameters
    
    // Sample Buffer parameters: keep ints for these for convenience in doing arithmetic on them
    int     readSampleDepth,   // # samples in a Single Frame (default 512, may be 16, 256, or 4096) [256 may not be supported]
            writeSampleDepth,  // # samples in a Full Frame (default 4096, may be 16, 256, or 512)   [256 may not be supported]
            triggerLoc,        // buffer address at which trigger occurred (lower addresses are pre-trigger, higher are post-trigger); default 2048 = 0x800
            readStartAddress;  // starting address of a Single Frame (Full Frames always start at 0); default 2048 - 256 = 0x700
    
    boolean dcCoupled;           // bit 7 of Front End Control; true if input is DC coupled (default). Initialized to DEFAULT, set by setDcCoupled()
    boolean rejectTriggerNoise;  // bit 5 of Trigger Control
    
    volatile short batteryVoltage, temperature, acceleration; // added volatile
    byte hardwareId, fpgaRev, firmwareRev;
    int serialNo;
    
    //short cal1, cal2, cal3, cal4;  // Not used

    volatile String debugMsg;
    volatile boolean buttonDown;
    short buttonDownTime;      // -1 if unknown?
    long lastOutputTimestamp;  // ms value from last Output message timestamp
    
    byte[] fpgaRegisterBlock;  // the first 20 8-bit registers (in-memory copy)
    
    volatile int batteryState;          // CRITICAL, LOW, MEDIUM, or FULL (0, 1, 2, 3)
    volatile boolean chargerConnected;  // bit 15 of battery register
    volatile boolean chargingNow;       // bit 14
    
    // TODO: replace these with Channel values
    // Calibration Parameters: 16-bit unsigned values to be added to the DAC offset value (clipping to avoid overflows)
    //short[] calParam = new short[7];    // indexed by same index as other Vertical stuff: 0->100mV; 6->10V (initialized to 0)
    //short[] dacOffset = new short[7];   // same index, 16-bit unsigned current DAC offset value (initially midpoint of 32K)
    
    volatile byte criticalError;
    byte[] errorLog = new byte[19];
    
    volatile PowerState powerState = PowerState.DEVICE_POWER_UNK;
    AtomicBoolean isRunning;  // to monitor Run/Stop mode
    volatile boolean rollMode;  // 500 ms/div and slower
    volatile int bleSignalStrength;     // RSSI (unboxed from Integer, presumably)
    
    PublishSubject<Long> buttonDownSubject;  //  subscribers get Long emissions with timestamps of subsequent Button Down events

/*
    In the MainActivity, the user presses the Scan button
    This calls asBleServiceRef.scanForAeroscopes(), which scans for up to N Aeroscopes or T seconds
    Discovered Aeroscopes are put in a Vector<RxBleDevice>
    They are used to construct new Aeroscope objects, which go in a separate Vector<AeroscopeDevice>
    AeroscopeDevice constructor has a CONNECT_ON_DISCOVERY boolean, which we will make false for now
    Aeroscope constructor:
        stores static reference to AeroscopeBluetoothService instance in asBleServiceRef
        sets instance variables
            bleDevice is the underlying Bluetooth device object
            initializes a new AsConnSubscriber connSubscriber to handle connection
            initializes connSubscription to null (since CONNECT_ON_DISCOVERY is false)
            initializes a new AsDataSubscriber dataSubscriber
            leaves Subscription dataSubscription null
            initializes a new AsOutputSubscriber outputSubscriber
            sets asStateChangeSubscription and a new AsStateChangeSubscriber to keep connectionState updated
*/
    
    
    // constructor (may eventually want to pass in a data Object of Aeroscope initialization parameters)
    // TODO: see if we can eliminate the service parameter (replaced by Service's onCreate calling our setServiceRef)
    public AeroscopeDevice ( RxBleDevice device, AeroscopeBluetoothService service ) {
        Log.d( LOG_TAG, "Entering constructor with device " + device.getName() );
        bleDevice = device;
        connSubscriber = new AsConnSubscriber( );
    
        fpgaRegisterBlock = Arrays.copyOf( DEFAULT_FPGA_REGISTERS, 20 );  // load the defaults into the in-memory copy

        // initialize buffer parameters
        readSampleDepth = FRAME_SIZE[fpgaRegisterBlock[READ_SAMPLE_DEPTH]];    // code is index into size array
        writeSampleDepth = FRAME_SIZE[fpgaRegisterBlock[WRITE_SAMPLE_DEPTH]];  // code is index into size array
        triggerLoc = (fpgaRegisterBlock[TRIGGER_XPOS_LO] & 0xFF) + 256 * (fpgaRegisterBlock[TRIGGER_XPOS_HI] & 0xFF);
        readStartAddress = (fpgaRegisterBlock[READ_START_ADDRS_LO] & 0xFF) + 256 * (fpgaRegisterBlock[READ_START_ADDRS_HI] & 0xFF);
        
        dcCoupled = DEFAULT_DC_COUPLED;
        

/* Subscription to connection state changes handled in AeroscopeDisplay */
        
        writeCommandInProgress = new AtomicBoolean( false );  // TODO: need?
        
        asChannel = new AsChannel();     // instances of the subsystem classes
        asTimeBase = new AsTimeBase();
        asTrigger = new AsTrigger();
        asScreen = new AsScreen();  //
        
        // send reference to this class to supporting modules
        asTrigger.setAsDeviceRef( this );
        asChannel.setAsDeviceRef( this );
        asTimeBase.setAsDeviceRef( this );
        asScreen.setAsDeviceRef( this );
    
        buttonDownSubject = PublishSubject.create();     // to send button presses as an Observable
        isRunning = new AtomicBoolean( false );          // not running at first
        rollMode = false;                                // start up not in Roll Mode
    
        Log.d( LOG_TAG, "Finished constructor with device " + device.getName() );
    }
    
    
    @Override // for display in ArrayAdapter view
    public String toString() { return bleDevice.getName(); }
    
    // Connect to this device via BLE
    // Unsubscribe the returned Subscription to disconnect; NOTE with shared connection, disconnects when LAST subscriber hops off
    Subscription connectBle() {  // TODO: try it with AUTO_CONNECT?
        sharedConnectionObservable = this.bleDevice  // an Observable<RxBleConnection>, declared above
                .establishConnection( NO_AUTO_CONNECT )
                .retry( CONNECTION_RETRIES )
                .compose( new ConnectionSharingAdapter() );
        return sharedConnectionObservable.subscribe( connSubscriber );
    }
    
    // Method to return a shared Observable<connection> (allowing multiple subscribers)
    // In normal (non-shared) operation, unsubscribing tears down the connection
    // With multiple subscribers it's not released until the LAST subscriber unsubscribes
    // Also errors are propagated to all subscribers
    // NOTE if this is called before connectBle() will no doubt cause a NPE
    Observable<RxBleConnection> getConnectionObservable() {
        return sharedConnectionObservable;
    }
    
    

/*------------------------------------------ NEW STUFF -------------------------------------------*/
    
    
    // Method to send a command to Aeroscope Input characteristic
    // Copied from AeroscopeDisplay, changing "selectedScope" to "this"
    void sendCommand( byte[] command ) {  // command must be 20 bytes
        if( command.length != 20 ) {      // haven't seen any failures here
            Log.d( LOG_TAG, "ERROR: sendCommand() aborted: arg length = " + command.length + "; arg = " + HexString.bytesToHex( command ) );
        } else {  // length OK
            Log.d( LOG_TAG, "sendCommand() sending to Aeroscope Input: " + HexString.bytesToHex( command ) );
            this.getConnectionObservable( )
                    .flatMap( rxBleConnection -> rxBleConnection.writeCharacteristic( AeroscopeConstants.asInputCharId, command ) )
                    .subscribe( this::onWriteInputSuccess, this::onWriteInputError );  // just log the results
        }
    }  // sendCommand()
    void onWriteInputSuccess( byte[] bytesWritten ) { // handler for write Input characteristic success
        Log.d( LOG_TAG, "Message written to Aeroscope Input: " + HexString.bytesToHex( bytesWritten ) );
    }
    void onWriteInputError( Throwable writeError ) { // handler for write Input characteristic error
        Log.d( LOG_TAG, "Write error in Input message to Aeroscope: " + writeError.getMessage( ) );
    }
    

    
    
    // Method to write the full 20 FPGA registers to the Aeroscope State characteristic
    // copies the in-memory version of the register block to the device
    void copyFpgaRegisterBlockToAeroscope() {
        this.getConnectionObservable()
                .flatMap( rxBleConnection -> rxBleConnection.writeCharacteristic( AeroscopeConstants.asStateCharId, fpgaRegisterBlock ) )
                .observeOn( AndroidSchedulers.mainThread( ) )
                .subscribe( this::onWriteStateSuccess, this::onWriteStateError );  // just log the results
    }
    void onWriteStateSuccess( byte[] bytesWritten ) { // handler for write State characteristic success
        Log.d( LOG_TAG, "Message written to Aeroscope State: " + HexString.bytesToHex( bytesWritten ) );
    }
    void onWriteStateError( Throwable writeError ) { // handler for write State characteristic error
        Log.d( LOG_TAG, "Write error in State message to Aeroscope: " + writeError.getMessage( ) );
    }



    // Read BLE signal strength
    void readBleSignalStrength() {  // seems to work very reliably
        this.getConnectionObservable()
                .flatMap( rxBleConnection -> rxBleConnection.readRssi() )  // a single-value Observable that does complete
                .observeOn( AndroidSchedulers.mainThread() )
                .subscribe( integer -> bleSignalStrength = integer, // );  // made variable volatile  onError() not implemented error
                        throwable -> Log.d( LOG_TAG, "Error on readBleSignalStrength(): " + throwable.getMessage() )  // NEW
                );
    }
    
    
    int getOffsetDAC( ) {  // note it's a 16-bit unsigned value  TODO: unused, eliminate(?) (used for testing?)
        return (fpgaRegisterBlock[DAC_CTRL_LO] & 0xff) + 256 * (fpgaRegisterBlock[DAC_CTRL_HI] & 0xff);
    }

    
    // convert the hardware trigger level (0..255) to a voltage at the current scale
    float getScaledTriggerLevel( float vMin, float vMax ) {
        return scaledVolts( fpgaRegisterBlock[TRIGGER_PT] & 0xff, vMin, vMax );
    }
    
    // update the Read Sample Depth and return new value, else -1
    int setReadSampleDepth( int lengthCode ) {  // frame sizes are in FRAME_SIZE array, indexed by lengthCode
        if( FRAME_SIZE[lengthCode] > 0 ) {    // TODO:? potential array out of bounds
            readSampleDepth = FRAME_SIZE[lengthCode];
            fpgaRegisterBlock[READ_SAMPLE_DEPTH] = (byte) lengthCode;
            copyFpgaRegisterBlockToAeroscope();  // update the value
            Log.d( LOG_TAG, "Read Sample Depth set to " + readSampleDepth );
            return readSampleDepth;
        } else {
            Log.d( LOG_TAG, "Invalid argument to setReadSampleDepth(): " + lengthCode );
            return -1;  // bogus value
        }
    }
    
    // update the Write Sample Depth and return new value, else -1 -- return the new depth
    int setWriteSampleDepth( int lengthCode ) {  // frame sizes are in FRAME_SIZE array, indexed by lengthCode
        if( FRAME_SIZE[lengthCode] > 0 ) {     // TODO:? potential array out of bounds
            writeSampleDepth = FRAME_SIZE[lengthCode];
            fpgaRegisterBlock[WRITE_SAMPLE_DEPTH] = (byte) lengthCode;
            copyFpgaRegisterBlockToAeroscope();  // update the value
            Log.d( LOG_TAG, "Write Sample Depth set to " + writeSampleDepth );
            return writeSampleDepth;
        } else {
            Log.d( LOG_TAG, "Invalid argument to setWriteSampleDepth(): " + lengthCode );
            return -1;  // bogus value
        }
    }
    
    // set new Trigger Location (must be within Write Sample Depth) -- true on success
    boolean setTriggerLoc( int trigLoc ) {
        if( trigLoc >= 0 && trigLoc < writeSampleDepth ) {
            triggerLoc = trigLoc;
            fpgaRegisterBlock[TRIGGER_XPOS_LO] = (byte) (trigLoc & 0xFF);
            fpgaRegisterBlock[TRIGGER_XPOS_HI] = (byte) ((trigLoc >>> 8) & 0x0F);  // 4 low-order bits
            copyFpgaRegisterBlockToAeroscope();
            Log.d( LOG_TAG, "Trigger Location set to: " + trigLoc );
            return true;
        } else {  // bad argument
            Log.d( LOG_TAG, "Invalid argument to setTriggerLoc(): " + trigLoc );
            return false;
        }
    }
    
    // set starting address for Single Frame (must fit in Write buffer) -- return true if successful
    boolean setReadStartAddress( int readStart ) {
        int readEnd = readStart + readSampleDepth - 1;  // ending address of frame to read
        if( readStart >= 0 && readEnd < writeSampleDepth ) {  // will requested frame fit in written full frame?
            readStartAddress = readStart;
            fpgaRegisterBlock[READ_START_ADDRS_LO] = (byte) (readStart & 0xFF);
            fpgaRegisterBlock[READ_START_ADDRS_HI] = (byte) ((readStart >>> 8) & 0x0F);  // 4 low-order bits
            copyFpgaRegisterBlockToAeroscope();
            Log.d( LOG_TAG, "Read start address set to: " + readStart );
            return true;
        } else {
            Log.d( LOG_TAG, "Invalid argument to setReadStartAddress(): " + readStart );
            return false;
        }
    }

    // bit 7 of front-end control register set means DC-coupled
    void setDcCoupled( boolean isDcCoupled ) {
        fpgaRegisterBlock[FRONT_END_CTRL] = isDcCoupled? (byte)(fpgaRegisterBlock[FRONT_END_CTRL] | 0x80)  // set bit 7
                : (byte)(fpgaRegisterBlock[FRONT_END_CTRL] & 0x7F);  // clear bit 7
        copyFpgaRegisterBlockToAeroscope();
        dcCoupled = isDcCoupled;
        Log.d( LOG_TAG, "DC coupled set to: " + isDcCoupled );
    }
    
    // bit 5 of Trigger Control register: Trigger Noise Rejection
    void setRejectTriggerNoise( boolean rejectIt ) {
        fpgaRegisterBlock[TRIGGER_CTRL] = rejectIt? (byte)(fpgaRegisterBlock[TRIGGER_CTRL] | 0x20)  // set bit 5
                : (byte)(fpgaRegisterBlock[TRIGGER_CTRL] & 0xDF);  // clear bit 5
        copyFpgaRegisterBlockToAeroscope();
        rejectTriggerNoise = rejectIt;
        Log.d( LOG_TAG, "Trigger Noise Rejection set to: " + rejectIt );
    }


/*------------------------------------/SAMPLE DATA OPERATIONS-------------------------------------*/
    

    // special Subscriber for connection to device
    // Note that connection is broken when Observable is unsubscribed, and vice versa
    class AsConnSubscriber extends TestSubscriber<RxBleConnection> { // TODO: change to normal Subscriber when done
        public AsConnSubscriber() { // constructor
            super();
            Log.d( LOG_TAG, "Finished AsConnSubscriber constructor" );
        }
        @Override
        public void onNext( RxBleConnection connection ) { // called with emission from Observable<RxBleConnection>
            bleConnection = connection; // volatile
            Log.d( LOG_TAG, "AsConnSubscriber made connection " + connection.toString() );
            // TODO maybe a Toast? some assertions?
        }
        @Override  // Polidea says this is NEVER called because connection is open until unsubscribed or error
        public void onCompleted( ) {
            Log.d( LOG_TAG, "AsConnSubscriber called onCompleted() " );
        }
        @Override
        public void onError( Throwable error ) { // called with notification from Observable<RxBleConnection>
            Log.d( LOG_TAG, "AsConnSubscriber error connecting: " + error.getMessage() );
            bleConnection = null;       // (volatile) NEW
            // presumably may generate BleDisconnectedException (expected) or BleGattException (error)
            // maybe a Toast?
        }
    }

    
    
    
    // special Subscriber subclass that receives connection state changes
    // added a string for device name
    class AsStateChangeSubscriber extends TestSubscriber<RxBleConnection.RxBleConnectionState> {
        String deviceName;
        public AsStateChangeSubscriber( String devName ) {
            super();
            deviceName = devName;
            Log.d( LOG_TAG, "Finished AsStateChangeSubscriber constructor for device " + deviceName );
        }
        @Override // from Class Subscriber: invoked when the Subscriber and Observable have been connected
        // but the Observable has not yet begun to emit items
        public void onStart( ) {
            Log.d( LOG_TAG, "AsStateChangeSubscriber onStart() called for device " + deviceName );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onNext( RxBleConnection.RxBleConnectionState connState ) {
            connectionState = connState;
            if( connectionState != RxBleConnection.RxBleConnectionState.CONNECTED ) bleConnection = null;
            Log.d( LOG_TAG, "AsStateChangeSubscriber: new state of " + deviceName + " is " + connState.toString() );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onCompleted() { // not sure this would ever complete
            // unsubscribe here? Need to re-subscribe in onResume? ****** TODO
            // asStateChangeSubscription.unsubscribe();
            this.unsubscribe();
            Log.d( LOG_TAG, "AsStateChangeSubscriber: onCompleted(); " + deviceName + " unsubscribed" );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onError( Throwable e ) { // just punt on errors for now
            RxBleLog.d( "Error in Connection State Change from Aeroscope: " + e.getMessage(), (Object) null );
            Log.d( LOG_TAG, "AsStateChangeSubscriber: onError(); " + deviceName + " error: " + e.getMessage() );
            // onError() can't throw unchecked exception:
            //throw new RuntimeException( "Error in Connection State feed from Aeroscope " + deviceName + ": " + e.getMessage(), e );
        }
    }
    
    
    // special Subscriber subclass that receives data from Aeroscope's Output Characteristic
    // the way this is set up, it appears that this is a single-item Observable
    // Change: the new type of emission is Timestamped<byte[]>
    // TODO: is this being used? If so, should it be?
    class AsOutputSubscriber extends TestSubscriber<Timestamped<byte[]>> {
        String deviceName;
        public AsOutputSubscriber( String devName ) {
            super();
            deviceName = devName;
            Log.d( LOG_TAG, "Finished AsOutputSubscriber constructor for " + deviceName );
        }
        @Override // from Class Subscriber: invoked when the Subscriber and Observable have been connected
        // but the Observable has not yet begun to emit items
        public void onStart( ) {
            // super.onStart();  // apparently not necessary to call super in a Subscriber
            Log.d( LOG_TAG, "AsOutputSubscriber onStart() called" );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onNext( Timestamped<byte[]> outputMessage ) {
            //outputQueue.offer( outputMessage );
            updateIoHistory( outputMessage, MSG_FROM_AEROSCOPE );  // add this command to the history Vector
            Log.d( LOG_TAG, "Message from Aeroscope added to Output Queue: " + outputMessage.toString() );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onCompleted() { // will we ever get this? I think after 1 emission, since it's readCharacteristic
            // unsubscribe here? Need to re-subscribe in onResume? TODO
            //dead2 outputSubscription.unsubscribe(); // TODO: correct? (may be redundant)
        }
        @Override // from the Observer interface that Subscriber implements
        public void onError( Throwable e ) { // just punt on errors for now
            RxBleLog.d( "AsOutputSubscriber: error in output subscription from Aeroscope: " + e.getMessage(), (Object) null );
            // onError() can't throw unchecked exception:
            //throw new RuntimeException( "Error in output subscription from Aeroscope " + deviceName + ": " + e.getMessage(), e );
        }
    }
    
    
    
    // special Subscriber subclass that sends data to Aeroscope's Input Characteristic
    // Change: the new type of emission is Timestamped<byte[]>
    // TODO: is this used anywhere?
    class AsInputSubscriber extends TestSubscriber<Timestamped<byte[]>> { // changed from <byte[]>
        String deviceName;
        public AsInputSubscriber( String devName ) {
            super();
            deviceName = devName;
            Log.d( LOG_TAG, "Finished AsInputSubscriber constructor for " + deviceName );
        }
        @Override // from Class Subscriber: invoked when the Subscriber and Observable have been connected
        // but the Observable has not yet begun to emit items. Override to add useful initialization
        public void onStart( ) {
            Log.d( LOG_TAG, "AsInputSubscriber onStart() called for device: " + deviceName );
        }
        @Override // from the Observer interface that Subscriber implements
        public void onNext( Timestamped<byte[]> inputMessage ) {  // just internally plays back the command sent to Aeroscope
            updateIoHistory( inputMessage, CMD_TO_AEROSCOPE );    // add this command to the history Vector
            Log.d( LOG_TAG, "onNext() invoked by AsInputSubscriber for device: " + deviceName
                    + " with data: " + inputMessage.toString() ); //TODO: instead of toString(), something that prints the contents
        }
        @Override // from the Observer interface that Subscriber implements
        public void onCompleted() { // will we ever get this? Yes?
            // unsubscribe here? Need to re-subscribe in onResume? ******
            Log.d( LOG_TAG, "AsInputSubscriber onCompleted() called for device: " + deviceName
                    + "; unsubscribing now");
            //dead2 inputSubscription.unsubscribe(); // TODO: correct?
        }
        @Override // from the Observer interface that Subscriber implements
        public void onError( Throwable e ) { // just punt on errors for now
            RxBleLog.d( "Error in input subscription to Aeroscope: " + e.getMessage(), (Object) null );
            // onError() can't throw unchecked exception:
            //throw new RuntimeException( "Error in input subscription to Aeroscope: " + e.getMessage(), e );
        }
    }
    
    
    //HELPER METHODS ETC.
    
    // Convert battery voltage to one of the 4 conditions (see AeroscopeConstants)
    int getBatteryCondition() {
        //TODO: implement
        chargerConnected = (batteryVoltage & 0x8000) != 0;  // all variables volatile
        chargingNow = (batteryVoltage & 0x4000) != 0;
        return batteryVoltage & 0x00FF; // for now, the raw voltage TODO: later: the battery condition pseudo-enum? Graphic?
    }
    
    
    // Got a message (or notification) from Aeroscope Output characteristic
    // only called from AeroscopeDisplay.onOutputMessageReceived()
    void handleOutputFromAeroscope( Timestamped<byte[]> outputMessage ) {
        
        lastOutputTimestamp = outputMessage.getTimestampMillis();
        ByteBuffer dataBuf = ByteBuffer.wrap( outputMessage.getValue() ); // default byte order is Big-Endian
        byte preamble1, preamble2;
        
        preamble1 = dataBuf.get();  // first byte of message
        switch ( preamble1 ) {
            case TELEMETRY:  // "T" next bytes are shorts
                batteryVoltage = dataBuf.getShort();  // also includes Charger/Charging status in top 2 bits
                temperature = dataBuf.getShort();
                //acceleration = dataBuf.getShort();  // gone
                break;
            case VERSION: // "V" followed by HW ID, FPGA Rev, Firmware Rev, int S/N
                hardwareId = dataBuf.get();
                fpgaRev = dataBuf.get();
                firmwareRev = dataBuf.get();
                serialNo = dataBuf.getInt();
                break;
            case ERROR:  // "E"
                preamble2 = dataBuf.get(); // should be first error code, or 'C' for critical
                switch( preamble2 ) {
                    case CRITICAL:  // "C"
                        criticalError = dataBuf.get();
                        break;
                    default:
                        errorLog[0] = preamble2;
                        for( int i=1; i<19; i++ ) errorLog[i] = dataBuf.get();
                        break;
                } // inner switch on ERROR byte 2
                break;
            
            case CALIBRATION:  // "C"  NOTE: protocol changed in "1.2" spec version; sent after a Calibrate op or a 'QC' command
                // now we're told calibration values are 16-bit signed, not unsigned
                int versionPadding = dataBuf.getInt();  // eat 4 NULLs, presumably placeholders for the fancier Aeroscope's 2 more sensitive ranges
                for( int vIndex = 0; vIndex < AVAILABLE_VOLTS_PER_DIV.length; vIndex++ ) {
                    asChannel.calibrationValue[vIndex] = dataBuf.getShort();  // cast short to int; WANT sign extension!
                }
                // output looks OK (though delayed if scope is running)
                Log.d( LOG_TAG, "Calibration parameters received from Aeroscope: " + Arrays.toString( asChannel.calibrationValue ) );
                break;
            
            case BUTTON:  // "B"
                preamble2 = dataBuf.get();  // followed by D, DT and time H/L, or UT and time H/L (latter 2 are unimplemented, we think)
                switch( preamble2 ) {  // next char must be D, else error
                    case DOWN: // "D" for now is end of message
                        onScopeButtonDown( System.currentTimeMillis() );  // do something
                        break;
                    default: // second byte of BUTTON message was not DOWN; error
                        Log.d( LOG_TAG, "Bad Button message format: B" + (char) preamble2 );
                        break;
                } // switch on preamble2
                break;  // done with case BUTTON
            
            case POWER:  // "P"
                preamble2 = dataBuf.get(); // should be 'F' (FULL) or 'O' (OFF)
                switch( preamble2 ) {
                    case FULL:
                        powerState = PowerState.DEVICE_POWER_FULL;
                        break;
                    case OFF:
                        powerState = PowerState.DEVICE_POWER_OFF;
                        break;
                    default:
                        Log.d( LOG_TAG, "Bad Power State message format: P" + (char) preamble2 );
                        break;
                }  // switch on preamble2
                break;  // done with case POWER
            
            default: // don't recognize the command
                Log.d( LOG_TAG, "Unrecognized message received from Aeroscope Output Characteristic: " + (char) preamble1 );
        } // switch on preamble1
    } // handleOutputFromAeroscope
    
    // listener for Button Down events: emits them to buttonDownSubject subscribers
    void onScopeButtonDown( long timeStampMs ) {
        Log.d( LOG_TAG, "onScopeButtonDown time: " + timeStampMs );  // seems fine
        buttonDownSubject.onNext( Long.valueOf( timeStampMs ) );
    }
    
} // class AeroscopeDevice
