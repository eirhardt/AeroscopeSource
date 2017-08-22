package io.aeroscope.aeroscope;

import android.app.Instrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Created on 2017-03-07.
 */

public class FrameBufferTest extends Instrumentation {
    
    static int packetNo;
    
    public FrameBufferTest() {
    }
    
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        
        return null;
    }
    
    @Before
    public void setUp() throws Exception {
        
        
    }
    
    @Test
    public void name() throws Exception {
        packetNo = -1;
    }
    
    
    @After
    public void tearDown() throws Exception {
        
    }
    
    
    public class Packets {
        void initialize( int frameSize ) {
            
        }
        public byte[] getNext() { // returns null if done with a frame
        
            return null;
        }
    
    
    }

}
