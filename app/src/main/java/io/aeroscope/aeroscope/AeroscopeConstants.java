package io.aeroscope.aeroscope;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

import io.aeroscope.oscilloscope.Trigger;

import static java.lang.Thread.MAX_PRIORITY;


/**
 * Created on 2017-02-25.
 */

final class AeroscopeConstants {
    
    /************************************ APPLICATION PARAMETERS **************************************/
    
    static final long SCAN_TIME = 600L;                    // limit BLE scan to this many seconds (if used)
    static final int MAX_AEROSCOPES = 1;                   // limit BLE scan to this many scopes (if used)
    static final boolean CONNECT_ON_DISCOVERY = false;     // should we automatically connect to discovered Aeroscopes?
    static final long FRAMESUBSCRIBER_SLEEP_MS = 20L;      // sleep time to wait for a new Data frame TODO: eliminate?
    static final int IO_HISTORY_LENGTH = 10;               // how many messages to keep in history TODO: using?
    static final boolean MSG_FROM_AEROSCOPE = false;       // describes origin of packets in history TODO: need?
    static final boolean CMD_TO_AEROSCOPE = true;          // describes origin of packets in history TODO: need?
    static final long HEARTBEAT_SECOND = 15;               // seconds between heartbeat ticks
    static final int DATA_RX_PRIORITY = MAX_PRIORITY - 2;  // priority for thread that receives packets & assembles frames (1-10) [was 8 then 10 then back to 8]
    
    static final long STOP_CANCEL_DELAY_MS = 1_000;        // # of milliseconds to wait after STOP command before sending CANCEL
    static final long POWER_ON_DELAY_MS = 1_500;           // # of milliseconds to wait after FULL POWER command (for FPGA init, etc.)
    //static final short INITIAL_DAC_OFFSET = (short)32_768; // middle of DAC range = 0V (range +/- 5V)  see DEFAULT_OFFSET_VALUE
    
    static final int LOCATION_PERMISSION_REQ_ID = 7490621; // any random int to indentify the callback in MainActivity
    
    // Screen Values
    static final int RAW_X_STEPS = 512;                    // 512 time steps (X values)
    static final int RAW_Y_STEPS = 256;                    // 256 possible Y values
    static final int X_DIVISIONS = 10;                     // 10 divisions on time scale
    static final int Y_DIVISIONS = 8;                      // 8 divisions on voltage scale


    
    /*************************************** BLUETOOTH SECTION ****************************************/
    
    // Parameter for connecting to a BLE device
    static final boolean AUTO_CONNECT = true;
    static final boolean NO_AUTO_CONNECT = false;
    static final long CONNECTION_RETRIES = 3;    // times to retry establishing connection
    
    // UUID section
    // The standard Bluetooth Base UUID; replace the first group with "0000SSSS" for 16-bit or "LLLLLLLL" for 32-bit abbreviated values
    private static final String BASE_UUID_STRING = "0000XXXX-0000-1000-8000-00805F9B34FB"; // just for reference
    private static final String BASE_UUID_HEAD = "0000"; // for 16-bit UUIDs we replace digits 4-7 of the base string with the value
    private static final String BASE_UUID_TAIL = "-0000-1000-8000-00805F9B34FB"; //
    
    // Standard descriptor used by RxAndroidBle to enable notifications (but not used directly by our code)
    private static final String CLIENT_CHAR_CONFIG_STRING = "2902"; // GATT standard Client Characteristic Configuration UUID
    static final UUID clientCharConfigID = UUID.fromString( BASE_UUID_HEAD + CLIENT_CHAR_CONFIG_STRING + BASE_UUID_TAIL ); // for notifications/indications
    static final byte[] asDisableNotifications = { 0, 0 }; // 2 bytes of 0 = 16-bit Descriptor
    static final byte[] asEnableNotifications  = { 1, 0 }; // ASSUMES LITTLE-ENDIAN
    
    // Aeroscope Service UUID
    private static final String AEROSCOPE_UUID_HEAD  =  "F954"; // next 4 characters are the short-form UUID
    private static final String AEROSCOPE_UUID_TAIL  =  "-91B3-BD9A-F077-80F2A6E57D00";
    private static final String asServiceIdString    =  AEROSCOPE_UUID_HEAD + "1234" + AEROSCOPE_UUID_TAIL; // Aeroscope Service UUID
    private static final UUID asServiceId = UUID.fromString( asServiceIdString ); // a Service is a collection of Characteristics
    static final UUID[] asServiceIdArray = { asServiceId }; // vararg argument to scanBleDevices is implicitly an array
    
    // Aeroscope Characteristic UUIDs (apparently verified by scanning the device)
    private static final String asDataCharIdString   =  BASE_UUID_HEAD + "1235" + BASE_UUID_TAIL; // Aeroscope Data UUID
    private static final String asInputCharIdString  =  BASE_UUID_HEAD + "1236" + BASE_UUID_TAIL; // Aeroscope Input UUID
    private static final String asStateCharIdString  =  BASE_UUID_HEAD + "1237" + BASE_UUID_TAIL; // Aeroscope State UUID
    private static final String asOutputCharIdString =  BASE_UUID_HEAD + "1239" + BASE_UUID_TAIL; // Aeroscope Output UUID
    static final UUID asDataCharId =   UUID.fromString( asDataCharIdString );
    static final UUID asInputCharId =  UUID.fromString( asInputCharIdString );
    static final UUID asStateCharId =  UUID.fromString( asStateCharIdString );
    static final UUID asOutputCharId = UUID.fromString( asOutputCharIdString );
    
    
    /**************************************** AEROSCOPE PROBE *****************************************/
    
    // Misc. parameters
    static final int MAX_FRAME_SIZE = 4096;
    
    // Channel values
    static final float MIN_VOLTS_PER_DIV = 0.1f;        // 100 mV/div
    static final float MAX_VOLTS_PER_DIV = 10f;         // 10 V/div
    static final float[] AVAILABLE_VOLTS_PER_DIV = { 0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f };
    static final String[] VERT_SENS_DESCRIPTION  // indexed by position in list
            = { "100mV", "200mV", "500mV", "1V", "2V", "5V", "10V" };
    static final float[] VERT_SCALE_FACTOR = { 1000f, 1000f, 1000f, 1f, 1f, 1f, 1f };  // to generate axis labels
    static final float DEFAULT_VOLTS_PER_DIV = 0.1f;    // 100 mV/div
    static final int DEFAULT_VERT_SENS_INDEX = 0;       // corresponds to array item 0
    static final boolean DEFAULT_DC_COUPLED = true;
    static final String[] VOLT_DESCRIPTION_UNITS = { "mV", "mV", "mV", "V", "V", "V", "V" };
    static final String[] Y_FORMAT_STRINGS = {  // formatter strings used to format chart Description X display
            "%+04.0f",         // at 100mV/div, Y values can range ±400 mV (plus possible offset); try 4 digits w/no fractional part
            "%+04.0f",         // at 200mV/div, same as 100?
            "%+05.0f",         // at 500mV/div let range go to 99999 mV
            "%+05.2f",         // at 1V/div, 99.99V
            "%+05.2f",         // at 2V/div, 99.99V
            "%+05.2f",         // at 5V/div, 99.99V
            "%+06.2f"          // at 10V/div, 999.99V
    };
    
    // TimeBase values
    static final float MIN_SECS_PER_DIV = 500e-9f;      // 500 ns/div
    static final float MAX_SECS_PER_DIV = 5f;           // 5 sec/div
    static final float[] AVAILABLE_SECS_PER_DIV = {  //                                  512 samples        4K samples
            500e-9f,         //   500ns/div ->  100M samples/sec  ->   10ns/sample  ->   ~5µs/screen  ->   ~40µs/buffer
            1e-6f,           //   1µs       ->   50M              ->   20ns         ->   10µs         ->    80µs
            2e-6f,           //   2µs       ->   25M              ->   40ns         ->   20µs         ->   160µs
            5e-6f,           //   5µs       ->   10M              ->  100ns         ->   50µs         ->   400µs
            10e-6f,          //  10µs       ->    5M              ->  200ns         ->  100µs         ->   800µs
            20e-6f,          //  20µs       ->  2.5M              ->  400ns         ->  200µs         ->   1.6ms
            50e-6f,          //  50µs       ->    1M              ->    1µs         ->  500µs         ->     4ms
            100e-6f,         // 100µs       ->  500K              ->    2µs         ->    1ms         ->     8ms
            200e-6f,         // 200µs       ->  250K              ->    4µs         ->    2ms         ->    16ms
            500e-6f,         // 500µs       ->  100K              ->   10µs         ->    5ms         ->    40ms
            1e-3f,           //   1ms       ->   50K              ->   20µs         ->   10ms         ->    80ms
            2e-3f,           //   2ms       ->   25K              ->   40µs         ->   20ms         ->   160ms
            5e-3f,           //   5ms       ->   10K              ->  100µs         ->   50ms         ->   400ms
            10e-3f,          //  10ms       ->    5K              ->  200µs         ->  100ms         ->   800ms
            20e-3f,          //  20ms       ->  2.5K              ->  400µs         ->  200ms         ->   1.6s
            50e-3f,          //  50ms       ->    1K              ->    1ms         ->  500ms         ->     4s
            100e-3f,         // 100ms       ->   500              ->    2ms         ->    1s          ->     8s
            200e-3f,         // 200ms       ->   250              ->    4ms         ->    2s          ->    16s (measured ~17)
            500e-3f,         // 500ms       ->   100              ->   10ms         ->    5s          ->    40s
            1f,              //   1s        ->    50              ->   20ms         ->   10s          ->    80s
            2f,              //   2s        ->    25              ->   40ms         ->   20s          ->   160s
            5f               //   5s        ->    10              ->  100ms         ->   50s          ->   400s
    };
    static final String[] TIME_BASE_DESCRIPTION   // indexed by position in list
            = { "500ns", "1µs", "2µs", "5µs", "10µs", "20µs", "50µs", "100µs", "200µs", "500µs",
            "1ms", "2ms", "5ms", "10ms", "20ms", "50ms", "100ms", "200ms", "500ms", "1s", "2s", "5s" };
    static final float[] HORIZ_SCALE_FACTOR = { 1e9f, 1e6f, 1e6f, 1e6f, 1e6f, 1e6f, 1e6f, 1e6f, 1e6f, 1e6f,
            1e3f, 1e3f, 1e3f, 1e3f, 1e3f, 1e3f, 1e3f, 1e3f, 1e3f, 1f, 1f, 1f };  // to generate axis labels
    static final float DEFAULT_SECS_PER_DIV = 500e-9f;  // 500 ns/div
    static final int DEFAULT_TIME_BASE_INDEX = 0;  // corresponds to array item 0
    static final int DEFAULT_TRIGGER_PCT = 50;     // 50% along the X axis (initial read buffer is middle of 4K memory)
    static final String[] TIME_DESCRIPTION_UNITS = { "ns", "µs", "µs", "µs", "µs", "µs", "µs", "µs", "µs", "µs",
            "ms", "ms", "ms", "ms", "ms", "ms", "ms", "ms", "ms", "s", "s", "s" };
    static final String[] X_FORMAT_STRINGS = {  // formatter strings used to format chart Description Y display
            "%+05.0f",         // at 500ns/div, X values can range ±2500ns (plus possible offset); try 5 digits w/no fractional part
            "%+05.2f",         // at 1us/div, 99.99;
            "%+05.2f",         // at 2us/div, 99.99;
            "%+06.2f",         // at 5us/div, 999.99;
            "%+06.2f",         // at 10us/div, 999.99
            "%+06.2f",         // at 20us/div, 999.99;
            "%+07.2f",         // at 50us/div, 9999.99;
            "%+07.2f",         // at 100us/div, 9999.99;
            "%+07.2f",         // at 200us/div, 9999.99;
            "%+07.1f",         // at 500us/div, 99999.9;
            "%+05.2f",         // at 1ms/div, 99.99;
            "%+05.2f",         // at 2ms/div, 99.99;
            "%+06.2f",         // at 5ms/div, 999.99;
            "%+06.2f",         // at 10ms/div, 999.99
            "%+06.2f",         // at 20ms/div, 999.99;
            "%+07.2f",         // at 50ms/div, 9999.99;
            "%+07.2f",         // at 100ms/div, 9999.99;
            "%+07.2f",         // at 200ms/div, 9999.99;
            "%+07.1f",         // at 500ms/div, 99999.9;
            "%+05.2f",         // at 1s/div, 99.99;
            "%+05.2f",         // at 2s/div, 99.99;
            "%+06.2f"          // at 5s/div, 999.99;
    };
    
  
    
    // Default values of the first 20 FPGA registers, writable as a block
    // updated to 1.1 spec
    static final byte[] DEFAULT_FPGA_REGISTERS = { // unallocated values are set to 0x00
            (byte)0x03, (byte)0x80, (byte)0xC5, (byte)0xE0, (byte)0x08,   // they use 0x00 but 0x08 makes more sense
            (byte)0x08, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x09,
            (byte)0x06,
            (byte)0x00, (byte)0x00, //DAC Control - should we initialize these? 0x80 & 0x00?
            (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00
    };
    // Register names with their array indexes
    static final int TRIGGER_CTRL = 0;
    static final int TRIGGER_PT = 1;
    static final int PLL_CTRL = 2;              // not currently used
    static final int FRONT_END_CTRL = 3;
    static final int SAMPLER_CTRL = 4;
    static final int TRIGGER_XPOS_HI = 5;       // bits 11:8
    static final int TRIGGER_XPOS_LO = 6;       // bits 7:0
    static final int READ_START_ADDRS_HI = 7;   // bits 11:8
    static final int READ_START_ADDRS_LO = 8;   // bits 7:0

    static final int WRITE_SAMPLE_DEPTH = 9;    // bits 3:0    code for size: 1 -> 16, 5 -> 256, 6 -> 512, 9 -> 4K
    static final int READ_SAMPLE_DEPTH = 10;    // bits 3:0    code for size: 1 -> 16, 5 -> 256, 6 -> 512, 9 -> 4K
    static final int DAC_CTRL_HI = 11;          // bits 15:8
    static final int DAC_CTRL_LO = 12;          // bits 7:0
    
    static final int DEFAULT_OFFSET_VALUE = 32_768;  // DAC value to center trace (+/- any calibration correction)
    
    static final int DEFAULT_READ_START_ADDRS = 256 * DEFAULT_FPGA_REGISTERS[READ_START_ADDRS_HI] + DEFAULT_FPGA_REGISTERS[READ_START_ADDRS_LO];
    
    // Trigger values
    static final EnumSet<Trigger.Mode> SUPPORTED_TRIGGER_MODES = EnumSet.of( Trigger.Mode.AUTO,
            Trigger.Mode.RISING, Trigger.Mode.FALLING, Trigger.Mode.NOISE_REDUCED);
    static final EnumSet<Trigger.Mode> DEFAULT_TRIGGER_MODES = EnumSet.of( Trigger.Mode.AUTO,
            Trigger.Mode.RISING );
    //  "raw" default trigger level is 0x80; this is initial scaled value at center of default scaled Y axis
    static final float DEFAULT_SCALED_TRIGGER_LEVEL = DataOps.scaledVolts(
            DEFAULT_FPGA_REGISTERS[TRIGGER_PT] & 0xFF,  // a byte promoted to an int and automatically cast to a float
            -(Y_DIVISIONS/2f) * DEFAULT_VOLTS_PER_DIV, (Y_DIVISIONS/2f) * DEFAULT_VOLTS_PER_DIV);  //(-4 * DEFAULT_VOLTS_PER_DIV) + 128f/255f * (8 * DEFAULT_VOLTS_PER_DIV);
    static final int DEFAULT_RAW_TRIGGER_LEVEL = DEFAULT_FPGA_REGISTERS[TRIGGER_PT] & 0xFF;  // = 128; watch sign extension
    static final int DEFAULT_RAW_TRIGGER_LOC = (DEFAULT_FPGA_REGISTERS[TRIGGER_XPOS_LO] & 0xFF)
            + 256 * (DEFAULT_FPGA_REGISTERS[TRIGGER_XPOS_HI] & 0xFF);  // trigger address in sample memory (0x800)
    
    // Values to be written to FPGA register 8 (SAMPLER_CTRL) to set Time per Division
    static final byte DIVISOR_500ns      = (byte)0x08;  // 1 x 10^0  = 1  (they use 0 or 1?; any unrecognized value works, this makes most sense)
    static final byte DIVISOR_1us        = (byte)0x10;  // 2 x 10^0  = 2
    static final byte DIVISOR_2us        = (byte)0x20;  // 4 x 10^0  = 4
    static final byte DIVISOR_5us        = (byte)0x09;  // 1 x 10^1  = 10
    static final byte DIVISOR_10us       = (byte)0x11;  // 2 x 10^1  = 20
    static final byte DIVISOR_20us       = (byte)0x21;  // 4 x 10^1  = 40
    static final byte DIVISOR_50us       = (byte)0x0A;  // 1 x 10^2  = 100
    static final byte DIVISOR_100us      = (byte)0x12;  // 2 x 10^2  = 200
    static final byte DIVISOR_200us      = (byte)0x22;  // 4 x 10^2  = 400
    static final byte DIVISOR_500us      = (byte)0x0B;  // 1 x 10^3  = 1000
    static final byte DIVISOR_1ms        = (byte)0x13;  // 2 x 10^3  = 2000
    static final byte DIVISOR_2ms        = (byte)0x23;  // 4 x 10^3  = 4000
    static final byte DIVISOR_5ms        = (byte)0x0C;  // 1 x 10^4  = 10000
    static final byte DIVISOR_10ms       = (byte)0x14;  // 2 x 10^4  = 20000
    static final byte DIVISOR_20ms       = (byte)0x24;  // 4 x 10^4  = 40000
    static final byte DIVISOR_50ms       = (byte)0x0D;  // 1 x 10^5  = 100000
    static final byte DIVISOR_100ms      = (byte)0x15;  // 2 x 10^5  = 200000
    static final byte DIVISOR_200ms      = (byte)0x25;  // 4 x 10^5  = 400000 TODO: not in specs; should be
    static final byte DIVISOR_500ms_ROLL = (byte)0xE7;  // special 1000000
    static final byte DIVISOR_1s_ROLL    = (byte)0xEF;  // special 2000000
    static final byte DIVISOR_2s_ROLL    = (byte)0xF7;  // special 4000000
    static final byte DIVISOR_5s_ROLL    = (byte)0xFF;  // special 10000000
    
    static final byte[] SAMPLER_CTRL_BYTE  // indexed by position in list
            = { DIVISOR_500ns, DIVISOR_1us, DIVISOR_2us, DIVISOR_5us, DIVISOR_10us, DIVISOR_20us,
            DIVISOR_50us, DIVISOR_100us, DIVISOR_200us, DIVISOR_500us, DIVISOR_1ms, DIVISOR_2ms,
            DIVISOR_5ms, DIVISOR_10ms, DIVISOR_20ms, DIVISOR_50ms, DIVISOR_100ms, DIVISOR_200ms,
            DIVISOR_500ms_ROLL, DIVISOR_1s_ROLL, DIVISOR_2s_ROLL, DIVISOR_5s_ROLL };
    
    
    //static final java.time.Duration _500ns = Duration.of
    
    // Values to be written to FPGA register 3 (FRONT_END_CTRL) to set Volts per Division
    // NOTE: MUST PRESERVE BIT 7 OF THE REGISTER, WHICH SPECIFIES AC OR DC COUPLING!
    static final byte FRONT_END_100mV    = (byte)0x60;
    static final byte FRONT_END_200mV    = (byte)0x41;
    static final byte FRONT_END_500mV    = (byte)0x20;
    static final byte FRONT_END_1V       = (byte)0x22;
    static final byte FRONT_END_2V       = (byte)0x03;
    static final byte FRONT_END_5V       = (byte)0x04;
    static final byte FRONT_END_10V      = (byte)0x05;
    
    static final byte[] FRONT_END_CTRL_BYTE  // indexed by position in list
            = { FRONT_END_100mV, FRONT_END_200mV, FRONT_END_500mV,
            FRONT_END_1V, FRONT_END_2V, FRONT_END_5V, FRONT_END_10V };
    
    // For each Voltage range (with usual index), the no. of DAC bits corresponding to 1 ADC bit
    static final float[] DAC_COUNTS_PER_ADC_BIT = { 2.048f, 4.096f, 10.24f, 20.48f, 40.96f, 102.4f, 204.8f };
    // at 100mV/div, with 8 divs, measurement range is 800mV/256 counts, or 3.125mV/ADC bit
    // for DAC to generate the same 3.125mV, it takes 64K/10V * 0.003125 = 20.48 counts.
    // However, there's a 10X attenuator at front end so only takes 2.048 DAC counts/ADC bit
    // at 10V/div, measurement range is 80V/256 counts, or 0.3125V/ADC count; 64K/10V * 0.3125 = 2048, or 204.8 with the attenuator
    // So for a given sensitivity, moving the zero point by 1 division (1/8 of the scaled Y range) = 32 ADC bits, DAC takes 32 * counts/ADC bit
    // Moving the zero point by 1 full screen takes 256 * (DAC counts/ADC bit) counts.
    // For an offset of a fraction y of the screen height, it takes (y * 256) * (counts/ADC bit for the current sensitivity) DAC counts
    // After the move, the scaled Ymin and Ymax for the chart are adjusted by y * Yrange. Get all the signs right! Round?
    // A drag UP decreases Y values. At the bottom of the screen, we want a negative value to read 0 ADC bits so
    // we want to add to it with the DAC. But since the DAC output is inverted, we reduce the DAC setting to bring lower voltages into range
    // SO: drag UP: decrease scaled Y values on axis. Decrease DAC value. Conversely for drag DOWN: increase scaled Y values and DAC value
    
    // To change vertical sensitivity while keeping mid-screen voltage value the same:
    // What to load in the DAC (ignoring calibration values):
    // For a desired Ymid value: the corresponding DAC value to be added to 0x8000 is:
    //     Ymid * (# of raw ADC values = 256)/[(# of screen divisions = 8) * (current Volts/div)] * [(# of DAC counts)/(ADC bit at current V/div setting)]
    // Example: we want 12V to be mid-axis and sensitivity is 5 V/div:
    //     (12V) * (256ADC bits)/(8div * 5V/div) * (102.4DAC counts/ADC bit) = 7864.32DAC counts ADDED TO 32768
    
    
    // 8-bit numeric values for battery state ranges (low-order byte of battery voltage register)
    // (bit 15 = charger present; bit 14 = now charging)
    static final byte BATT_FULL_MAX     = (byte)255;
    static final byte BATT_FULL_MIN     = (byte)238;
    static final byte BATT_MED_MAX      = (byte)237;
    static final byte BATT_MED_MIN      = (byte)226;
    static final byte BATT_LOW_MAX      = (byte)225;
    static final byte BATT_LOW_MIN      = (byte)220;
    static final byte BATT_CRITICAL_MAX = (byte)219;
    static final byte BATT_CRITICAL_MIN = (byte)212;
    static final byte BATT_SHUTDOWN     = (byte)211;
    
    static final int BATTERY_CRITICAL   = 0; // because enums are discouraged
    static final int BATTERY_LOW        = 1;
    static final int BATTERY_MEDIUM     = 2;
    static final int BATTERY_FULL       = 3;
    
    
    // Aeroscope commands (written to Input Characteristic)
    // RxAndroidBle wants them all to be byte[20]
    static final byte[] DUMMY_COMMAND        = { };  // for the "Command..." at top of spinner
    static final byte[] RUN_MODE             = "R\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1
    static final byte[] STOP_MODE            = "S\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1
    static final byte[] GET_SNGL_FRAME_STOP  = "F\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1 (called "Single Frame")
    static final byte[] GET_FULL_FRAME_STOP  = "L\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1 (called "Full Frame")
    static final byte[] CANCEL_FRAME         = "X\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1 see STOP_CANCEL_DELAY_MS
    static final byte[] PAUSE_AND_CAL        = "CF\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.2 (called "Start Calibration") (was CI)
    static final byte[] CLEAR_CAL            = "CX\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1 (called "Clear Calibration")
    static final byte[] DEEP_SLEEP           = "ZZ\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1 (called "Sleep")
    static final byte[] RESET                = "ZR\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    // static final byte[] WRITE_FPGA_REG       = "W\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // undocumented; for internal testing
    static final byte[] SET_NAME             = "N\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );  // *1.1 ("Scope Name")
    static final byte[] POWER_FULL           = "PF\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    // static final byte[] POWER_LOW            = "PL\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // /1.1 don't use
    static final byte[] POWER_OFF            = "PO\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1 (turn off FPGA; no data send)
    static final byte[] SEND_TELEMETRY       = "QTI\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );    // *1.1
    // static final byte[] REFRESH_TELEMETRY    = "QTR\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );    // /1.1 don't use
    // static final byte[] SEND_CACHED_VERSION  = "QVI\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );    // /1.1 don't use
    static final byte[] GET_VERSION          = "QVR\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );    // *1.1
    static final byte[] FETCH_ERROR_LOG      = "QE\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    static final byte[] FETCH_CAL_PARAMS     = "QC\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    static final byte[] FETCH_POWER_STATE    = "QP\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    static final byte[] CLEAR_ERROR_LOG      = "EX\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.US_ASCII );   // *1.1
    

    static final byte[][] USER_COMMAND_ARRAY = {  // updated to 1.1 spec
            // first 2 entries swapped in branch Stop/Run/Button so the command auto-executed at startup will be STOP
            DUMMY_COMMAND,          //INDEX 0    New for "Command..." at top of spinner; increase other indexes by 1
            STOP_MODE,              //INDEX 1    Stop (but send any pending frame when it's triggered -- see CANCEL)
            RUN_MODE,               //INDEX 2    Continuously acquire frames and send to host
            GET_SNGL_FRAME_STOP,    //INDEX 3    Send the next frame captured then Stop
            GET_FULL_FRAME_STOP,    //INDEX 4    Send full contents of sample buffer then Stop (no new data acquired)
            CANCEL_FRAME,           //INDEX 5    Cancel any pending frame (issued 1 second after Stop to allow any current transmission to complete)
            PAUSE_AND_CAL,          //INDEX 6    Enter calibration routine; send a 'C' packet with values upon completion
            CLEAR_CAL,              //INDEX 7    Clear calibration parameters
            DEEP_SLEEP,             //INDEX 8    "Factory Ship" mode: doesn't advertise, can only be woken by pressing button or attaching charger
            RESET,                  //INDEX 9    Reset the MCU and power-cycle the FPGA
            SET_NAME,               //INDEX 10   Set a new name of up to 19 characters (null-terminated if shorter). Nonvolatile
            POWER_FULL,             //INDEX 11   Fully turn on the unit (automatically set (with delay) on BLE connection; don't request frames before)
            POWER_OFF,              //INDEX 12   Turn off the FPGA (no data transmitted)
            SEND_TELEMETRY,         //INDEX 13   Send charger info, battery voltage, and temperature
            GET_VERSION,            //INDEX 14   Send hardware rev, FPGA firmware rev, MCU firmware rev, device S/N
            FETCH_ERROR_LOG,        //INDEX 15   Send last 19 1-byte error codes
            FETCH_CAL_PARAMS,       //INDEX 16   Send calibration parameters
            FETCH_POWER_STATE,      //INDEX 17   Send the power state
            CLEAR_ERROR_LOG         //INDEX 18   Clear the error log
    };
    
    // Indexes of various commands in USER_COMMAND_ARRAY (for switch, e.g.)
    // Adjusted up by 1 to make room for DUMMY_COMMAND at top
    static final int DUMMY_INDEX = 0;        // not sure using this
    static final int STOP_INDEX = 1;         // index of Stop command in USER_COMMAND_ARRAY
    static final int RUN_INDEX  = 2;         // index of Run  command in USER_COMMAND_ARRAY
    static final int SET_NAME_INDEX  = 10;   // index of Set Name command in USER_COMMAND_ARRAY
    static final int DEEP_SLEEP_INDEX  = 8;  // index of Deep Sleep command in USER_COMMAND_ARRAY
    static final int FULL_FRAME_INDEX  = 4;  // index of Get Full Frame & Stop command in USER_COMMAND_ARRAY
    static final int RESET_INDEX  = 9;       // index of Reset  command in USER_COMMAND_ARRAY
    static final int CLEAR_CAL_INDEX = 7;    // index of Clear Calibration Parameters
    static final int SINGLE_FRAME_INDEX = 3; // index of Get Single Frame
    static final int FPGA_OFF_INDEX = 12;    // index of Power Off to FPGA (disables sending)
    
    // Aeroscope messages (received from Output Characteristic)
    // These should probably be byte, not byte[]
    // updated to 1.1 spec
    static final byte TELEMETRY            = (byte) 'T';   // followed by Charger Status, Battery Voltage,
                                                           // Temperature (H), Temperature (L), GONE: Acceleration (H), Acceleration (L)
    static final byte VERSION              = (byte) 'V';   // followed by Hardware ID, FPGA Rev, Firmware Rev, Serial No. (4 bytes, big first)
    
    static final byte ERROR                = (byte) 'E';   // followed by the 1-byte codes of the last 19 errors (as long as second byte isn't 'C')
    static final byte CRITICAL             = (byte) 'C';   // second byte of Error preamble followed by 1-byte unrecoverable error code (sent immed; no query needed)
    
    static final byte CALIBRATION          = (byte) 'C';   // first byte of Calibration preamble
    //static final byte QUICK                = (byte) 'A';   // second byte of Calibrate preamble followed by Cal1 (H), Cal1 (L),  // removed
                                                             // Cal2 (H), Cal2 (L), Cal3 (H), Cal3 (L), Cal4 (H), Cal4 (L)
    static final byte BRUTE_FORCE          = (byte) 'B';   // second byte of Calibrate preamble followed by 10V (H), 10V (L),
                                                           // 5V (H), 5V (L), 2V (H), 2V (L), 1V (H), 1V (L),
                                                           // 500mV (H), 500mV (L), 200mV (H), 200mV (L), 100mV (H), 100mV (L), (14 data bytes)
                                                           // GONE (from this low-cost version): 50mV (H), 50mV (L), 20mV (H), 20mV (L)
    
    // static final byte DEBUG_PRINT          = (byte) 'D';   // followed by null-terminated string (ASCII)  // undocumented or unimplemented
    
    static final byte BUTTON               = (byte) 'B';   // first byte of Button message
    static final byte DOWN                 = (byte) 'D';   // second byte of Button Down message
    //static final byte UP                   = (byte) 'U';   // second byte of Button preamble, followed by T for time down (future?)
    //static final byte TIME_DOWN            = (byte) 'T';   // third byte of Button preamble, followed by Time (H), Time (L) (future?)
    
    static final byte POWER                = (byte) 'P';   // first byte of Power State
    static final byte FULL                 = (byte) 'F';   // second byte of Power State -- Sent on connection after FPGA finishes configuring
    //static final byte LOW                  = (byte) 'L';   // second byte of Power State  // gone
    static final byte OFF                  = (byte) 'O';   // second byte of Power State

    enum PowerState { DEVICE_POWER_OFF, DEVICE_POWER_FULL, DEVICE_POWER_UNK }

    static final byte NULL                   = (byte) 0;      // padding bytes for BLE packets  TODO: eliminate?
    
    // Packet Header Values (code is 8 * 2^n)
    static final byte PACKET_HEADER_COF      = (byte) 0;      // Continuation of Frame
    static final byte PACKET_HEADER_SOF_16   = (byte) 1;      // Start of 16-byte Frame
    static final byte PACKET_HEADER_SOF_256  = (byte) 5;      // Start of 256-byte Frame - support optional for now
    static final byte PACKET_HEADER_SOF_512  = (byte) 6;      // Start of 512-byte Frame - new
    static final byte PACKET_HEADER_SOF_4096 = (byte) 9;      // Start of 4K-byte Frame
    
    static final int[] FRAME_SIZE = {  // use one of the above codes to get the corresponding frame size
                                       // (-1 = unsupported, first value 0 for packet ID)
            0, 16, -1, -1, -1, 256, 512, -1, -1, 4096
    };
    
    static final int[] PACKETS_PER_FRAME = {  // indexed by same SOF header value as FRAME_SIZE
            0, 1, 0, 0, 0, 14, 27, 0, 0, 216
    };
    
    static final int  PACKET_SIZE = 20;                       // BLE standard, eh?


    //ERROR CODE TYPES

    static final byte E_OK               = 0x00;
    static final byte E_ARG_ERR          = 0x20;
    static final byte E_CTRL_CMD         = 0x21;
    static final byte E_CAL_ARG          = 0x22;
    static final byte E_Q_ARG            = 0x24;
    static final byte E_Z_ARG            = 0x25;
    static final byte E_P_ARG            = 0x26;
    static final byte E_TEL_ARG          = 0x27;
    static final byte E_VERS_ARG         = 0x28;
    static final byte E_C_ARGS           = 0x29;
    static final byte E_UART_TIMEOUT     = 0x30;
    static final byte E_UART_HEADER      = 0x31;
    static final byte E_UART_FOOTER      = 0x32;
    static final byte E_UART_CRC         = 0x33;
    static final byte E_UART_LENGTH      = 0x34;
    static final byte E_UART_NO_SOF      = 0x35;
    static final byte E_UART_BREAK       = 0x36;
    static final byte E_UART_FRAMING     = 0x37;
    static final byte E_UART_PARITY      = 0x38;
    static final byte E_UART_OVERRUN     = 0x39;
    static final byte E_UART_FIFO        = 0x3A;
    static final byte E_BLE_DISCONNECTED = 0x40;
    static final byte E_XMIT_IN_PROG     = 0x41;
    static final byte E_WDT_RESET        = 0x50;

    //CRITICAL ERROR CODE TYPES

    static final byte CRITICAL_ERROR  = (byte) 0xC0;
    static final byte E_FPGA_CONFIG   = (byte) 0xC0;
    static final byte E_FPGA_RES      = (byte) 0xC1;
    static final byte E_FPGA_FRZN     = (byte) 0xC2;
    
}
