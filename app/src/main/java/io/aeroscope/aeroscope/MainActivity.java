package io.aeroscope.aeroscope;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.polidea.rxandroidble.RxBleClient;

import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeroscope.aeroscope.AeroscopeConstants.LOCATION_PERMISSION_REQ_ID;


// MainActivity is defined as the LAUNCH activity in the Manifest
public class MainActivity extends AppCompatActivity implements ServiceConnection {
    
    // This https://stackoverflow.com/questions/32423157/android-check-if-location-services-enabled-using-fused-location-provider/35753050
    // says I may have to implement ActivityCompat.OnRequestPermissionsResultCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
    // to use SettingsApi to request Location Services

    private final static String LOG_TAG = "MainActivity           "; // tag for logging (23 chars max)
    
    private boolean scanActive;  // set false in onCreate(), true in startScan()

    // Set by onServiceConnected()
    private RxBleClient asBleClientRef;                       // reference to the BLE Client set by onServiceConnected()
    private AeroscopeBluetoothService asBleServiceRef;        // reference to the BLE Service instance set by onServiceConnected()
    ArrayAdapter<AeroscopeDevice> aeroscopeArrayAdapter;

    AtomicBoolean asBleSvcBound = new AtomicBoolean( false ); // whether Bluetooth Service is bound (set true; false on disconnect) TODO: ??
    
    LocationManager locMgr;  // for making sure Location Services are enabled (Android > 5)


    // GUI elements
    Button scanButton;
    ListView aeroscopeScanResults;
    Vector<AeroscopeDevice> foundDevices = new Vector<>();

/*
    If using a Client/Service architecture to handle Bluetooth:

    1. As the Client, this Activity implements ServiceConnection interface,
       overriding onServiceConnected() and onServiceDisconnected()

    2. Client calls bindService() [typically in onStart(), as here] passing an Intent
       (identifying Service class), the ServiceConnection instance (this), and flags

    3. System will call Client's onServiceConnected(), passing an IBinder. We cast this IBinder to
       our LocalBinder (in Service class)

    4. Then we can call the Service using methods provided in IBinder.
       In this case, the IBinder provides a asBleBinder.getService() call
       that returns the instance of AeroscopeBluetoothService (stored in asBleServiceRef)
       So we can call any service methods with asBleServiceRef.methodName()
       (Note that you can call static methods with an object reference)

    5. Call unbindService() to disconnect from it. When the last client unbinds, system destroys service.
       (You don't have to manage the lifecycle of a purely bound service.)

*/

/*---------------------------------SERVICE CONNECTION INTERFACE-----------------------------------*/

    // Created & used by onStart() in call to bindService()
    Intent bindAsBleIntent; // can pass extra data to the Service if we need to
    // e.g. myIntent.putExtra( "Name", <value> ); where <value> can be a whole range of <type>s
    // retrieve with <type> = myIntent.get<typeindicator>Extra( "Name" );

    // Implementation of ServiceConnection Interface: 2 methods
    @Override
    // called by system when connection with service is established, with object to interact with it.
    // Here, the IBinder has a getService() method that returns a reference to the Service instance
    // (If we connect to multiple services, probably have to test parameters to see which)
    @SuppressWarnings( "static-access" )
    public void onServiceConnected( ComponentName className, IBinder service ) {
        Log.d( LOG_TAG, "Entering onServiceConnected()" );
        // We've bound to Service, cast the IBinder and get Service instance
        AeroscopeBluetoothService.LocalBinder asBleBinder = (AeroscopeBluetoothService.LocalBinder) service;
        // (casting it makes compiler aware of existence of getService() method)
        asBleServiceRef = asBleBinder.getService();        // returns the instance of the Service
        if( asBleServiceRef == null ) throw new RuntimeException( LOG_TAG
                + ": onServiceConnected returned null from getService()" );

        asBleClientRef = asBleServiceRef.getRxBleClient(); // static member accessed by instance reference--OK!
        if( asBleClientRef == null ) throw new RuntimeException( LOG_TAG
                + ": onServiceConnected returned null from getRxBleClient()" );

        // ****** NEW pass this to Service for use in Toasts in handleScanError()
        asBleServiceRef.setCallingContext( MainActivity.this );

        asBleSvcBound.set( true ) ; // success: set "bound" flag TODO: need?
        Log.d( LOG_TAG, "Finished onServiceConnected()" ); // reached here OK
    }

    @Override // called when the connection with the service has been unexpectedly disconnected
    public void onServiceDisconnected( ComponentName name ) {
        asBleSvcBound.set( false ) ; // no longer bound
        Log.d( LOG_TAG, "Finished onServiceDisconnected()" );
    }

/*------------------------------------------------------------------------------------------------*/


/*--------------------------------------LIFECYCLE METHODS-----------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d( LOG_TAG, "Returned from super.onCreate()" ); // seems to work

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // TODO: what to do about this?
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setText("Scan for Aeroscopes");
        aeroscopeScanResults = (ListView) findViewById(R.id.FoundAeroscopes); // need layout for this to compile
        
        scanActive = false;  // NEW try to remember scan state
        
        locMgr = ( LocationManager ) getSystemService( Context.LOCATION_SERVICE );  // get the Location Manager service NEW

        Log.d( LOG_TAG, "Finished onCreate()" ); // [0040]
    } // onCreate


    @Override
    protected void onStart() {
        super.onStart();
        Log.d( LOG_TAG, "Returned from super.onStart()" );
        // Bluetooth stuff (was in onCreate() but sample code had it here)
        bindAsBleIntent = new Intent( getApplicationContext(), AeroscopeBluetoothService.class );   // intent to enable service

        if( !bindService( bindAsBleIntent, /*ServiceConnection*/ this, Context.BIND_AUTO_CREATE ) ) // flag: create the service if it's bound
            throw new RuntimeException( LOG_TAG + ": bindService() call in onStart() failed" );     // not getting Exception
        // can't do much else until we get the onServiceConnected() callback
    
    
        // NEW permissions check for ACCESS_COARSE_LOCATION
        int permissionCheck = ContextCompat.checkSelfPermission( MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION );
        if( permissionCheck != PackageManager.PERMISSION_GRANTED ) {  // don't have it
            ActivityCompat.requestPermissions( MainActivity.this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, LOCATION_PERMISSION_REQ_ID );
        }
    
        if (!locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {  // GPS not enabled, apparently
            buildAlertMessageNoGps();
        }
    
        Log.d( LOG_TAG, "Finished onStart()" ); // successfully reached
    }

    @Override // To receive Vector of discovered devices HUH?
    public void onResume() {
        super.onResume();
        // Register mMessageReceiver to receive messages (Vector of Aeroscopes discovered in scan)  // or register in Manifest(?)
        LocalBroadcastManager.getInstance( this ).registerReceiver( mMessageReceiver, new IntentFilter( "scanResults" ) );
        //asBleServiceRef.setCallingContext( this );  // for possible use by Toasts NEW -- NO -- NPE
    
        // not working yet...
        if( scanActive ) {
            if (aeroscopeArrayAdapter != null) {
                aeroscopeArrayAdapter.clear();
            }
            scanButton.setText("Scanning...");
            asBleServiceRef.scanForAeroscopes();      // fills the rxBleDeviceVector and asDeviceVector with detected Aeroscopes; sets nowScanning
        }
        
        Log.d( LOG_TAG, "Finished onResume()" );
    }

    @Override
    protected void onPause() {
        Log.d( LOG_TAG, "Entering onPause()" ); // NEW
        // Unregister since the activity is not visible
        LocalBroadcastManager.getInstance( this ).unregisterReceiver( mMessageReceiver );
        // Following line causing NPE (why is onPause() being called?)
        asBleServiceRef.stopScanning();  // stop scanning during pause (clears nowScanning)
        Log.d( LOG_TAG, "Finished onPause()" ); // NEW
        super.onPause();
    }

    @Override
    protected void onStop() {
        unbindService(this);
        super.onStop();
    }
    
    @Override
    protected void onDestroy() {  // system is shutting down MainActivity
        // Unsubscribe from all subscriptions to avoid memory leaks!  TODO: more?
        asBleServiceRef.stopScanning();  // just to make sure
        super.onDestroy();
    }
    
    // handler for received Intents for the "scanResults" event broadcast by AeroscopeBluetoothService after scan completion
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        @SuppressWarnings( "unchecked")
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            foundDevices = (Vector<AeroscopeDevice>) intent.getSerializableExtra( "io.aeroscope.aeroscope.DeviceVector" );  // "unchecked cast" warning
            Log.d( LOG_TAG, "Got foundDevices in onReceive(): " + foundDevices.toString() );
            aeroscopeArrayAdapter =
                    new ArrayAdapter<>(getBaseContext(), android.R.layout.simple_list_item_1, foundDevices);
            aeroscopeScanResults.setAdapter(aeroscopeArrayAdapter);

            aeroscopeScanResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override  // launch AeroscopeDisplay with the selected device
                // at this point we should be done with scanning (stopped here in both onPause() and onDestroy())
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                    Intent switchToDeviceView = new Intent(getApplicationContext(), AeroscopeDisplay.class);
                    switchToDeviceView.putExtra( "selectedScopeIndex", position);
                    Log.d(LOG_TAG, "Position value of ListView press: " + String.valueOf(position));
                    startActivity(switchToDeviceView);  // NOTE launching an Activity calls: onCreate(), onStart(), onResume()


                }
            });

            scanButton.setText("Scan Again?");
            Log.d(LOG_TAG, "We are done scanning.");
        }
    };


/*------------------------------------------------------------------------------------------------*/



    // When user clicks the Scan button (linked in content_main.xml)
    @SuppressWarnings( "static-access" ) // it is permissible to access static members with an instance reference
    public void startScan(View userPress) {
        Log.d( LOG_TAG, "Entered startScan()" );
        
        // TODO: clear the list of any old devices before starting scan again

        if (aeroscopeArrayAdapter != null) {
            aeroscopeArrayAdapter.clear();
        }
        scanButton.setText("Scanning...");
        asBleServiceRef.scanForAeroscopes();      // fills the rxBleDeviceVector and asDeviceVector with detected Aeroscopes; sets nowScanning
        scanActive = true;  // NEW try to remember scan state
        Log.d( LOG_TAG, "Finished startScan()" ); // seems to work
    }

    // When user clicks "stop scanning" call asBleServiceRef.stopScanning();
    // Then call & display asBleServiceRef.getFoundDeviceCount() and asBleServiceRef.getFoundDeviceVector().
    
// How the sample app scans for devices:
//    scanSubscription = rxBleClient.scanBleDevices() // note no Service ID supplied
//            .observeOn( AndroidSchedulers.mainThread())
//            .doOnUnsubscribe(this::clearSubscription)
//            .subscribe(resultsAdapter::addScanResult, this::onScanFailure);
//private void clearSubscription() {
//    scanSubscription = null;
//    resultsAdapter.clearScanResults();
//    updateButtonUIState();
//}
    
    
    @Override  // handler for the permissions request outcome
    public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults ) {
        super.onRequestPermissionsResult( requestCode, permissions, grantResults );
        switch (requestCode) {
            case LOCATION_PERMISSION_REQ_ID: {  // was it the request ID we sent?
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0  // got some result
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    // Do we need to actually do anything here? Just don't quit?
                
                } else {
                
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText( MainActivity.this, "Aeroscope App requires location permission, quitting...", Toast.LENGTH_LONG ).show();
                    finish();
                    
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    
    // ask user to enable Location Services (here, GPS) NEW
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);  // imported AppCompat version, could be wrong TODO: work?
        builder.setMessage("Aeroscope App needs GPS; do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                        finish();            // can't scan BLE without Location Services
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
