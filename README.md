# AeroscopeSource
My capstone project for UW. Software interface for the Aeroscope Probe.


The user can scan and connect to an Aeroscope, at which point they will see a change in views as the initial scanning page is replaced with a new view, the visual interface to the Aeroscope. It contains three option menus at the bottom of the screen - the leftmost one is used to select the horizontal timebase, the center, for sending a command to the probe, and the rightmost is for changing the vertical sensitivity of the probe. 

There are a few things that need explaining with the UI: double tapping anywhere in the rightmost column of the grid will reset the trigger level to zero. A long press in the rightmost column will set the trigger level to the location of the press. A vertical drag in the right column moves the trigger level, however the trigger is not displayed until after the drag has finished (this is something that could be improved). Finally, a tap in the right column briefly displays the trigger level (Note that the trigger level is always shown in the bottom of the screen, as part of the status line).

Touching anywhere on the signal trace will set that point as the x & y coordinates in the status line, and will highlight the point on the trace with faint magenta horizontal & vertical lines (cursor/crosshair). 

 Double tapping in any other part of the screen will re-zero the axes, making (0, 0) the center of the screen (Note: in the horizontal axis labeling, time 0 is always the trigger point). 

There are several commands located in the command spinner (the middle one) that could (should probably) be hidden from the user. 

There is a duplicate command: the stop/run command works as a button in the upper left, but also appears as a command in the command spinner. 

Code Structure 
The app is contained in two packages. Package “io.aeroscope.oscilloscope” is a generic interface to a software oscilloscope, with files defining the operations of timebase, trigger, channel, and screen. These are all Java interfaces consisting of several methods to be implemented for a specific hardware device. The implementations are in the package “io.aeroscope.aeroscope”, which also contains the Android activities, services, and utility classes (like AeroscopeConstants and DataOps). 

The classes AsChannel, AsTimebase, AsScreen, and AsTrigger are implementations of the oscilloscope interfaces. Additional classes include: 
•	AeroscopeApp – minimal wrapper that defines the application. 

•	AeroscopeBluetoothService – a background service that handles the Bluetooth connection, employing RxAndroidBLE, an open source Bluetooth package based on reactive programming techniques. 

•	AeroscopeConstants – symbolic definitions of many data items used by the application.

•	AeroscopeDevice – handles a lot of hardware specific details that are not addressed in the generic interfaces.

•	AeroscopeDisplay – the main UI activity that deals with everything except the graph display.

•	AeroscopePreferences – intended to encapsulate user settings and preferences, but not implemented.

•	DataOps – defines several data structures and methods having to do with data manipulation and display. In particular, scaling the data to the graph axes. 

•	HexString – Simple utility class for converting hex values to displayable numbers. 

•	MainActivity – the activity that is started when the app is launched. 

•	RollModeList – container for the values displayed in roll mode.

•	ScreenFragment – the fragment that resides inside of AeroscopeDisplay that handles the visual representation of the Aeroscope data stream (virtual oscilloscope screen). It employs the open source graphing package MPAndroidChart. 

Like most Android apps, the main activity is the first thing that is launched. This is used to scan for and locate any Aeroscope in the vicinity. Once found, tapping the name will launch the AeroscopeDisplay activity, presenting the UI to the user. From there, after a brief connection interval, the user can then operate the Aeroscope. 


Known Problems/Uncertainties

Tablet orientation changes – The current code does not address device orientation changes (rotating the device from vertical to horizontal, etc.). 
Issue with Android 6 & Beyond – Written some code to try and address permissions issues, but I don’t have any good way of testing it. In Android 6 or later, scanning for Bluetooth devices requires location permissions to be enabled. 
Full Frame Display – Haven’t been able to get this to work using your suggested method of first getting a single frame and then a full frame. However, it does seem to work if you do these operations in reverse. 
Roll Mode – Seems to work but has not been tested extensively. 
