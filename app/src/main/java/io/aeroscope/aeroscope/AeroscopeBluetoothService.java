package io.aeroscope.aeroscope;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.RxBleLog;

import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Subscription;

import static io.aeroscope.aeroscope.AeroscopeConstants.MAX_AEROSCOPES;
import static io.aeroscope.aeroscope.AeroscopeConstants.SCAN_TIME;
import static io.aeroscope.aeroscope.AeroscopeConstants.asServiceIdArray;

/**
 * Created on 2017-02-01.
 */

/*
A place to put the Bluetooth-specific code for Aeroscope
Update 2017-02-25: moved all the constants into new class AeroscopeConstants and imported it

Stuff that lives here:
    Constants etc.
        Static
            LOG_TAG
    RxAndroidBle Variables
        Static
            asBleClient: set by onCreate()
            rxBleDeviceVector: populated by scanForAeroscopes
            asDeviceVector: populated by scanForAeroscopes
            nowScanning: AtomicBoolean
            errorScanning: Atomic Boolean
            scanSubscription: for device scan
            scanSubscriber:      "
    Note on absent Constructor
    
    Methods for Clients
        AeroscopeBluetoothService getService();      returns reference to instance of this class
        IBinder onBind(Intent intent);               returns asBleBinder to Clients
        void onRebind( Intent intent );              just logs
        boolean onUnbind(Intent intent);             returns true to have onRebind() called when clients bind
        static RxBleClient getRxBleClient();         returns asBleClient;
        static void setClient( RxBleClient client ); sets asBleClient = client
        void addRxBleScanResult( RxBleScanResult scanResult ); calls addRxBleDevice
        boolean addRxBleDevice( RxBleDevice device ); adds device to Vector if not present, calls addAeroscopeDevice
        boolean addAeroscopeDevice( AeroscopeDevice asDevice ); adds device to Vector if not present
        void scanForAeroscopes();                    clears both Vectors, starts scan, subscribes scanSubscriber, scanSubscription
        synchronized void stopScanning();            unsubscribes, clears nowScanning flag
        boolean deviceScanIsActive();                returns nowScanning flag
        boolean lastDeviceScanGotError();            returns errorScanning flag
        int getFoundDeviceCount();                   returns rxBleDeviceVector.size()
        int getFoundAeroscopeCount();                returns asDeviceVector.size() (redundant?)
        Vector<RxBleDevice> getFoundDeviceVector();  returns rxBleDeviceVector
        Vector<AeroscopeDevice> getFoundAeroscopeVector(); returns asDeviceVector
        Subscription connect( AeroscopeDevice device, Subscriber<RxBleConnection> connSubscriber, boolean autoConnect); connects
        static void disconnect( AeroscopeDevice asDevice); unsubscribes, sets connection to null
        
        static void handleScanError( Throwable scanError, Context context ); Toasts reasons for scan problems; called by AsScanSubscriber.onError() ******TEST
        static void setClient( RxBleClient client ); sets asBleClient = client
        
*/


// Must be declared public (and listed in Manifest) to be used as a Service
public class AeroscopeBluetoothService extends Service { // a Bound Service is an implementation of abstract Service class
    
    // Constants, Options
    private final static String LOG_TAG = "AeroscopeBluetoothServc"; // tag for logging (23 chars max)
    
    // RxAndroidBle static variables
    static RxBleClient asBleClient; // only one (set by this onCreate()); reference as AeroscopeBluetoothService.asBleClient
    static Vector<RxBleDevice> rxBleDeviceVector = new Vector<>();    // can hold multiple devices discovered during scan
    static Vector<AeroscopeDevice> asDeviceVector = new Vector<>();   // can hold multiple Aeroscopes created from the above
    static AtomicBoolean nowScanning   = new AtomicBoolean( false );  // indicates when device scan is active
    static AtomicBoolean errorScanning = new AtomicBoolean( false );  // indicates if last scan got an error
    static Subscription scanSubscription;     // for scanning for BLE devices
    
    // Ref to calling context for use in Toasts in handleScanError() ****** NEW TODO: verify OK
    Context callingContext;
    public void setCallingContext( Context context ) { // initialized by AeroscopeDisplay
        callingContext = context;
    }
    
    // I THINK onCreate takes the place of a constructor
    
    @Override
    public void onCreate( ) {
        super.onCreate( ); // apparently a Bound Service's onCreate() isn't passed Bundle savedInstanceState
        // Try doing this here and not in App onCreate() Seems to work
        asBleClient = RxBleClient.create( /*context*/ this ); // we're only supposed to have 1 instance of Client
        RxBleClient.setLogLevel( RxBleLog.DEBUG );
        //RxBleClient.setLogLevel( RxBleLog.WARN );  //  TODO: what's the best?
        Log.d( LOG_TAG, "onCreate() created client and set RxBleLog level.");
        RxBleLog.d( "RxBleLog: ABS onCreate(): Created client and set log level.", (Object)null ); // works; seems to arrive here without incident
        //TODO: following line probably not wanted with the Service binding, right? (for Activities, yes, but prob. not for AeroscopeDevice)
        //TODO: since the service ref wasn't actually used in AeroscopeDevice, take it out.
        //AeroscopeDevice.setServiceRef( this ); // pass (static) ref to Service instance to AeroscopeDevice class // TODO: does this work? Seems to
    }
    
    //TODO: do we need onPause(), onResume()?
    
    @Override
    public void onDestroy() {
        // Unsubscribe from all subscriptions to avoid memory leaks!
        // This is the only possibly active subscription in the Service:
        if( scanSubscription != null && !scanSubscription.isUnsubscribed() ) scanSubscription.unsubscribe(); // fix(?) NPE when trying to leave MainActivity before scanning
        super.onDestroy();  // moved to end
    }
    
    
    
/*----------------------------------- SERVICE BINDING STUFF --------------------------------------*/
    
    // Binder given to clients for service--returns this instance of the Service class
    private final IBinder asBleBinder = new LocalBinder();
    
    // Class for binding Service
    public class LocalBinder extends Binder {
        AeroscopeBluetoothService getService() {
            Log.d( LOG_TAG, "Entering asBleBinder.getService()" );  // seems to work
            // return this instance of AeroscopeBluetoothService so Clients can call public methods
            // (note you can call static methods with an instance identifier)
            return AeroscopeBluetoothService.this; // include class name; otherwise "this" == LocalBinder
        }
    }
    
    @Override // Service must implement this & return an IBinder
    public IBinder onBind( Intent intent ) {
        Log.d( LOG_TAG, "Entering onBind()" ); // seems to work
        // Here can fetch "Extra" info sent with the Intent, e.g. String macAddress = intent.getStringExtra( "MAC_Address_Extra" );
        return asBleBinder; // can call the returned instance with .getService
    }
    


/*----------------------------------- /SERVICE BINDING STUFF -------------------------------------*/

    
/*------------------------------------- BLUETOOTH UTILITIES --------------------------------------*/
    
    // get the client
    // this is how sample does it; necessary?
    //    public static RxBleClient getRxBleClient(Context context) {
    //        AeroscopeApp application = (AeroscopeApp) context.getApplicationContext();
    //        return application.rxBleClient;
    //    }
    public static RxBleClient getRxBleClient() {
        return asBleClient;
    } // TODO: make sure this works
    // note static because there is only one client even if we have multiple devices
    

    // Add an RxBleDevice to the Vector (returns true if it was not already present)
    // note may not really need this separate vector
    private boolean addRxBleDevice( RxBleDevice device ) {
        Log.d( LOG_TAG, "Entering addRxBleDevice with device " + device.getName() );
        if( !rxBleDeviceVector.contains( device ) ) {
            Log.d( LOG_TAG, "Adding RxBleDevice " + device.getName() + " to Vector" );
            addAeroscopeDevice( new AeroscopeDevice( device, this ) ); // add an AeroscopeDevice using this (constructor connects?)
            return rxBleDeviceVector.add( device ); // true
        } else {
            Log.d( LOG_TAG, "RxBleDevice " + device.getName() + " already in rxBleDeviceVector");
            return false;
        }
    }
    
    // Add an Aeroscope device to the Vector (returns true if it was not already present)
    // static because there's just 1 vector
    private boolean addAeroscopeDevice( AeroscopeDevice asDevice ) {
        Log.d( LOG_TAG, "Entering addAeroscopeDevice with device " + asDevice.bleDevice.getName() );
        if( !asDeviceVector.contains( asDevice ) ) {
            Log.d( LOG_TAG, "Adding AeroscopeDevice " + asDevice.bleDevice.getName( ) + " to Vector" );
            return asDeviceVector.add( asDevice ); // true
        } else { // don't think we should ever see this(?)
            Log.d( LOG_TAG, "AeroscopeDevice " + asDevice.bleDevice.getName() + " already in asDeviceVector");
            return false;
        }
    }

    // Scanning starts when you subscribe to client.scanBleDevices and stops ONLY on unsubscribe.
    // In the sample app, unsubscribe() is only called when the user toggles the Scan button and in the onPause() method
    // javadoc for take() says that take(n) emits the first n items then calls onCompleted()
    // Since scanBleDevices NEVER completes, what's the implication? Suspect that you should still .unsubscribe() if you're done scanning
    // in order to stop the scanning process. RxAndroidBle docs imply the same.
    public void scanForAeroscopes( int numberToFind, long secondsToScan ) {  // for this new branch
        Log.d( LOG_TAG, "Entered new scanForAeroscopes()" );
        rxBleDeviceVector.clear( );                          // recall that Vectors are thread-safe
        asDeviceVector.clear( );
        nowScanning.set( true );                             // set "scan active" flag (synchronized)
        errorScanning.set( false );                          // clear the "scan error" flag
        // since we added time and device count limits, we know this Observable will not run forever, but see above
        scanSubscription = asBleClient
                .scanBleDevices( asServiceIdArray )          // argument must be an array, apparently
                // In later versions we flipped the order of the next 2 lines
                
                //.take( numberToFind )                      // limit the # of Aeroscopes we detect     try this
                //.take( secondsToScan, TimeUnit.SECONDS )   // limit the length of the scan
                
                // Next line could have been source of scan crashes! Who knows why?
                // Found statement: "This new thread can start processing values before the previous thread is finished generating them."
                //.observeOn( AndroidSchedulers.mainThread() ) // probably need this at least if a Toast is generated
                .subscribe(
                        this::addRxBleScanResult,            // onNext() handler when a device is found--add it to vectors
                        this::handleRxBleScanError,          // onError() handler for a scan error
                        this::completeRxBleScan              // onCompleted() handler (ONLY called because take() worked!!)
                );
    }

    public void scanForAeroscopes( ) {  //  no-arg overload for using the parameter values in AeroscopeConstants
        scanForAeroscopes( MAX_AEROSCOPES, SCAN_TIME );  // note: int and long params
    }

    private void addRxBleScanResult( RxBleScanResult scanResult ) {
        // don't think we need to change the scanning and error flags
        RxBleDevice device = scanResult.getBleDevice();
        Log.d( LOG_TAG, "addRxBleScanResult about to call addRxBleDevice with device " + device.toString() );
        // TODO: add each device to the displayed "found" list when it's found(?)
        addRxBleDevice( device );
        
        completeRxBleScan();  // try this (have to fake a completion because I think the .take() was only thing causing scan to complete
    }

    private void handleRxBleScanError( Throwable scanError ) {
        nowScanning.set( false );        // reset "scan active" flag (synchronized) (error ends scan)
        errorScanning.set( true );       // set the "scan error" flag
        Log.d( LOG_TAG, "scanSubscription onError() received: " + scanError.getMessage(), scanError );
        //handleScanError( scanError, callingContext ); // TODO: test
    }
    
    private void completeRxBleScan() {   // since scanning is infinite until unsubscribed, this is only called when take() fires(?)
        Log.d( LOG_TAG , "completeRxBleScan was called by scanSubscription; unsubscribing..." );
        stopScanning();
        errorScanning.set( false );      // clear the "scan error" flag
        // Broadcast the results back to MainActivity
        Intent scanIntent = new Intent( "scanResults" );
        scanIntent.putExtra( "io.aeroscope.aeroscope.DeviceVector", asDeviceVector ); // name must include package; Vector implements Serializable
        LocalBroadcastManager.getInstance( getApplicationContext() ).sendBroadcast( scanIntent ); // not sure this Context will work (seems to)
        Log.d( LOG_TAG, "scanSubscription onCompleted() broadcast asDeviceVector to MainActivity" );
    }
    
    
    // method to stop a device scan; NOTE use this instead of a simple unsubscribe()
    public /*synchronized*/ void stopScanning() {
        if( scanSubscription != null ) scanSubscription.unsubscribe();  // fix(?) NPE when trying to leave MainActivity before scanning
        nowScanning.set( false );
        Log.d( LOG_TAG, "stopScanning() has run" );
    }

    // methods to test if scan of BLE devices is active or got an error
    public boolean deviceScanIsActive()     { return nowScanning.get(); }
    public boolean lastDeviceScanGotError() { return errorScanning.get(); }
    
    // method to see how many devices have been found (since last scan was started)
    public int getFoundDeviceCount() {
        return rxBleDeviceVector.size();
    }
    
    // method to see how many Aeroscopes have been found (since last scan was started)
    public int getFoundAeroscopeCount() {
        return asDeviceVector.size();
    }
    
    public Vector<RxBleDevice> getFoundDeviceVector() {
        return rxBleDeviceVector;
    }
    
    public Vector<AeroscopeDevice> getFoundAeroscopeVector() {
        return asDeviceVector;
    }
    
    
    
/*------------------------------------ /BLUETOOTH UTILITIES --------------------------------------*/
    
    
    
    // Handling an error in the initial scan for Bluetooth devices (e.g., Bluetooth not enabled) TODO: test (worked once, I think)
    // Called by the onError() handler of scanSubscription
    // ****** NEW now supplying callingContext instead of getApplicationContext() which wasn't working apparently
    // ****** Toast "handleScanError: Unknown scanning error" is appearing over MainActivity; ClassCastException is thrown 1ms before this
    //        method is being called, apparently.
    static void handleScanError( Throwable scanError, Context context ) {
        Log.d( LOG_TAG, "Entering handleScanError: " + scanError.getMessage() );
        Log.d( LOG_TAG, "Context passed to handleScanError is: " + context.getClass().getName() );  // debugging (pushed)
        if (scanError instanceof BleScanException ) {
            switch ( ((BleScanException) scanError).getReason() ) {
                case BleScanException.BLUETOOTH_NOT_AVAILABLE:
                    Log.d( LOG_TAG, "handleScanError: Bluetooth is not available" );
                    Toast.makeText( context, "Bluetooth is not available", Toast.LENGTH_SHORT ).show( );
                    break;
                case BleScanException.BLUETOOTH_DISABLED:
                    Log.d( LOG_TAG, "handleScanError: Bluetooth is disabled" );
                    Toast.makeText( context, "Enable bluetooth and try again", Toast.LENGTH_SHORT ).show( );
                    break;
                case BleScanException.LOCATION_PERMISSION_MISSING:
                    Log.d( LOG_TAG, "handleScanError: Location permission missing" );
                    Toast.makeText( context,
                            "On Android 6.0 location permission is required. Implement Runtime Permissions", Toast.LENGTH_SHORT ).show( );
                    break;
                case BleScanException.LOCATION_SERVICES_DISABLED:
                    Log.d( LOG_TAG, "handleScanError: Location services disabled" );
                    Toast.makeText( context, "Location services needs to be enabled on Android 6.0", Toast.LENGTH_SHORT ).show( );
                    break;
                case BleScanException.BLUETOOTH_CANNOT_START:
                default:
                    Log.d( LOG_TAG, "handleScanError: Bluetooth cannot start" );
                    Toast.makeText( context, "Unable to start scanning (reason unknown)", Toast.LENGTH_SHORT ).show( );
            } // switch
        } else { // exception was not a BleScanException
            Log.d( LOG_TAG, "handleScanError: Unknown scanning error" );
            Toast.makeText( context, "Error scanning for Aeroscopes: " + scanError.getMessage(), Toast.LENGTH_SHORT ).show( );
        }
        Log.d( LOG_TAG, "handleScanError Finished" );
    }
}
