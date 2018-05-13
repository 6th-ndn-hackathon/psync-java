package ndn.psync.java_psync;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.named_data.jndn.psync.LogicConsumer;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }
    
    public void testHello()
    {
        // send a hello
        Name syncName = new Name("/sync/stuff");
        Name defaultCertificateName;
            try {
                defaultCertificateName = keyChain.createIdentityAndCertificate(testIdName);
                keyChain.getIdentityManager().setDefaultIdentity(testIdName);
            } 
            catch (SecurityException e2) {
                defaultCertificateName = new Name("/bogus/certificate/name");
            }
            face.setCommandSigningInfo(keyChain, defaultCertificateName);
            try {
                face.processEvents();
            }
            catch (IOException | EncodingException e) {
                e.printStackTrace();
            }
        LogicConsumer consumer = new LogicConsumer(syncName, face, 4, 0.001);
        consumer.sendHelloInterest();
        face.processEvents();
    }
}
