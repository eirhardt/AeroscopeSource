package io.aeroscope.aeroscope;

/**
 * Created on 4/23/17.
 */

class AeroscopePreferences {
    int asTimeBaseIndex;
    int asCommandIndex;
    int asVertSensIndex;

    //TODO: Calibration params (sent/received as shorts, need to cast to ints)
    //TODO: AC or DC coupling? If 1 == AC, call variable ACcoupled etc.
    //TODO: Offset, trigger mode (rising, falling, auto)



    public AeroscopePreferences() {

    }
}
